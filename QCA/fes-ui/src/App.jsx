import { useEffect, useMemo, useState } from "react";

const DEMO_USERNAME = "qca-admin";
const DEFAULT_DEMO_PASSWORD = "local-dev-password";

const randomScanId = () =>
  `scan-${Math.random().toString(36).slice(2, 8)}-${Date.now().toString(36).slice(-4)}`;

const pretty = (value) => {
  if (value == null || value === "") {
    return "";
  }
  if (typeof value === "string") {
    return value;
  }
  return JSON.stringify(value, null, 2);
};

const statusTone = (status) => {
  if (status >= 200 && status < 300) {
    return "ok";
  }
  if (status === 0) {
    return "warn";
  }
  return "fail";
};

const normalizeBaseUrl = (value) => (value || "").trim().replace(/\/$/, "");

async function copyJson(value) {
  await navigator.clipboard.writeText(typeof value === "string" ? value : pretty(value));
}

function MethodBadge({ method }) {
  return <span className={`method-badge ${method.toLowerCase()}`}>{method}</span>;
}

function ResponseBlock({ requestKey, state }) {
  const response = state?.lastResponse;
  const request = state?.lastRequest;

  return (
    <div className="request-sections-grid">
      <div className="request-section">
        <div className="summary-label">Request</div>
        <pre className="code-block">{pretty(request || { message: "No request sent yet." })}</pre>
      </div>
      <div className="request-section">
        <div className="response-header-row">
          <div className="summary-label">Response</div>
          {response ? (
            <button className="ghost-button small-button" type="button" onClick={() => copyJson(response.body)}>
              Copy
            </button>
          ) : null}
        </div>
        {response ? (
          <div className="response-meta">
            <span className={`response-stat ${statusTone(response.status)}`}>
              Status {response.status || "ERR"} {response.statusText || ""}
            </span>
            <span className="response-stat">{response.durationMs} ms</span>
            <span className="response-stat">{requestKey}</span>
          </div>
        ) : (
          <p className="muted-text">Run this request to populate the response section.</p>
        )}
        <pre className="code-block">{pretty(response || { message: "No response yet." })}</pre>
      </div>
    </div>
  );
}

function RequestPanel({
  id,
  title,
  method,
  path,
  description,
  actions,
  fields,
  requestPreview,
  state,
  defaultOpen = false,
}) {
  return (
    <details className="request-panel" open={defaultOpen}>
      <summary className="request-toggle">
        <div className="request-toggle-main">
          <div className="request-title-row">
            <h3 className="request-title">{title}</h3>
            <MethodBadge method={method} />
          </div>
          <div className="request-path">{path}</div>
          <p className="panel-description">{description}</p>
        </div>
        <div className="request-toggle-side">
          {state?.lastResponse ? (
            <span className={`status-pill ${statusTone(state.lastResponse.status)}`}>
              {state.lastResponse.status || "ERR"} · {state.lastResponse.durationMs} ms
            </span>
          ) : (
            <span className="status-pill neutral">Not run</span>
          )}
        </div>
      </summary>

      <div className="request-body">
        <div className="request-actions">{actions}</div>
        {fields}
        <div className="request-section">
          <div className="summary-label">Request preview</div>
          <pre className="code-block">{pretty(requestPreview)}</pre>
        </div>
        <ResponseBlock requestKey={id} state={state} />
      </div>
    </details>
  );
}

