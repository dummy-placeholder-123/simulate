import { useEffect, useMemo, useRef, useState } from "react";

const DEFAULT_ACCOUNT_ID = "564061926474";
const DEFAULT_USERNAME = "qca-admin";
const DEFAULT_PASSWORD = "local-dev-password";
const MAX_WAIT_MS = 190_000;
const POLL_INTERVAL_MS = 3_000;

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

export default function App() {
  const [baseUrl, setBaseUrl] = useState(window.location.origin);
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
      } catch {
        // Local development works through the Vite proxy; no config file is required.
      }
    }

    loadRuntimeConfig();
    return () => {
      ignore = true;
    };
  }, []);

  const selectedFileLabel = useMemo(() => {
    if (!file) {
      return "Choose ZIP";
    }

    return `${file.name} (${file.size.toLocaleString()} bytes)`;
  }, [file]);

  const displayedJson = useMemo(() => pretty(result || { result: null }), [result]);

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

  async function login() {
    return request("/api/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        username: DEFAULT_USERNAME,
        password: DEFAULT_PASSWORD,
      }),
    });
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
      const auth = await login();
      const createdScan = await createScan(auth.accessToken);
      await uploadZip(createdScan.uploadUrl);
      await startScan(auth.accessToken, createdScan.scanId);
      const findings = await waitForFindings(createdScan.scanId);
      setResult(findings);
    } catch (error) {
      setResult(createErrorPayload(error));
    } finally {
      setRunning(false);
    }
  }

  return (
    <main className="app-shell">
      <section className="upload-panel">
        <div className="brand-row">
          <div>
            <h1>QCA ZIP Scan</h1>
            <p>Upload a ZIP and get the scan JSON.</p>
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
