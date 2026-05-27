import { useMemo, useState } from "react";

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
  return "fail";
};

async function copyJson(value) {
  await navigator.clipboard.writeText(typeof value === "string" ? value : pretty(value));
}

function MethodBadge({ method }) {
  return <span className={`method-badge ${method.toLowerCase()}`}>{method}</span>;
}

function RequestCard({ title, method, path, description, actions, fields, requestPreview }) {
  return (
    <article className="request-card">
      <div className="request-header">
        <div>
          <h3 className="request-title">{title}</h3>
          <div className="request-meta">
            <MethodBadge method={method} />
            <span>{path}</span>
          </div>
          <p className="panel-description">{description}</p>
        </div>
        <div className="button-row">{actions}</div>
      </div>

      {fields}

      <div className="divider" />
      <div className="section-grid">
        <div>
          <div className="summary-label">Request preview</div>
          <pre className="code-block">{pretty(requestPreview)}</pre>
        </div>
      </div>
    </article>
  );
}

export default function App() {
  const [baseUrl, setBaseUrl] = useState(window.location.origin);
  const [auth, setAuth] = useState({
    username: "qca-admin",
    password: "",
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
  const [inFlight, setInFlight] = useState(false);
  const [lastRequest, setLastRequest] = useState(null);
  const [lastResponse, setLastResponse] = useState(null);

  const authHeaders = useMemo(() => {
    if (!auth.accessToken) {
      return {};
    }
    return { Authorization: `Bearer ${auth.accessToken}` };
  }, [auth.accessToken]);

  const performRequest = async ({
    title,
    method,
    path,
    headers = {},
    body,
    rawUrl,
    file,
    responseTransform,
  }) => {
    const url = rawUrl || `${baseUrl.replace(/\/$/, "")}${path}`;
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

    setInFlight(true);
    setLastRequest({
      title,
      method,
      url,
      headers: requestHeaders,
      body:
        file != null
          ? { fileName: file.name, sizeBytes: file.size, type: file.type || "application/octet-stream" }
          : body ?? null,
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

      setLastResponse(normalized);

      if (!response.ok) {
        const error = new Error(`Request failed with ${response.status}`);
        error.response = normalized;
        throw error;
      }

      return normalized.body;
    } catch (error) {
      if (!error.response) {
        setLastResponse({
          title,
          status: 0,
          statusText: error.message,
          durationMs: Math.round(performance.now() - startedAt),
          headers: {},
          body: error.message,
        });
      }
      throw error;
    } finally {
      setInFlight(false);
    }
  };

  const login = async () => {
    const body = await performRequest({
      title: "Login",
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
      title: "Refresh Tokens",
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
      title: "Start Scan",
      method: "POST",
      path: "/api/start-scan",
      headers: authHeaders,
      body: { scanId: scan.scanId },
    });

  const listScans = async () =>
    performRequest({
      title: "List Scans",
      method: "GET",
      path: `/api/accounts/${encodeURIComponent(scan.accountId)}/scans?limit=50`,
    });

  const getStatus = async () =>
    performRequest({
      title: "Get Scan Status",
      method: "GET",
      path: `/api/scans/${encodeURIComponent(scan.scanId)}/status`,
    });

  const getFindings = async () =>
    performRequest({
      title: "Get Scan Findings",
      method: "GET",
      path: `/api/scans/${encodeURIComponent(scan.scanId)}/findings`,
    });

  const requestCards = [
    {
      title: "Login",
      method: "POST",
      path: "/api/auth/login",
      description: "Exchange demo credentials for access and refresh tokens.",
      requestPreview: {
        method: "POST",
        url: `${baseUrl.replace(/\/$/, "")}/api/auth/login`,
        headers: { "Content-Type": "application/json" },
        body: {
          username: auth.username,
          password: auth.password || "replace-with-demo-password",
        },
      },
      actions: (
        <button className="primary-button" onClick={login} disabled={inFlight}>
          Login
        </button>
      ),
      fields: (
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
      ),
    },
    {
      title: "Refresh Tokens",
      method: "POST",
      path: "/api/auth/refresh",
      description: "Rotate the access token using the current refresh token.",
      requestPreview: {
        method: "POST",
        url: `${baseUrl.replace(/\/$/, "")}/api/auth/refresh`,
        headers: { "Content-Type": "application/json" },
        body: { refreshToken: auth.refreshToken || "replace-with-refresh-token" },
      },
      actions: (
        <button className="secondary-button" onClick={refresh} disabled={inFlight || !auth.refreshToken}>
          Refresh
        </button>
      ),
    },
    {
      title: "Create Scan",
      method: "POST",
      path: "/api/create-scan",
      description: "Creates the scan and returns the presigned upload URL.",
      requestPreview: {
        method: "POST",
        url: `${baseUrl.replace(/\/$/, "")}/api/create-scan`,
        headers: {
          Authorization: auth.accessToken ? "Bearer <access-token>" : "Bearer <missing>",
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
      actions: (
        <button className="primary-button" onClick={createScan} disabled={inFlight || !auth.accessToken}>
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
      title: "Upload Scan Archive",
      method: "PUT",
      path: "presigned-upload-url",
      description: "Uploads the selected file to S3 using the URL returned by create-scan.",
      requestPreview: {
        method: "PUT",
        url: uploadUrl || "paste-from-create-scan-response",
        headers: { "Content-Type": "application/octet-stream" },
        body: selectedFile ? { name: selectedFile.name, sizeBytes: selectedFile.size } : null,
      },
      actions: (
        <button className="primary-button" onClick={upload} disabled={inFlight || !selectedFile || !uploadUrl}>
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
      title: "Start Scan",
      method: "POST",
      path: "/api/start-scan",
      description: "Queues the Step Functions workflow after the file is uploaded.",
      requestPreview: {
        method: "POST",
        url: `${baseUrl.replace(/\/$/, "")}/api/start-scan`,
        headers: {
          Authorization: auth.accessToken ? "Bearer <access-token>" : "Bearer <missing>",
          "Content-Type": "application/json",
        },
        body: { scanId: scan.scanId },
      },
      actions: (
        <button className="primary-button" onClick={startScan} disabled={inFlight || !auth.accessToken}>
          Start Scan
        </button>
      ),
    },
    {
      title: "List Scans",
      method: "GET",
      path: `/api/accounts/${scan.accountId || "{accountId}"}/scans?limit=50`,
      description: "Lists recent scans for the configured account.",
      requestPreview: {
        method: "GET",
        url: `${baseUrl.replace(/\/$/, "")}/api/accounts/${encodeURIComponent(scan.accountId)}/scans?limit=50`,
      },
      actions: (
        <button className="secondary-button" onClick={listScans} disabled={inFlight}>
          List
        </button>
      ),
    },
    {
      title: "Get Scan Status",
      method: "GET",
      path: `/api/scans/${scan.scanId || "{scanId}"}/status`,
      description: "Returns the current workflow status for the scan.",
      requestPreview: {
        method: "GET",
        url: `${baseUrl.replace(/\/$/, "")}/api/scans/${encodeURIComponent(scan.scanId)}/status`,
      },
      actions: (
        <button className="secondary-button" onClick={getStatus} disabled={inFlight}>
          Get Status
        </button>
      ),
    },
    {
      title: "Get Scan Findings",
      method: "GET",
      path: `/api/scans/${scan.scanId || "{scanId}"}/findings`,
      description: "Returns merged findings. If one engine failed, successful engine findings still appear here.",
      requestPreview: {
        method: "GET",
        url: `${baseUrl.replace(/\/$/, "")}/api/scans/${encodeURIComponent(scan.scanId)}/findings`,
      },
      actions: (
        <button className="secondary-button" onClick={getFindings} disabled={inFlight}>
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
            React UI for running the auth, scan, upload, status, and findings workflow from one screen. Each action
            shows the exact request payload, response time, and last response body.
          </p>
        </div>
        <div className="app-meta">
          <div className="summary-card">
            <span className="summary-label">Access Token</span>
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
            <label>Base URL</label>
            <input className="text-input" value={baseUrl} onChange={(event) => setBaseUrl(event.target.value)} />
          </div>
          <div className="toolbar-field">
            <label>Access Token</label>
            <input
              className="text-input"
              value={auth.accessToken}
              onChange={(event) => setAuth((current) => ({ ...current, accessToken: event.target.value }))}
            />
          </div>
          <div className="toolbar-field">
            <label>Refresh Token</label>
            <input
              className="text-input"
              value={auth.refreshToken}
              onChange={(event) => setAuth((current) => ({ ...current, refreshToken: event.target.value }))}
            />
          </div>
        </div>
      </section>

      <div className="app-grid">
        <div className="left-column">
          <section className="panel">
            <div className="panel-header">
              <div>
                <h2 className="panel-title">Request Sections</h2>
                <p className="panel-description">
                  Swagger-style workflow cards with editable payload fields and direct actions.
                </p>
              </div>
              <div className="status-row">
                <span className={`status-pill ${inFlight ? "warning" : "success"}`}>
                  {inFlight ? "Request in progress" : "Idle"}
                </span>
              </div>
            </div>

            <div className="request-list">
              {requestCards.map((card) => (
                <RequestCard key={card.title} {...card} />
              ))}
            </div>
          </section>
        </div>

        <div className="right-column">
          <section className="response-card">
            <div className="panel-header">
              <div>
                <h2 className="panel-title">Response Section</h2>
                <p className="panel-description">Most recent request and response, including timing.</p>
              </div>
              {lastResponse ? (
                <button className="ghost-button" onClick={() => copyJson(lastResponse.body)}>
                  Copy Body
                </button>
              ) : null}
            </div>

            {lastResponse ? (
              <div className="response-meta">
                <span className={`response-stat ${statusTone(lastResponse.status)}`}>
                  Status {lastResponse.status || "ERR"} {lastResponse.statusText || ""}
                </span>
                <span className="response-stat">{lastResponse.durationMs} ms</span>
                <span className="response-stat">{lastResponse.title}</span>
              </div>
            ) : (
              <p className="muted-text">Run any request from the left side to populate this panel.</p>
            )}

            <div className="section-grid">
              <div>
                <div className="summary-label">Last request</div>
                <pre className="code-block">{pretty(lastRequest || { message: "No request sent yet." })}</pre>
              </div>
              <div>
                <div className="summary-label">Last response</div>
                <pre className="code-block">{pretty(lastResponse || { message: "No response yet." })}</pre>
              </div>
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
      </div>
    </div>
  );
}
