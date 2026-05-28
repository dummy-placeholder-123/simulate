import { useEffect, useState } from "react";

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

const normalizeBaseUrl = (value) => (value || "").trim().replace(/\/$/, "");

async function copyJson(value) {
  await navigator.clipboard.writeText(typeof value === "string" ? value : pretty(value));
}

function MethodBadge({ method }) {
  return <span className={`method-badge ${method.toLowerCase()}`}>{method}</span>;
}

function ResponsePanel({ response }) {
  if (!response) {
    return (
      <div className="response-panel">
        <div className="summary-label">Response</div>
        <pre className="code-block">{pretty({ message: "No response yet." })}</pre>
      </div>
    );
  }

  return (
    <div className="response-panel">
      <div className="response-header">
        <div className="response-meta">
          <span className={`response-stat ${statusTone(response.status)}`}>
            Status {response.status || "ERR"} {response.statusText || ""}
          </span>
          <span className="response-stat">{response.durationMs} ms</span>
        </div>
        <button className="ghost-button" type="button" onClick={() => copyJson(response.body)}>
          Copy Body
        </button>
      </div>
      <pre className="code-block">{pretty(response.body)}</pre>
    </div>
  );
}

function RequestSection({
  title,
  method,
  path,
  description,
  defaultOpen = false,
  fields,
  actions,
  requestPreview,
  response,
}) {
  return (
    <details className="request-section" open={defaultOpen}>
      <summary className="request-summary">
        <div className="request-summary-main">
          <h3 className="request-title">{title}</h3>
          <div className="request-meta">
            <MethodBadge method={method} />
            <span>{path}</span>
          </div>
        </div>
        {response ? (
          <div className="request-summary-status">
            <span className={`response-stat ${statusTone(response.status)}`}>{response.status || "ERR"}</span>
            <span className="response-stat">{response.durationMs} ms</span>
          </div>
        ) : null}
      </summary>

      <div className="request-body">
        <p className="panel-description">{description}</p>
        {fields ? <div className="request-fields">{fields}</div> : null}
        <div className="button-row">{actions}</div>
        <div className="section-panels">
          <div className="preview-panel">
            <div className="summary-label">Request</div>
            <pre className="code-block">{pretty(requestPreview)}</pre>
          </div>
          <ResponsePanel response={response} />
        </div>
      </div>
    </details>
  );
}

