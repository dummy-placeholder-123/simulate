import { useEffect, useMemo, useRef, useState } from "react";

const DEFAULT_ACCOUNT_ID = "564061926474";
const DEFAULT_USERNAME = "qca-admin";
const MAX_WAIT_MS = 190_000;
const POLL_INTERVAL_MS = 3_000;
const DEFAULT_SESSION_TTL_SECONDS = 21_600;
const SESSION_STORAGE_KEY = "qca-auth-session";

const delay = (ms) => new Promise((resolve) => window.setTimeout(resolve, ms));

const randomScanId = () =>
  `scan-${Math.random().toString(36).slice(2, 8)}-${Date.now().toString(36).slice(-5)}`;

const normalizeBaseUrl = (value) => (value || "").trim().replace(/\/$/, "");

const pretty = (value) => JSON.stringify(value ?? {}, null, 2);

const isZipFile = (file) => {
  if (!file) {
    return false;
  }

  return file.name.toLowerCase().endsWith(".zip");
};

const defaultAuthConfig = {
  provider: "cognito",
  sessionTtlSeconds: DEFAULT_SESSION_TTL_SECONDS,
  cognito: {
    region: "",
    userPoolId: "",
    clientId: "",
  },
};

async function parseResponse(response) {
  const contentType = response.headers.get("content-type") || "";
  const text = await response.text();

  if (contentType.includes("application/json") && text) {
    return JSON.parse(text);
  }

  return text;
}

function createErrorPayload(error) {
  return {
    error: true,
    message: error.message,
    status: error.status || null,
    details: error.body || null,
  };
}

function readStoredSession() {
  try {
    const stored = JSON.parse(window.localStorage.getItem(SESSION_STORAGE_KEY) || "null");
    if (!stored || stored.sessionExpiresAt <= Date.now()) {
      window.localStorage.removeItem(SESSION_STORAGE_KEY);
      return null;
    }
    return stored;
  } catch {
    window.localStorage.removeItem(SESSION_STORAGE_KEY);
    return null;
  }
}

function persistSession(session) {
  window.localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session));
}

function clearStoredSession() {
  window.localStorage.removeItem(SESSION_STORAGE_KEY);
}

function normalizeAuthConfig(config, defaultEnv) {
  const rootAuth = config?.auth || {};
  const envAuth = config?.environments?.[defaultEnv]?.auth || {};
  const auth = { ...defaultAuthConfig, ...rootAuth, ...envAuth };
  const ttlSeconds = Number(auth.sessionTtlSeconds || DEFAULT_SESSION_TTL_SECONDS);
  return {
    provider: auth.provider || defaultAuthConfig.provider,
    sessionTtlSeconds: Number.isFinite(ttlSeconds) && ttlSeconds > 0
      ? ttlSeconds
      : DEFAULT_SESSION_TTL_SECONDS,
    cognito: {
      ...defaultAuthConfig.cognito,
      ...(rootAuth.cognito || {}),
      ...(envAuth.cognito || {}),
    },
  };
}

function sessionExpiry(sessionTtlSeconds) {
  return Date.now() + sessionTtlSeconds * 1000;
}

function accessExpiry(expiresInSeconds, sessionExpiresAt) {
  return Math.min(Date.now() + Number(expiresInSeconds || 900) * 1000, sessionExpiresAt);
}