export default function App() {
  const [runtimeConfig, setRuntimeConfig] = useState(null);
  const [runtimeConfigError, setRuntimeConfigError] = useState("");
  const [selectedEnv, setSelectedEnv] = useState("");
  const [baseUrl, setBaseUrl] = useState(window.location.origin);
  const [showAdvancedAuth, setShowAdvancedAuth] = useState(false);
  const [auth, setAuth] = useState({
    username: DEMO_USERNAME,
    password: DEFAULT_DEMO_PASSWORD,
    accessToken: "",
    refreshToken: "",
  });
  const [scan, setScan] = useState({
    scanId: randomScanId(),
    accountId: "564061926474",
    repoFullName: "devashishrane/qca",
    prNumber: "42",
    headSha: "abc123def4567890",
    useCase: "github-pr-scan",
    service: "fes",
  });
  const [uploadUrl, setUploadUrl] = useState("");
  const [uploadObjectKey, setUploadObjectKey] = useState("");
  const [selectedFile, setSelectedFile] = useState(null);
  const [requestState, setRequestState] = useState({});

  useEffect(() => {
    let ignore = false;

    const loadRuntimeConfig = async () => {
      try {
        const response = await fetch("/runtime-config.json", { cache: "no-store" });
        if (!response.ok) {
          throw new Error(`runtime-config request failed with ${response.status}`);
        }

        const config = await response.json();
        if (ignore) {
          return;
        }

        setRuntimeConfig(config);
        setRuntimeConfigError("");
        const defaultEnv = config.defaultEnv || Object.keys(config.environments || {})[0] || "";
        setSelectedEnv(defaultEnv);
        const defaultBaseUrl = config.environments?.[defaultEnv]?.apiBaseUrl;
        if (defaultBaseUrl) {
          setBaseUrl(defaultBaseUrl);
        }
      } catch (error) {
        if (!ignore) {
          setRuntimeConfigError(error.message);
        }
      }
    };

    loadRuntimeConfig();
    return () => {
      ignore = true;
    };
  }, []);

  const authHeaders = useMemo(() => {
    if (!auth.accessToken) {
      return {};
    }
    return { Authorization: `Bearer ${auth.accessToken}` };
  }, [auth.accessToken]);

  const anyRequestInFlight = Object.values(requestState).some((entry) => entry?.inFlight);

  const updateRequestState = (requestKey, patch) => {
    setRequestState((current) => ({
      ...current,
      [requestKey]: {
        ...current[requestKey],
        ...patch,
      },
    }));
  };

  const performRequest = async ({
    requestKey,
    title,
    method,
    path,
    headers = {},
    body,
    rawUrl,
    file,
    responseTransform,
  }) => {
    const url = rawUrl || `${normalizeBaseUrl(baseUrl)}${path}`;
    const requestHeaders = { ...headers };
    const init = { method, headers: requestHeaders };

    if (body != null && file == null) {
      requestHeaders["Content-Type"] = requestHeaders["Content-Type"] || "application/json";
      init.body = typeof body === "string" ? body : JSON.stringify(body);
    }

    if (file) {
      requestHeaders["Content-Type"] = "application/octet-stream";
      init.body = file;
    }

    updateRequestState(requestKey, {
      inFlight: true,
      lastRequest: {
        title,
        method,
        url,
        headers: requestHeaders,
        body:
          file != null
            ? { fileName: file.name, sizeBytes: file.size, type: file.type || "application/octet-stream" }
            : body ?? null,
      },
    });

    const startedAt = performance.now();

    try {
      const response = await fetch(url, init);
      const durationMs = Math.round(performance.now() - startedAt);
      const contentType = response.headers.get("content-type") || "";
      const text = await response.text();
      let payload = text;

      if (contentType.includes("application/json") && text) {
        try {
          payload = JSON.parse(text);
        } catch {
          payload = text;
        }
      }

      const normalized = {
        title,
        status: response.status,
        statusText: response.statusText,
        durationMs,
        headers: Object.fromEntries(response.headers.entries()),
        body: responseTransform ? responseTransform(payload) : payload,
      };

      updateRequestState(requestKey, {
        inFlight: false,
        lastResponse: normalized,
      });

      if (!response.ok) {
        const error = new Error(`Request failed with ${response.status}`);
        error.response = normalized;
        throw error;
      }

      return normalized.body;
    } catch (error) {
      updateRequestState(requestKey, {
        inFlight: false,
        lastResponse:
          error.response || {
            title,
            status: 0,
            statusText: error.message,
            durationMs: Math.round(performance.now() - startedAt),
            headers: {},
            body: error.message,
          },
      });
      throw error;
    }
  };

  const login = async () => {
    const body = await performRequest({
      requestKey: "login",
      title: "Demo Session Login",
      method: "POST",
      path: "/api/auth/login",
      body: {
        username: auth.username,
        password: auth.password,
      },
    });

    setAuth((current) => ({
      ...current,
      accessToken: body.accessToken || "",
      refreshToken: body.refreshToken || "",
    }));
  };

  const refresh = async () => {
    const body = await performRequest({
      requestKey: "refresh",
      title: "Refresh Session",
      method: "POST",
      path: "/api/auth/refresh",
      body: {
        refreshToken: auth.refreshToken,
      },
    });

    setAuth((current) => ({
      ...current,
      accessToken: body.accessToken || "",
      refreshToken: body.refreshToken || current.refreshToken,
    }));
  };

  const createScan = async () => {
    const body = await performRequest({
      requestKey: "create-scan",
      title: "Create Scan",
      method: "POST",
      path: "/api/create-scan",
      headers: authHeaders,
      body: {
        scanId: scan.scanId,
        accountId: scan.accountId,
        repoFullName: scan.repoFullName,
        prNumber: Number(scan.prNumber),
        headSha: scan.headSha,
        useCase: scan.useCase,
        service: scan.service,
      },
    });

    setUploadUrl(body.uploadUrl || "");
    setUploadObjectKey(body.uploadObjectKey || "");
  };

  const upload = async () => {
    if (!uploadUrl) {
      throw new Error("Create a scan first to get a presigned upload URL.");
    }
    if (!selectedFile) {
      throw new Error("Choose a file before uploading.");
    }

    await performRequest({
      requestKey: "upload",
      title: "Upload Scan Archive",
      method: "PUT",
      rawUrl: uploadUrl,
      file: selectedFile,
      responseTransform: () => ({
        uploaded: true,
        fileName: selectedFile.name,
        sizeBytes: selectedFile.size,
      }),
    });
  };

  const startScan = async () =>
    performRequest({
      requestKey: "start-scan",
      title: "Start Scan",
      method: "POST",
      path: "/api/start-scan",
      headers: authHeaders,
      body: { scanId: scan.scanId },
    });

  const listScans = async () =>
    performRequest({
      requestKey: "list-scans",
      title: "List Scans",
      method: "GET",
      path: `/api/accounts/${encodeURIComponent(scan.accountId)}/scans?limit=50`,
    });

  const getStatus = async () =>
    performRequest({
      requestKey: "get-status",
      title: "Get Scan Status",
      method: "GET",
      path: `/api/scans/${encodeURIComponent(scan.scanId)}/status`,
    });

  const getFindings = async () =>
    performRequest({
      requestKey: "get-findings",
      title: "Get Scan Findings",
      method: "GET",
      path: `/api/scans/${encodeURIComponent(scan.scanId)}/findings`,
    });

  const handleEnvChange = (nextEnv) => {
    setSelectedEnv(nextEnv);
    const configuredUrl = runtimeConfig?.environments?.[nextEnv]?.apiBaseUrl;
    if (configuredUrl) {
      setBaseUrl(configuredUrl);
    }
  };

  const requestPanels = [
    {
      id: "login",
      title: "Demo Session",
      method: "POST",
      path: "/api/auth/login",
      description: "Loads access and refresh tokens for the built-in demo user. Advanced auth input stays hidden unless you need to override it.",
      requestPreview: {
        method: "POST",
        url: `${normalizeBaseUrl(baseUrl)}/api/auth/login`,
        headers: { "Content-Type": "application/json" },
        body: {
          username: auth.username,
          password: auth.password,
        },
      },
      defaultOpen: true,
      actions: (
        <>
          <button className="primary-button" type="button" onClick={login} disabled={requestState.login?.inFlight}>
            Load Session
          </button>
          <button
            className="secondary-button"
            type="button"
            onClick={() => setShowAdvancedAuth((current) => !current)}
          >
            {showAdvancedAuth ? "Hide advanced" : "Advanced"}
          </button>
        </>
      ),
      fields: (
        <div className="simple-auth-block">
          <div className="small-note">
            Demo user <code>{auth.username}</code>. Session tokens are stored in memory after login.
          </div>
          {showAdvancedAuth ? (
            <div className="form-grid">
              <div className="form-field">
                <label>Username</label>
                <input
                  className="text-input"
                  value={auth.username}
                  onChange={(event) => setAuth((current) => ({ ...current, username: event.target.value }))}
                />
              </div>
              <div className="form-field">
                <label>Password</label>
                <input
                  type="password"
                  className="text-input"
                  value={auth.password}
                  onChange={(event) => setAuth((current) => ({ ...current, password: event.target.value }))}
                />
              </div>
            </div>
          ) : null}
        </div>
      ),
    },
    {
      id: "refresh",
      title: "Refresh Session",
      method: "POST",
      path: "/api/auth/refresh",
      description: "Rotates the access token using the current refresh token already loaded in memory.",
      requestPreview: {
        method: "POST",
        url: `${normalizeBaseUrl(baseUrl)}/api/auth/refresh`,
        headers: { "Content-Type": "application/json" },
        body: { refreshToken: auth.refreshToken || "<not loaded>" },
      },
      actions: (
        <button
          className="secondary-button"
          type="button"
          onClick={refresh}
          disabled={requestState.refresh?.inFlight || !auth.refreshToken}
        >
          Refresh
        </button>
      ),
    },
    {
      id: "create-scan",
      title: "Create Scan",
      method: "POST",
      path: "/api/create-scan",
      description: "Creates the scan and returns the presigned upload URL. The scan id generator stays local to this page.",
      requestPreview: {
        method: "POST",
        url: `${normalizeBaseUrl(baseUrl)}/api/create-scan`,
        headers: {
          Authorization: auth.accessToken ? "Bearer <loaded>" : "Bearer <missing>",
          "Content-Type": "application/json",
        },
        body: {
          scanId: scan.scanId,
          accountId: scan.accountId,
          repoFullName: scan.repoFullName,
          prNumber: Number(scan.prNumber || 0),
          headSha: scan.headSha,
          useCase: scan.useCase,
          service: scan.service,
        },
      },
      defaultOpen: true,
      actions: (
        <button
          className="primary-button"
          type="button"
          onClick={createScan}
          disabled={requestState["create-scan"]?.inFlight || !auth.accessToken}
        >
          Create Scan
        </button>
      ),
      fields: (
        <div className="form-grid">
          <div className="form-field">
            <label>Scan ID</label>
            <div className="inline-actions">
              <input
                className="text-input"
                value={scan.scanId}
                onChange={(event) => setScan((current) => ({ ...current, scanId: event.target.value }))}
              />
              <button
                className="secondary-button"
                type="button"
                onClick={() => setScan((current) => ({ ...current, scanId: randomScanId() }))}
              >
                Random
              </button>
            </div>
          </div>
          <div className="form-field">
            <label>Account ID</label>
            <input
              className="text-input"
              value={scan.accountId}
              onChange={(event) => setScan((current) => ({ ...current, accountId: event.target.value }))}
            />
          </div>
          <div className="form-field span-2">
            <label>Repo Full Name</label>
            <input
              className="text-input"
              value={scan.repoFullName}
              onChange={(event) => setScan((current) => ({ ...current, repoFullName: event.target.value }))}
            />
          </div>
          <div className="form-field">
            <label>PR Number</label>
            <input
              className="text-input"
              value={scan.prNumber}
              onChange={(event) => setScan((current) => ({ ...current, prNumber: event.target.value }))}
            />
          </div>
          <div className="form-field">
            <label>Head SHA</label>
            <input
              className="text-input"
              value={scan.headSha}
              onChange={(event) => setScan((current) => ({ ...current, headSha: event.target.value }))}
            />
          </div>
          <div className="form-field">
            <label>Use Case</label>
            <input
              className="text-input"
              value={scan.useCase}
              onChange={(event) => setScan((current) => ({ ...current, useCase: event.target.value }))}
            />
          </div>
          <div className="form-field">
            <label>Service</label>
            <input
              className="text-input"
              value={scan.service}
              onChange={(event) => setScan((current) => ({ ...current, service: event.target.value }))}
            />
          </div>
        </div>
      ),
    },
    {
      id: "upload",
      title: "Upload Scan Archive",
      method: "PUT",
      path: "presigned-upload-url",
      description: "Uploads the selected file to the presigned S3 URL returned from create-scan.",
      requestPreview: {
        method: "PUT",
        url: uploadUrl || "<create scan first>",
        headers: { "Content-Type": "application/octet-stream" },
        body: selectedFile ? { name: selectedFile.name, sizeBytes: selectedFile.size } : null,
      },
      actions: (
        <button
          className="primary-button"
          type="button"
          onClick={upload}
          disabled={requestState.upload?.inFlight || !selectedFile || !uploadUrl}
        >
          Upload File
        </button>
      ),
      fields: (
        <div className="form-grid">
          <div className="form-field span-2">
            <label>Upload URL</label>
            <textarea
              className="textarea-input"
              value={uploadUrl}
              onChange={(event) => setUploadUrl(event.target.value)}
            />
          </div>
          <div className="form-field span-2">
            <label>File</label>
            <input
              type="file"
              className="file-input"
              onChange={(event) => setSelectedFile(event.target.files?.[0] || null)}
            />
            <div className="small-note">
              {selectedFile
                ? `Selected ${selectedFile.name} (${selectedFile.size.toLocaleString()} bytes)`
                : "Choose the archive to upload."}
            </div>
          </div>
        </div>
      ),
    },
    {
      id: "start-scan",
      title: "Start Scan",
      method: "POST",
      path: "/api/start-scan",
      description: "Starts the Step Functions workflow after the file upload is complete.",
      requestPreview: {
        method: "POST",
        url: `${normalizeBaseUrl(baseUrl)}/api/start-scan`,
        headers: {
          Authorization: auth.accessToken ? "Bearer <loaded>" : "Bearer <missing>",
          "Content-Type": "application/json",
        },
        body: { scanId: scan.scanId },
      },
      actions: (
        <button
          className="primary-button"
          type="button"
          onClick={startScan}
          disabled={requestState["start-scan"]?.inFlight || !auth.accessToken}
        >
          Start Scan
        </button>
      ),
    },
    {
      id: "list-scans",
      title: "List Scans",
      method: "GET",
      path: `/api/accounts/${scan.accountId || "{accountId}"}/scans?limit=50`,
      description: "Lists the most recent scans for the configured account.",
      requestPreview: {
        method: "GET",
        url: `${normalizeBaseUrl(baseUrl)}/api/accounts/${encodeURIComponent(scan.accountId)}/scans?limit=50`,
      },
      actions: (
        <button
          className="secondary-button"
          type="button"
          onClick={listScans}
          disabled={requestState["list-scans"]?.inFlight}
        >
          List
        </button>
      ),
    },
    {
      id: "get-status",
      title: "Get Scan Status",
      method: "GET",
      path: `/api/scans/${scan.scanId || "{scanId}"}/status`,
      description: "Reads the workflow status for the current scan id.",
      requestPreview: {
        method: "GET",
        url: `${normalizeBaseUrl(baseUrl)}/api/scans/${encodeURIComponent(scan.scanId)}/status`,
      },
      actions: (
        <button
          className="secondary-button"
          type="button"
          onClick={getStatus}
          disabled={requestState["get-status"]?.inFlight}
        >
          Get Status
        </button>
      ),
    },
    {
      id: "get-findings",
      title: "Get Scan Findings",
      method: "GET",
      path: `/api/scans/${scan.scanId || "{scanId}"}/findings`,
      description: "Returns merged findings. If one engine failed, the successful engine output can still appear here.",
      requestPreview: {
        method: "GET",
        url: `${normalizeBaseUrl(baseUrl)}/api/scans/${encodeURIComponent(scan.scanId)}/findings`,
      },
      actions: (
        <button
          className="secondary-button"
          type="button"
          onClick={getFindings}
          disabled={requestState["get-findings"]?.inFlight}
        >
          Get Findings
        </button>
      ),
    },
  ];

  return (
    <div className="app-shell">
      <header className="app-header">
        <div>
          <h1 className="app-title">QCA API Explorer</h1>
          <p className="app-subtitle">
            Expand any request, edit the payload, send it, and inspect that request’s own response block. The UI keeps
            demo session handling in the background so the main workflow stays focused.
          </p>
        </div>
        <div className="app-meta">
          <div className="summary-card">
            <span className="summary-label">Session</span>
            <div className="summary-value">{auth.accessToken ? "Loaded" : "Not loaded"}</div>
          </div>
          <div className="summary-card">
            <span className="summary-label">Current Scan ID</span>
            <div className="summary-value">{scan.scanId}</div>
          </div>
          <div className="summary-card">
            <span className="summary-label">Upload Object Key</span>
            <div className="summary-value">{uploadObjectKey || "Not created yet"}</div>
          </div>
        </div>
      </header>

      <section className="toolbar-card">
        <div className="toolbar">
          <div className="toolbar-field">
            <label>Environment</label>
            <select
              className="text-input"
              value={selectedEnv}
              onChange={(event) => handleEnvChange(event.target.value)}
              disabled={!runtimeConfig || Object.keys(runtimeConfig.environments || {}).length === 0}
            >
              <option value="">Select environment</option>
              {Object.entries(runtimeConfig?.environments || {}).map(([envName, config]) => (
                <option key={envName} value={envName}>
                  {envName}
                </option>
              ))}
            </select>
          </div>
          <div className="toolbar-field toolbar-wide">
            <label>Base URL</label>
            <input className="text-input" value={baseUrl} onChange={(event) => setBaseUrl(event.target.value)} />
          </div>
          <div className="toolbar-field toolbar-status">
            <label>Request State</label>
            <span className={`status-pill ${anyRequestInFlight ? "warning" : "success"}`}>
              {anyRequestInFlight ? "Request in progress" : "Idle"}
            </span>
          </div>
        </div>
        {runtimeConfigError ? (
          <div className="small-note">runtime-config.json not loaded: {runtimeConfigError}</div>
        ) : null}
      </section>

      <section className="panel">
        <div className="panel-header">
          <div>
            <h2 className="panel-title">Request Sections</h2>
            <p className="panel-description">
              Each request is isolated in its own dropdown with editable inputs, request preview, and response output.
            </p>
          </div>
        </div>

        <div className="request-list">
          {requestPanels.map((panel) => (
            <RequestPanel key={panel.id} {...panel} state={requestState[panel.id]} />
          ))}
        </div>
      </section>

      <section className="panel">
        <div className="panel-header">
          <div>
            <h2 className="panel-title">Workflow Notes</h2>
            <p className="panel-description">Useful behavior in the current backend flow.</p>
          </div>
        </div>
        <ul className="hint-list">
          <li>Create scan first. The API returns the presigned upload URL needed for file upload.</li>
          <li>Upload must finish before starting the scan workflow.</li>
          <li>Each worker has a 3 minute Step Functions timeout.</li>
          <li>
            If one engine succeeds and the other fails, status becomes <code>FAILED</code> but findings can still
            return the successful engine output.
          </li>
          <li>
            Requests include <code>X-Trace-Id</code> and <code>X-Span-Id</code> in responses for log correlation.
          </li>
        </ul>
      </section>
    </div>
  );
}