export default function App() {
  const [runtimeConfig, setRuntimeConfig] = useState(null);
  const [runtimeConfigError, setRuntimeConfigError] = useState("");
  const [selectedEnv, setSelectedEnv] = useState("");
  const [baseUrl, setBaseUrl] = useState(window.location.origin);
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
  const [requestLog, setRequestLog] = useState({});
  const [responseLog, setResponseLog] = useState({});

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
        if (ignore) {
          return;
        }
        setRuntimeConfigError(error.message);
      }
    };

    loadRuntimeConfig();
    return () => {
      ignore = true;
    };
  }, []);

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
    const normalizedBaseUrl = normalizeBaseUrl(baseUrl);
    const url = rawUrl || `${normalizedBaseUrl}${path}`;
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
    setRequestLog((current) => ({
      ...current,
      [title]: {
        title,
        method,
        url,
        headers: requestHeaders,
        body:
          file != null
            ? { fileName: file.name, sizeBytes: file.size, type: file.type || "application/octet-stream" }
            : body ?? null,
      },
    }));

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

      setResponseLog((current) => ({
        ...current,
        [title]: normalized,
      }));

      if (!response.ok) {
        const error = new Error(`Request failed with ${response.status}`);
        error.response = normalized;
        throw error;
      }

      return normalized.body;
    } catch (error) {
      if (!error.response) {
        setResponseLog((current) => ({
          ...current,
          [title]: {
            title,
            status: 0,
            statusText: error.message,
            durationMs: Math.round(performance.now() - startedAt),
            headers: {},
            body: error.message,
          },
        }));
      }
      throw error;
    } finally {
      setInFlight(false);
    }
  };

  const createScan = async () => {
    const body = await performRequest({
      title: "Create Scan",
      method: "POST",
      path: "/api/create-scan",
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

  const handleEnvChange = (nextEnv) => {
    setSelectedEnv(nextEnv);
    const configuredUrl = runtimeConfig?.environments?.[nextEnv]?.apiBaseUrl;
    if (configuredUrl) {
      setBaseUrl(configuredUrl);
    }
  };

  const sections = [
    {
      title: "Create Scan",
      method: "POST",
      path: "/api/create-scan",
      description: "Create a scan and get the presigned upload URL.",
      defaultOpen: true,
      requestPreview: {
        method: "POST",
        url: `${normalizeBaseUrl(baseUrl)}/api/create-scan`,
        headers: {
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
        <button className="primary-button" onClick={createScan} disabled={inFlight}>
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
                className="icon-button"
                type="button"
                onClick={() => setScan((current) => ({ ...current, scanId: randomScanId() }))}
                title="Generate random scan ID"
              >
                New
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
      description: "Upload the archive to S3 using the URL returned from create-scan.",
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
      description: "Queue the scan workflow after the file upload completes.",
      requestPreview: {
        method: "POST",
        url: `${normalizeBaseUrl(baseUrl)}/api/start-scan`,
        headers: { "Content-Type": "application/json" },
        body: { scanId: scan.scanId },
      },
      actions: (
        <button className="primary-button" onClick={startScan} disabled={inFlight}>
          Start Scan
        </button>
      ),
    },
    {
      title: "List Scans",
      method: "GET",
      path: `/api/accounts/${scan.accountId || "{accountId}"}/scans?limit=50`,
      description: "List recent scans for the current account.",
      requestPreview: {
        method: "GET",
        url: `${normalizeBaseUrl(baseUrl)}/api/accounts/${encodeURIComponent(scan.accountId)}/scans?limit=50`,
      },
      actions: (
        <button className="secondary-button" onClick={listScans} disabled={inFlight}>
          List Scans
        </button>
      ),
    },
    {
      title: "Get Scan Status",
      method: "GET",
      path: `/api/scans/${scan.scanId || "{scanId}"}/status`,
      description: "Return the current workflow status for the scan.",
      requestPreview: {
        method: "GET",
        url: `${normalizeBaseUrl(baseUrl)}/api/scans/${encodeURIComponent(scan.scanId)}/status`,
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
      description: "Return merged findings. Successful engine output can still appear even when overall status is FAILED.",
      requestPreview: {
        method: "GET",
        url: `${normalizeBaseUrl(baseUrl)}/api/scans/${encodeURIComponent(scan.scanId)}/findings`,
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
      <header className="hero-panel">
        <div className="hero-copy">
          <h1 className="app-title">QCA API Explorer</h1>
          <p className="app-subtitle">
            Create a scan, upload the archive, start the workflow, and inspect status or findings from one page.
            Each request keeps its own request and response history.
          </p>
        </div>
        <div className="hero-meta">
          <div className="summary-card">
            <span className="summary-label">API Access</span>
            <div className="summary-value">Open</div>
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

      <section className="top-bar">
        <div className="top-bar-grid">
          <div className="toolbar-field">
            <label>Environment</label>
            <select
              className="text-input"
              value={selectedEnv}
              onChange={(event) => handleEnvChange(event.target.value)}
              disabled={!runtimeConfig || Object.keys(runtimeConfig.environments || {}).length === 0}
            >
              <option value="">Select environment</option>
              {Object.entries(runtimeConfig?.environments || {}).map(([envName]) => (
                <option key={envName} value={envName}>
                  {envName}
                </option>
              ))}
            </select>
          </div>
          <div className="toolbar-field toolbar-span-2">
            <label>Base URL</label>
            <input className="text-input" value={baseUrl} onChange={(event) => setBaseUrl(event.target.value)} />
          </div>
        </div>
        {runtimeConfigError ? <div className="small-note">runtime-config.json not loaded: {runtimeConfigError}</div> : null}
      </section>

      <section className="panel">
        <div className="panel-header">
          <div>
            <h2 className="panel-title">Requests</h2>
            <p className="panel-description">Each section sends one request and keeps its own response panel.</p>
          </div>
          <span className={`status-pill ${inFlight ? "warning" : "success"}`}>
            {inFlight ? "Request in progress" : "Idle"}
          </span>
        </div>

        <div className="request-list">
          {sections.map((section) => (
            <RequestSection
              key={section.title}
              {...section}
              response={responseLog[section.title]}
              requestPreview={requestLog[section.title] || section.requestPreview}
            />
          ))}
        </div>
      </section>

      <section className="panel">
        <div className="panel-header">
          <div>
            <h2 className="panel-title">Workflow Notes</h2>
            <p className="panel-description">Current backend behavior that matters while testing.</p>
          </div>
        </div>
        <ul className="hint-list">
          <li>Create the scan first. That response contains the presigned upload URL.</li>
          <li>Upload must finish before starting the workflow.</li>
          <li>Each worker branch has a 3 minute Step Functions timeout.</li>
          <li>
            If one engine succeeds and the other fails, overall status can be <code>FAILED</code> while findings still
            return the successful engine output.
          </li>
          <li>
            Responses include <code>X-Trace-Id</code> and <code>X-Span-Id</code> for log correlation.
          </li>
        </ul>
      </section>
    </div>
  );
}