export default function App() {
  const [baseUrl, setBaseUrl] = useState(window.location.origin);
  const [authConfig, setAuthConfig] = useState(defaultAuthConfig);
  const [runtimeConfigLoaded, setRuntimeConfigLoaded] = useState(false);
  const [session, setSession] = useState(readStoredSession);
  const [loginForm, setLoginForm] = useState({ username: DEFAULT_USERNAME, password: "" });
  const [loginError, setLoginError] = useState("");
  const [loginRunning, setLoginRunning] = useState(false);
  const [file, setFile] = useState(null);
  const [running, setRunning] = useState(false);
  const [result, setResult] = useState(null);
  const [copyStatus, setCopyStatus] = useState("Copy");
  const fileInputRef = useRef(null);

  useEffect(() => {
    let ignore = false;

    async function loadRuntimeConfig() {
      try {
        const response = await fetch("/runtime-config.json", { cache: "no-store" });
        if (!response.ok) {
          return;
        }

        const config = await response.json();
        if (ignore) {
          return;
        }

        const defaultEnv = config.defaultEnv || Object.keys(config.environments || {})[0];
        const configuredUrl = config.environments?.[defaultEnv]?.apiBaseUrl;
        if (configuredUrl) {
          setBaseUrl(configuredUrl);
        }
        setAuthConfig(normalizeAuthConfig(config, defaultEnv));
      } catch {
        // Local development works through the Vite proxy; no config file is required.
      } finally {
        if (!ignore) {
          setRuntimeConfigLoaded(true);
        }
      }
    }

    loadRuntimeConfig();
    return () => {
      ignore = true;
    };
  }, []);

  useEffect(() => {
    if (runtimeConfigLoaded && session && session.provider !== authConfig.provider) {
      logout();
    }
  }, [authConfig.provider, runtimeConfigLoaded, session]);

  useEffect(() => {
    if (!session) {
      return undefined;
    }

    const timeoutMs = Math.max(0, session.sessionExpiresAt - Date.now());
    const timeoutId = window.setTimeout(() => {
      logout();
    }, timeoutMs);
    return () => window.clearTimeout(timeoutId);
  }, [session]);

  const selectedFileLabel = useMemo(() => {
    if (!file) {
      return "Choose ZIP";
    }

    return `${file.name} (${file.size.toLocaleString()} bytes)`;
  }, [file]);

  const displayedJson = useMemo(() => pretty(result || { result: null }), [result]);

  const sessionLabel = useMemo(() => {
    if (!session) {
      return "";
    }

    return `${session.username} - ${new Date(session.sessionExpiresAt).toLocaleTimeString([], {
      hour: "2-digit",
      minute: "2-digit",
    })}`;
  }, [session]);

  async function copyJson() {
    try {
      await navigator.clipboard.writeText(displayedJson);
      setCopyStatus("Copied");
      window.setTimeout(() => setCopyStatus("Copy"), 1400);
    } catch {
      setCopyStatus("Failed");
      window.setTimeout(() => setCopyStatus("Copy"), 1400);
    }
  }

  async function request(path, options = {}) {
    const url = `${normalizeBaseUrl(baseUrl)}${path}`;
    const response = await fetch(url, options);
    const body = await parseResponse(response);

    if (!response.ok) {
      const error = new Error(typeof body === "string" ? body : `Request failed with ${response.status}`);
      error.status = response.status;
      error.body = body;
      throw error;
    }

    return body;
  }

  async function demoLogin(username, password) {
    const body = await request("/api/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username, password }),
    });
    const sessionExpiresAt = sessionExpiry(authConfig.sessionTtlSeconds);
    return {
      provider: "demo",
      username,
      accessToken: body.accessToken,
      refreshToken: body.refreshToken,
      accessExpiresAt: accessExpiry(body.accessTokenExpiresInSeconds, sessionExpiresAt),
      sessionExpiresAt,
    };
  }

  async function cognitoAuthRequest(payload) {
    const { region } = authConfig.cognito;
    if (!region || !authConfig.cognito.clientId) {
      throw new Error("Cognito region and client ID are required in runtime-config.json.");
    }

    const response = await fetch(`https://cognito-idp.${region}.amazonaws.com/`, {
      method: "POST",
      headers: {
        "Content-Type": "application/x-amz-json-1.1",
        "X-Amz-Target": "AWSCognitoIdentityProviderService.InitiateAuth",
      },
      body: JSON.stringify(payload),
    });
    const body = await parseResponse(response);
    if (!response.ok) {
      const error = new Error(body.message || body.__type || `Cognito request failed with ${response.status}`);
      error.status = response.status;
      error.body = body;
      throw error;
    }

    return body;
  }

  async function cognitoLogin(username, password) {
    const body = await cognitoAuthRequest({
      AuthFlow: "USER_PASSWORD_AUTH",
      ClientId: authConfig.cognito.clientId,
      AuthParameters: {
        USERNAME: username,
        PASSWORD: password,
      },
    });

    const auth = body.AuthenticationResult;
    if (!auth?.AccessToken) {
      throw new Error("Cognito did not return an access token.");
    }

    const sessionExpiresAt = sessionExpiry(authConfig.sessionTtlSeconds);
    return {
      provider: "cognito",
      username,
      accessToken: auth.AccessToken,
      idToken: auth.IdToken || "",
      refreshToken: auth.RefreshToken || "",
      accessExpiresAt: accessExpiry(auth.ExpiresIn, sessionExpiresAt),
      sessionExpiresAt,
    };
  }

  async function refreshDemoSession(currentSession) {
    const body = await request("/api/auth/refresh", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken: currentSession.refreshToken }),
    });
    return {
      ...currentSession,
      accessToken: body.accessToken,
      refreshToken: body.refreshToken || currentSession.refreshToken,
      accessExpiresAt: accessExpiry(body.accessTokenExpiresInSeconds, currentSession.sessionExpiresAt),
    };
  }

  async function refreshCognitoSession(currentSession) {
    const body = await cognitoAuthRequest({
      AuthFlow: "REFRESH_TOKEN_AUTH",
      ClientId: authConfig.cognito.clientId,
      AuthParameters: {
        REFRESH_TOKEN: currentSession.refreshToken,
      },
    });

    const auth = body.AuthenticationResult;
    if (!auth?.AccessToken) {
      throw new Error("Cognito did not return a refreshed access token.");
    }

    return {
      ...currentSession,
      accessToken: auth.AccessToken,
      idToken: auth.IdToken || currentSession.idToken || "",
      accessExpiresAt: accessExpiry(auth.ExpiresIn, currentSession.sessionExpiresAt),
    };
  }

  async function ensureAccessToken() {
    if (!session) {
      throw new Error("Login is required.");
    }
    if (session.sessionExpiresAt <= Date.now()) {
      logout();
      throw new Error("Session expired. Login again.");
    }
    if (session.accessToken && session.accessExpiresAt > Date.now() + 30_000) {
      return session.accessToken;
    }
    if (!session.refreshToken) {
      logout();
      throw new Error("Session expired. Login again.");
    }

    const refreshed = session.provider === "cognito"
      ? await refreshCognitoSession(session)
      : await refreshDemoSession(session);
    setSession(refreshed);
    persistSession(refreshed);
    return refreshed.accessToken;
  }

  async function handleLogin(event) {
    event.preventDefault();
    setLoginRunning(true);
    setLoginError("");

    try {
      const nextSession = authConfig.provider === "cognito"
        ? await cognitoLogin(loginForm.username, loginForm.password)
        : await demoLogin(loginForm.username, loginForm.password);
      setSession(nextSession);
      persistSession(nextSession);
      setLoginForm((current) => ({ ...current, password: "" }));
    } catch (error) {
      setLoginError(error.message);
    } finally {
      setLoginRunning(false);
    }
  }

  function logout() {
    clearStoredSession();
    setSession(null);
    setFile(null);
    setResult(null);
    setCopyStatus("Copy");
  }

  async function createScan(accessToken) {
    const timestamp = Date.now();

    return request("/api/create-scan", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        scanId: randomScanId(),
        accountId: DEFAULT_ACCOUNT_ID,
        repoFullName: "local/upload",
        prNumber: timestamp % 100000,
        headSha: `upload-${timestamp}`,
        useCase: "zip-upload-scan",
        service: "fes",
      }),
    });
  }

  async function uploadZip(uploadUrl) {
    const response = await fetch(uploadUrl, {
      method: "PUT",
      headers: { "Content-Type": "application/octet-stream" },
      body: file,
    });

    if (!response.ok) {
      const body = await parseResponse(response);
      const error = new Error(typeof body === "string" ? body : `Upload failed with ${response.status}`);
      error.status = response.status;
      error.body = body;
      throw error;
    }
  }

  async function startScan(accessToken, scanId) {
    return request("/api/start-scan", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ scanId }),
    });
  }

  async function getFindings(scanId) {
    return request(`/api/scans/${encodeURIComponent(scanId)}/findings`);
  }

  async function getStatus(scanId) {
    return request(`/api/scans/${encodeURIComponent(scanId)}/status`);
  }

  async function waitForFindings(scanId) {
    const expiresAt = Date.now() + MAX_WAIT_MS;
    let lastError = null;

    while (Date.now() < expiresAt) {
      try {
        return await getFindings(scanId);
      } catch (error) {
        lastError = error;

        try {
          const status = await getStatus(scanId);
          if (status.status === "FAILED") {
            return {
              scanId,
              status: "FAILED",
              error: "Scan finished without available findings.",
            };
          }
        } catch {
          // Keep polling findings; the final output will contain the latest findings error.
        }

        await delay(POLL_INTERVAL_MS);
      }
    }

    return {
      scanId,
      error: "Timed out waiting for findings.",
      details: lastError?.body || lastError?.message || null,
    };
  }

  async function runUploadScan(event) {
    event.preventDefault();

    if (!isZipFile(file)) {
      setResult({ error: true, message: "Select a .zip file." });
      return;
    }

    setRunning(true);
    setResult(null);
    setCopyStatus("Copy");

    try {
      const accessToken = await ensureAccessToken();
      const createdScan = await createScan(accessToken);
      await uploadZip(createdScan.uploadUrl);
      const startToken = await ensureAccessToken();
      await startScan(startToken, createdScan.scanId);
      const findings = await waitForFindings(createdScan.scanId);
      setResult(findings);
    } catch (error) {
      setResult(createErrorPayload(error));
    } finally {
      setRunning(false);
    }
  }

  if (!session) {
    return (
      <main className="login-shell">
        <section className="login-panel">
          <h1>QCA ZIP Scan</h1>
          <p>Login to upload a ZIP and get the scan JSON.</p>

          <form className="login-form" onSubmit={handleLogin}>
            <label>
              Username
              <input
                className="text-input"
                value={loginForm.username}
                autoComplete="username"
                onChange={(event) => setLoginForm((current) => ({ ...current, username: event.target.value }))}
              />
            </label>
            <label>
              Password
              <input
                className="text-input"
                type="password"
                value={loginForm.password}
                placeholder={authConfig.provider === "demo" ? "local-dev-password" : ""}
                autoComplete="current-password"
                onChange={(event) => setLoginForm((current) => ({ ...current, password: event.target.value }))}
              />
            </label>
            {loginError ? <div className="login-error">{loginError}</div> : null}
            <button className="submit-button login-submit" type="submit" disabled={loginRunning}>
              {loginRunning ? (
                <>
                  <span className="button-spinner" aria-hidden="true" />
                  Logging in
                </>
              ) : (
                "Login"
              )}
            </button>
          </form>
        </section>
      </main>
    );
  }

  return (
    <main className="app-shell">
      <section className="upload-panel">
        <div className="brand-row">
          <div>
            <h1>QCA ZIP Scan</h1>
            <p>Upload a ZIP and get the scan JSON.</p>
          </div>
          <div className="session-actions">
            <span>{sessionLabel}</span>
            <button className="logout-button" type="button" onClick={logout} disabled={running}>
              Logout
            </button>
          </div>
        </div>

        <form className="upload-form" onSubmit={runUploadScan}>
          <input
            ref={fileInputRef}
            className="hidden-input"
            type="file"
            accept=".zip,application/zip,application/x-zip-compressed"
            onChange={(event) => setFile(event.target.files?.[0] || null)}
          />

          <button
            className="file-button"
            type="button"
            onClick={() => fileInputRef.current?.click()}
            disabled={running}
          >
            {selectedFileLabel}
          </button>

          <button className="submit-button" type="submit" disabled={running || !file}>
            {running ? (
              <>
                <span className="button-spinner" aria-hidden="true" />
                Scanning
              </>
            ) : (
              "Upload"
            )}
          </button>
        </form>
      </section>

      <section className={`json-panel ${running ? "is-loading" : ""}`} aria-label="JSON result" aria-busy={running}>
        <div className="json-toolbar">
          <span>JSON</span>
          <button className="copy-button" type="button" onClick={copyJson} disabled={running}>
            {copyStatus}
          </button>
        </div>
        {running ? (
          <div className="loader" role="status">
            <div className="loader-ring" aria-hidden="true" />
            <div className="loader-text">Scanning...</div>
          </div>
        ) : null}
        <pre>{displayedJson}</pre>
      </section>
    </main>
  );
}
