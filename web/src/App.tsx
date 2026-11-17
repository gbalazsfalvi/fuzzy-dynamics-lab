import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import {
  api,
  defaultForm,
  isActive,
  requestFrom,
  short,
  validateForm,
} from "./data";
import type { FormValues, Job, Model, Preset } from "./data";
import { BoundPlot, InitialProfile, StatePlot } from "./charts";
import { reconcileJobSelection } from "./job-selection";
import type { JobSelection } from "./job-selection";

const strategies = [
  ["MAXIMUM_BOUND", "Maximum bound · fair"],
  ["UNIFORM", "Uniform · FIFO"],
  ["NEXT_STEP_BOUND", "Next-step bound"],
  ["PREDICTED_GAIN_PER_WORK", "Predicted gain / work"],
  ["RANKED", "Learned ranking"],
];
type Evidence = {
  title?: string;
  scope?: string;
  generatedAtUtc?: string;
  sourceSha256?: string;
  verification?: unknown;
  summary?: string;
  comparisons?: Record<string, string | number | null>[];
  artifacts?: { label: string; url: string }[];
  [key: string]: unknown;
};
function Arrow({ stop = false }: { stop?: boolean }) {
  return (
    <svg
      width="16"
      height="16"
      viewBox="0 0 16 16"
      fill="none"
      aria-hidden="true"
    >
      {stop ? (
        <rect x="4" y="4" width="8" height="8" fill="currentColor" />
      ) : (
        <path d="M3 8h10M9 4l4 4-4 4" stroke="currentColor" strokeWidth="1.5" />
      )}
    </svg>
  );
}
function readable(value: string) {
  return value.toLowerCase().replaceAll("_", " ");
}
function seconds(value: string | undefined) {
  return value ? `${short(Number(value) / 1e9, 3)} s` : "—";
}

export default function App() {
  const [presets, setPresets] = useState<Preset[]>([]);
  const [models, setModels] = useState<Model[]>([]);
  const [jobs, setJobs] = useState<Job[]>([]);
  const [selection, setSelection] = useState<JobSelection>({
    job: null,
    notice: "",
  });
  const job = selection.job;
  const [form, setForm] = useState<FormValues>(defaultForm);
  const [online, setOnline] = useState(false);
  const [connecting, setConnecting] = useState(true);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [view, setView] = useState<"workbench" | "evidence">("workbench");
  const [time, setTime] = useState(0);
  const [snapshotName, setSnapshotName] = useState<"best" | "last">("best");
  const [enclosures, setEnclosures] = useState(true);
  const [curveMode, setCurveMode] = useState<"horizon" | "refinement">(
    "refinement",
  );
  const [selectedCell, setSelectedCell] = useState("");
  const [evidence, setEvidence] = useState<Evidence | null>(null);
  const [evidenceStatus, setEvidenceStatus] = useState<
    "loading" | "available" | "absent"
  >("loading");

  function setJob(next: Job) {
    setSelection({ job: next, notice: "" });
  }

  async function connect() {
    setConnecting(true);
    try {
      const [presetList, modelList, jobList] = await Promise.all([
        api<Preset[]>("/presets"),
        api<Model[]>("/models"),
        api<Job[]>("/jobs"),
      ]);
      setPresets(presetList);
      setModels(modelList);
      setJobs(jobList);
      setSelection((current) => reconcileJobSelection(current, jobList));
      setTime(0);
      setSelectedCell("");
      setSnapshotName("best");
      setOnline(true);
      setError("");
      setForm((current) => ({
        ...current,
        presetId: presetList.some((p) => p.id === current.presetId)
          ? current.presetId
          : (presetList[0]?.id ?? ""),
      }));
    } catch (caught) {
      setOnline(false);
      setError(
        `Cannot reach the local API. Start the backend on port 4180, then reconnect. ${caught instanceof Error ? caught.message : ""}`,
      );
    } finally {
      setConnecting(false);
    }
  }
  useEffect(() => {
    void connect();
  }, []);
  useEffect(() => {
    let alive = true;
    void fetch("/results.json")
      .then((response) => {
        if (
          !response.ok ||
          !response.headers.get("content-type")?.includes("json")
        )
          throw new Error("No saved bundle");
        return response.json() as Promise<Evidence>;
      })
      .then((data) => {
        if (alive) {
          setEvidence(data);
          setEvidenceStatus("available");
        }
      })
      .catch(() => {
        if (alive) setEvidenceStatus("absent");
      });
    return () => {
      alive = false;
    };
  }, []);
  useEffect(() => {
    if (!job || !isActive(job.status)) return;
    let alive = true;
    const timer = window.setInterval(() => {
      void api<Job>(`/jobs/${encodeURIComponent(job.id)}`)
        .then((next) => {
          if (!alive) return;
          setJob(next);
          setOnline(true);
          setJobs((current) => [
            next,
            ...current.filter((item) => item.id !== next.id),
          ]);
        })
        .catch((caught) => {
          if (alive) {
            setOnline(false);
            setError(
              `Job polling failed. ${caught instanceof Error ? caught.message : "Reconnect to recover."}`,
            );
          }
        });
    }, 1000);
    return () => {
      alive = false;
      window.clearInterval(timer);
    };
  }, [job?.id, job?.status]);

  const configuredPreset = presets.find(
    (preset) => preset.id === form.presetId,
  );
  const preset = job?.problem ?? configuredPreset;
  const result = job?.result;
  const snapshot = result?.[snapshotName];
  const cell =
    snapshot?.cells.find((item) => item.path === selectedCell) ??
    snapshot?.cells[0];
  const step = cell?.trajectory.find((item) => item.time === time);
  const active = Boolean(job && isActive(job.status));
  const horizon = job?.requested.horizon ?? Number(form.horizon);

  function update(key: keyof FormValues, value: string) {
    setForm((current) => ({ ...current, [key]: value }));
    setErrors((current) => {
      const next = { ...current };
      delete next[key];
      return next;
    });
  }
  async function run(event: FormEvent) {
    event.preventDefault();
    const problems = validateForm(form);
    setErrors(problems);
    if (Object.keys(problems).length) return;
    setBusy(true);
    setError("");
    try {
      const next = await api<Job>("/jobs", {
        method: "POST",
        body: JSON.stringify(requestFrom(form)),
      });
      setJob(next);
      setTime(0);
      setSnapshotName("best");
      setSelectedCell("");
      setOnline(true);
      setJobs((current) => [
        next,
        ...current.filter((item) => item.id !== next.id),
      ]);
    } catch (caught) {
      setError(
        caught instanceof Error
          ? caught.message
          : "The job could not be submitted.",
      );
    } finally {
      setBusy(false);
    }
  }
  async function cancel() {
    if (!job) return;
    setBusy(true);
    try {
      setJob(
        await api<Job>(`/jobs/${encodeURIComponent(job.id)}`, {
          method: "DELETE",
        }),
      );
    } catch (caught) {
      setError(
        caught instanceof Error ? caught.message : "Cancellation failed.",
      );
    } finally {
      setBusy(false);
    }
  }
  async function openJob(id: string) {
    setError("");
    try {
      setJob(await api<Job>(`/jobs/${encodeURIComponent(id)}`));
      setTime(0);
      setSelectedCell("");
      setSnapshotName("best");
      setView("workbench");
    } catch (caught) {
      setError(
        caught instanceof Error
          ? caught.message
          : "The saved job is unavailable.",
      );
    }
  }
  function field(
    key:
      | "precision"
      | "horizon"
      | "tolerance"
      | "maxRefinements"
      | "maxCells"
      | "maxDepth",
    label: string,
    hint: string,
  ) {
    return (
      <label className="field" key={key}>
        <span>{label}</span>
        <input
          name={key}
          value={form[key]}
          inputMode={key === "tolerance" ? "decimal" : "numeric"}
          onChange={(event) => update(key, event.target.value)}
          aria-invalid={Boolean(errors[key])}
          aria-describedby={`${key}-hint`}
        />
        <small id={`${key}-hint`} className={errors[key] ? "field-error" : ""}>
          {errors[key] ?? hint}
        </small>
      </label>
    );
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <a
          className="brand"
          href="#main"
          aria-label="Fuzzy Dynamics Lab workbench"
        >
          <svg
            viewBox="0 0 34 32"
            width="34"
            height="32"
            fill="none"
            aria-hidden="true"
          >
            <path
              d="M3 26V6M3 26h28M7 25l7-17 5 17 7-11 5 11"
              stroke="currentColor"
              strokeWidth="1.5"
            />
            <circle cx="14" cy="8" r="2.8" fill="currentColor" />
          </svg>
          <span>
            Fuzzy Dynamics <strong>Lab</strong>
          </span>
        </a>
        <nav aria-label="Application">
          <button
            className={view === "workbench" ? "nav-item active" : "nav-item"}
            onClick={() => setView("workbench")}
          >
            Workbench
          </button>
          <button
            className={view === "evidence" ? "nav-item active" : "nav-item"}
            onClick={() => setView("evidence")}
          >
            Saved evidence
          </button>
        </nav>
        <div className="connection">
          <span className={online ? "status-dot connected" : "status-dot"} />
          {connecting
            ? "Connecting"
            : online
              ? "Local API connected"
              : "API offline"}
          <span className="local-tag">LOCAL</span>
        </div>
      </header>

      <div className="workspace">
        <aside
          className="experiment-rail"
          aria-label="Experiment configuration"
        >
          <div className="rail-heading">
            <h2>Experiment</h2>
            <span className="code-label">CAE</span>
          </div>
          <p className="rail-intro">
            Configure a finite horizon. <br />
            Inspect the complete error bound.
          </p>
          <form onSubmit={(event) => void run(event)} noValidate>
            <label className="field">
              <span>Dimension</span>
              <select
                value={configuredPreset?.dimension ?? 1}
                disabled={!presets.length}
                onChange={(event) => {
                  const next = presets.find(
                    (p) => p.dimension === Number(event.target.value),
                  );
                  if (next) update("presetId", next.id);
                }}
              >
                <option value="1">1D · unit interval</option>
                <option value="2">2D · unit square</option>
              </select>
            </label>
            <label className="field">
              <span>Verified model</span>
              <select
                value={form.presetId}
                disabled={!presets.length}
                onChange={(event) => update("presetId", event.target.value)}
              >
                {!presets.length && (
                  <option value={form.presetId}>Waiting for API</option>
                )}
                {presets
                  .filter((p) => p.dimension === configuredPreset?.dimension)
                  .map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.label}
                    </option>
                  ))}
              </select>
            </label>
            <label className="field">
              <span>Refinement strategy</span>
              <select
                value={form.strategy}
                onChange={(event) => update("strategy", event.target.value)}
              >
                {strategies.map(([value, label]) => (
                  <option
                    key={value}
                    value={value}
                    disabled={value === "RANKED" && !models.length}
                  >
                    {label}
                    {value === "RANKED" && !models.length
                      ? " · unavailable"
                      : ""}
                  </option>
                ))}
              </select>
            </label>
            {form.strategy === "RANKED" && (
              <label className="field">
                <span>Policy model</span>
                <select
                  value={form.modelId ?? ""}
                  onChange={(event) => update("modelId", event.target.value)}
                >
                  <option value="">Select model</option>
                  {models.map((model) => (
                    <option key={model.id} value={model.id}>
                      {model.id} · {model.featureVersion}
                    </option>
                  ))}
                </select>
                {errors.modelId && (
                  <small className="field-error">{errors.modelId}</small>
                )}
              </label>
            )}
            <div className="field-pair">
              {field("horizon", "Horizon T", "0–20 steps")}
              {field("precision", "Precision", "16–64 digits")}
            </div>
            {field(
              "tolerance",
              "Tolerance ε",
              "Endograph distance · 1e-12 ≤ ε < 1",
            )}
            <details className="budget-controls" open>
              <summary>Resource limits</summary>
              <div className="field-pair">
                {field("maxRefinements", "Refinements", "0–512 splits")}
                {field("maxCells", "Active cells", "1–513 leaves")}
              </div>
              {field("maxDepth", "Maximum depth", "0–24 bisections")}
            </details>
            <button
              className="run-button"
              type="submit"
              disabled={!online || active || busy}
            >
              {busy && !active ? "Submitting…" : "Run experiment"}
              <Arrow />
            </button>
            {active && (
              <button
                className="cancel-button"
                type="button"
                disabled={busy || job?.status === "CANCELLING"}
                onClick={() => void cancel()}
              >
                {job?.status === "CANCELLING"
                  ? "Cancellation requested"
                  : "Cancel run"}
                <Arrow stop />
              </button>
            )}
            <p className="form-footnote">
              Directed decimal intervals. <br />
              Full domain, every integer time.
            </p>
          </form>
          <div className="job-history">
            <h3>
              Session jobs <span>{jobs.length}</span>
            </h3>
            {jobs.length ? (
              <ol>
                {jobs.slice(0, 6).map((item) => (
                  <li key={item.id}>
                    <button
                      className={job?.id === item.id ? "selected" : ""}
                      onClick={() => void openJob(item.id)}
                    >
                      <span>
                        {item.problem?.label ?? item.requested.presetId}
                        <small>{readable(item.status)}</small>
                      </span>
                      <span className="mono">{item.id.slice(0, 6)}</span>
                    </button>
                  </li>
                ))}
              </ol>
            ) : (
              <p>Your submitted runs appear here.</p>
            )}
          </div>
          <div className="rail-footer">
            Research software · v0.1.0
            <br />
            Membership is not probability.
          </div>
        </aside>

        <main id="main" tabIndex={-1}>
          {error && (
            <div className="error-banner" role="alert">
              <div>
                <strong>
                  {online ? "Request needs attention" : "Local API unavailable"}
                </strong>
                <p>{error}</p>
              </div>
              <button onClick={() => void connect()} disabled={connecting}>
                {connecting ? "Connecting…" : "Reconnect"}
              </button>
            </div>
          )}
          {selection.notice && (
            <div className="progress-note" role="status">
              <p>{selection.notice}</p>
            </div>
          )}
          {view === "evidence" ? (
            <EvidenceView
              evidence={evidence}
              status={evidenceStatus}
              jobs={jobs}
              onOpen={(id) => void openJob(id)}
            />
          ) : (
            <>
              <div className="page-heading">
                <div>
                  <h1>Dynamics workbench</h1>
                  <p>
                    Finite fuzzy states. Explicit uncertainty. Inspectable
                    evidence.
                  </p>
                </div>
                <span className="method-mark">
                  Certified Adaptive
                  <br />
                  <strong>Extension</strong>
                </span>
              </div>
              <section
                className="run-strip"
                aria-label="Current experiment status"
                aria-live="polite"
              >
                <div>
                  <span className="meta-label">
                    {job ? "Current run" : "Ready to investigate"}
                  </span>
                  <strong>
                    {job ? readable(job.status) : "Configure your experiment"}
                  </strong>
                </div>
                <div>
                  <span className="meta-label">Model</span>
                  <span>{preset?.label ?? "Awaiting verified models"}</span>
                </div>
                <div>
                  <span className="meta-label">Horizon</span>
                  <span className="mono">
                    t = 0…{Number.isFinite(horizon) ? horizon : "—"}
                  </span>
                </div>
                <div>
                  <span className="meta-label">Compute time</span>
                  <span className="mono">
                    {seconds(result?.elapsedNanos ?? job?.elapsedNanos)}
                  </span>
                </div>
                {job && (
                  <span
                    className={`status-label ${result?.status === "CERTIFIED" ? "certified" : ""}`}
                  >
                    {result ? readable(result.status) : readable(job.status)}
                  </span>
                )}
              </section>
              {active && (
                <div className="progress-note" role="status">
                  <span className="working-line" />
                  <p>
                    {job?.status === "CANCELLING"
                      ? "Stopping at a safe partition boundary."
                      : "The numerical worker is computing a complete partition."}{" "}
                    The API publishes verified results when the job finishes;
                    partial numerical progress is unavailable.
                  </p>
                </div>
              )}
              {job?.status === "FAILED" && (
                <div className="error-banner" role="alert">
                  <p>
                    The numerical run failed. {job.reason} Adjust the settings
                    and submit a new run.
                  </p>
                </div>
              )}
              {result && (!result.best || !result.last) && (
                <div className="error-banner" role="alert">
                  <p>
                    No complete certificate is available. {result.status}:{" "}
                    {result.detail} Verification applies only to available
                    complete snapshots.
                  </p>
                </div>
              )}
              {job?.status === "CANCELLED" && (
                <div className="progress-note" role="status">
                  <p>
                    The job was cancelled. No numerical certificate was
                    published for this job. You can submit a new experiment.
                  </p>
                </div>
              )}

              <section className="panel profile-panel">
                <div className="panel-heading">
                  <div>
                    <h2>Initial state</h2>
                    <p>
                      {preset
                        ? `${preset.map.family.replaceAll("_", " ").toLowerCase()} · maximum aggregation of ${preset.profile.peaks.length} tent peaks`
                        : "Verified model profile"}
                    </p>
                  </div>
                  <span className="mono panel-note">t = 0</span>
                </div>
                {preset ? (
                  <div className="initial-layout">
                    <InitialProfile preset={preset} />
                    <div className="profile-spec">
                      <h3>Exact model definition</h3>
                      <dl>
                        <dt>Domain</dt>
                        <dd>
                          {preset.dimension === 1
                            ? "[0, 1]"
                            : "[0, 1] × [0, 1]"}
                        </dd>
                        {Object.entries(preset.map.parameters).map(
                          ([key, value]) => (
                            <div className="definition-row" key={key}>
                              <dt>{key}</dt>
                              <dd>{value}</dd>
                            </div>
                          ),
                        )}
                      </dl>
                      {preset.profile.peaks.map((peak, i) => (
                        <div className="peak-spec" key={i}>
                          <span>Peak {i + 1}</span>
                          <code>
                            c = ({peak.center.join(", ")})<br />w = {peak.width}{" "}
                            · h = {peak.height}
                          </code>
                        </div>
                      ))}
                      <p>
                        {preset.dimension === 1
                          ? "Sampled profile preview; exact parameters shown above."
                          : "Centers and individual support boundaries; overlapping grades combine by maximum."}
                      </p>
                    </div>
                  </div>
                ) : (
                  <div className="empty-inline">
                    <h3>Connect the numerical service</h3>
                    <p>
                      Available models and their exact initial profiles will
                      load from the local API.
                    </p>
                    <button
                      onClick={() => void connect()}
                      disabled={connecting}
                    >
                      Reconnect API
                    </button>
                  </div>
                )}
              </section>

              <section className="panel trajectory-panel">
                <div className="panel-heading">
                  <div>
                    <h2>Transported finite state</h2>
                    <p>
                      Discrete representatives and directed reachable enclosures
                    </p>
                  </div>
                  {result && (
                    <div className="segmented" aria-label="Partition snapshot">
                      <button
                        className={snapshotName === "best" ? "chosen" : ""}
                        onClick={() => setSnapshotName("best")}
                      >
                        Best
                      </button>
                      <button
                        className={snapshotName === "last" ? "chosen" : ""}
                        onClick={() => setSnapshotName("last")}
                      >
                        Last
                      </button>
                    </div>
                  )}
                </div>
                {snapshot && preset ? (
                  <>
                    <div className="plot-toolbar">
                      <label className="checkbox-label">
                        <input
                          type="checkbox"
                          checked={enclosures}
                          onChange={(event) =>
                            setEnclosures(event.target.checked)
                          }
                        />{" "}
                        Show interval enclosures
                      </label>
                      <span className="mono">
                        {snapshot.cellCount} cells · refinement{" "}
                        {snapshot.refinement}
                      </span>
                    </div>
                    <StatePlot
                      snapshot={snapshot}
                      time={time}
                      dimension={preset.dimension}
                      enclosures={enclosures}
                    />
                    <div className="time-control">
                      <label htmlFor="time">
                        Time <strong className="mono">{time}</strong>
                      </label>
                      <input
                        id="time"
                        type="range"
                        min="0"
                        max={horizon}
                        step="1"
                        value={time}
                        onChange={(event) =>
                          setTime(Number(event.target.value))
                        }
                      />
                      <span className="mono">{horizon}</span>
                      <span className="time-bound">
                        B<sub>{time}</sub> ={" "}
                        <strong className="mono">
                          {short(snapshot.bounds[time])}
                        </strong>
                      </span>
                    </div>
                    <div className="legend">
                      <span>
                        <i className="legend-atom" />
                        Finite representative
                      </span>
                      <span>
                        <i className="legend-interval" />
                        Reachable box
                      </span>
                      {preset.dimension === 2 && (
                        <span>Point opacity follows membership grade</span>
                      )}
                    </div>
                    <p className="plot-footnote">
                      No interpolation between atoms. The reachable boxes
                      enclose trajectories from complete initial cells; they are
                      not a continuous membership reconstruction.
                    </p>
                  </>
                ) : (
                  <div className="empty-state">
                    <svg
                      width="100"
                      height="57"
                      viewBox="0 0 100 57"
                      aria-hidden="true"
                    >
                      <path
                        d="M2 3v50h95M19 53V34m22 19V15m21 38V25m21 28V40"
                        fill="none"
                        stroke="currentColor"
                        strokeWidth="1"
                      />
                      <circle cx="19" cy="34" r="3" fill="currentColor" />
                      <circle cx="41" cy="15" r="3" fill="currentColor" />
                      <circle cx="62" cy="25" r="3" fill="currentColor" />
                      <circle cx="83" cy="40" r="3" fill="currentColor" />
                    </svg>
                    <h3>
                      {active
                        ? "Awaiting the complete partition"
                        : "A finite state, with its uncertainty"}
                    </h3>
                    <p>
                      Run an experiment to inspect representatives across time.
                      The chart will show the returned numerical result and its
                      interval enclosures.
                    </p>
                  </div>
                )}
              </section>

              {result && result.best && result.last && (
                <>
                  <section className="panel bounds-panel">
                    <div className="panel-heading">
                      <div>
                        <h2>Error certificate</h2>
                        <p>
                          Whole initial domain · all integer times 0…{horizon}
                        </p>
                      </div>
                      <div className="segmented" aria-label="Bound plot mode">
                        <button
                          className={curveMode === "refinement" ? "chosen" : ""}
                          onClick={() => setCurveMode("refinement")}
                        >
                          Refinement
                        </button>
                        <button
                          className={curveMode === "horizon" ? "chosen" : ""}
                          onClick={() => setCurveMode("horizon")}
                        >
                          Horizon
                        </button>
                      </div>
                    </div>
                    <div className="certificate-summary">
                      <div>
                        <span className="meta-label">
                          Retained best upper bound
                        </span>
                        <strong className="certificate-number mono">
                          {short(result.worstBound, 6)}
                        </strong>
                      </div>
                      <div>
                        <span className="meta-label">Requested tolerance</span>
                        <strong className="mono">
                          {job?.requested.tolerance}
                        </strong>
                      </div>
                      <div>
                        <span className="meta-label">Canonical replay</span>
                        <strong>
                          {result.verified ? "Verified" : "Not verified"}
                        </strong>
                        <small>{seconds(result.verificationNanos)}</small>
                      </div>
                    </div>
                    <BoundPlot
                      result={result}
                      horizon={horizon}
                      tolerance={job!.requested.tolerance}
                      mode={curveMode}
                    />
                    <div className="legend">
                      <span>
                        <i className="legend-bound" />
                        Retained best certificate
                      </span>
                      {curveMode === "refinement" && (
                        <span>
                          <i className="legend-current" />
                          Current partition
                        </span>
                      )}
                      <span>
                        <i className="legend-tolerance" />
                        Tolerance ε
                      </span>
                    </div>
                    <p className="plot-footnote">
                      {result.detail} A split can increase the actual error.
                      Best and last partitions are retained separately. This
                      plot shows upper bounds, not observed errors.
                    </p>
                  </section>
                  <section className="panel exact-panel">
                    <div className="panel-heading">
                      <div>
                        <h2>Exact numerical evidence</h2>
                        <p>API strings preserved without display rounding</p>
                      </div>
                      <button onClick={() => downloadJob(job!)}>
                        Export job JSON
                        <Arrow />
                      </button>
                    </div>
                    <details>
                      <summary>Certificate, method, and trust boundary</summary>
                      <dl className="exact-definitions">
                        <dt>Job identifier</dt>
                        <dd>{job?.id}</dd>
                        <dt>Stopping condition</dt>
                        <dd>
                          {result.status} / {result.reason}
                        </dd>
                        <dt>Strategy</dt>
                        <dd>{job?.requested.strategy}</dd>
                        <dt>Decimal precision</dt>
                        <dd>{job?.requested.precision} significant digits</dd>
                        <dt>Best horizon bound</dt>
                        <dd>{result.best.worstBound}</dd>
                        <dt>Last horizon bound</dt>
                        <dd>{result.last.worstBound}</dd>
                        <dt>Per-time best bounds</dt>
                        <dd>
                          {result.best.bounds.map((bound, i) => (
                            <div key={i}>
                              t={i}: {bound}
                            </div>
                          ))}
                        </dd>
                      </dl>
                      <p>
                        The certificate is an endograph distance upper bound
                        under the supported map, membership, directed-rounding,
                        and complete-partition assumptions. It does not certify
                        individual positive α-cuts, infinite-horizon behavior,
                        comparative speed, novelty, or independent mathematical
                        review. Browser geometry is approximate; it is never an
                        input to certification.
                      </p>
                    </details>
                    <details>
                      <summary>Inspect a partition cell</summary>
                      <label className="cell-selector">
                        Cell path
                        <select
                          value={cell?.path ?? ""}
                          onChange={(event) =>
                            setSelectedCell(event.target.value)
                          }
                        >
                          {snapshot?.cells.map((item) => (
                            <option key={item.path} value={item.path}>
                              {item.path || "root"}
                            </option>
                          ))}
                        </select>
                      </label>
                      {cell && step && (
                        <dl className="exact-definitions">
                          <dt>Initial box</dt>
                          <dd>
                            {cell.initialBox
                              .map((a) => `[${a.lower}, ${a.upper}]`)
                              .join(" × ")}
                          </dd>
                          <dt>Stored grade</dt>
                          <dd>{cell.membership.grade}</dd>
                          <dt>Grade error σ</dt>
                          <dd>{cell.membership.gradeError}</dd>
                          <dt>Whole-cell variation κ</dt>
                          <dd>{cell.membership.variation}</dd>
                          <dt>Upper membership M</dt>
                          <dd>{cell.membership.upperMembership}</dd>
                          <dt>Representative at t={time}</dt>
                          <dd>({step.representative.join(", ")})</dd>
                          <dt>Reachable box</dt>
                          <dd>
                            {step.reachable
                              .map((a) => `[${a.lower}, ${a.upper}]`)
                              .join(" × ")}
                          </dd>
                          <dt>Enclosure radius ρ</dt>
                          <dd>{step.radius}</dd>
                          <dt>Evaluation residual η</dt>
                          <dd>{step.evaluationResidual}</dd>
                          <dt>Incoming Lipschitz bound</dt>
                          <dd>{step.transitionLipschitz}</dd>
                          <dt>Cell bound at t={time}</dt>
                          <dd>{cell.bounds[time]}</dd>
                        </dl>
                      )}
                    </details>
                  </section>
                </>
              )}
              <footer className="workbench-footer">
                <span>CAE · finite-horizon numerical workbench</span>
                <span>All coordinates and grades are dimensionless.</span>
              </footer>
            </>
          )}
        </main>
      </div>
    </div>
  );
}

function downloadJob(job: Job) {
  const url = URL.createObjectURL(
    new Blob([JSON.stringify(job, null, 2)], { type: "application/json" }),
  );
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = `fuzzy-job-${job.id}.json`;
  anchor.click();
  URL.revokeObjectURL(url);
}
function EvidenceView({
  evidence,
  status,
  jobs,
  onOpen,
}: {
  evidence: Evidence | null;
  status: string;
  jobs: Job[];
  onOpen: (id: string) => void;
}) {
  const rows = evidence?.comparisons ?? [];
  const keys = Array.from(new Set(rows.flatMap((row) => Object.keys(row))));
  return (
    <>
      <div className="page-heading">
        <div>
          <h1>Saved evidence</h1>
          <p>Separate numerical certificates from comparative observations.</p>
        </div>
        <span className="method-mark">
          Local research
          <br />
          <strong>record</strong>
        </span>
      </div>
      <section className="panel evidence-intro">
        <h2>What the evidence establishes</h2>
        <div className="evidence-columns">
          <div>
            <h3>Certificate</h3>
            <p>
              An upper bound over the full initial domain and every declared
              integer time, under the verified model assumptions.
            </p>
          </div>
          <div>
            <h3>Observed reference error</h3>
            <p>
              A separately computed discrepancy or bracket for an eligible
              reference. Missing or unavailable values remain missing.
            </p>
          </div>
          <div>
            <h3>Comparison</h3>
            <p>
              Paired cohorts, resource exits, timing scope, and the declared
              protocol matter. Local measurements do not establish a universal
              winner.
            </p>
          </div>
        </div>
      </section>
      <section className="panel saved-results">
        <div className="panel-heading">
          <div>
            <h2>{evidence?.title ?? "Saved comparison bundle"}</h2>
            <p>
              {evidence?.scope ??
                "Final saved experiment evidence, when available"}
            </p>
          </div>
          {evidence && (
            <a className="text-link" href="/results.json" download>
              Download summary JSON
            </a>
          )}
        </div>
        {status === "loading" ? (
          <p className="empty-inline">Loading saved evidence…</p>
        ) : !evidence ? (
          <div className="empty-inline">
            <h3>No saved summary is installed</h3>
            <p>
              The live API remains available in the workbench. A saved
              comparison summary can be supplied as{" "}
              <code>web/public/results.json</code>; no demonstration results are
              substituted.
            </p>
          </div>
        ) : (
          <>
            <div className="evidence-provenance">
              {evidence.generatedAtUtc && (
                <p>
                  Generated <code>{evidence.generatedAtUtc}</code>
                </p>
              )}
              {evidence.sourceSha256 && (
                <p>
                  Source fingerprint <code>{evidence.sourceSha256}</code>
                </p>
              )}
              {evidence.summary && <p>{evidence.summary}</p>}
            </div>
            {rows.length ? (
              <div className="table-scroll">
                <table>
                  <thead>
                    <tr>
                      {keys.map((key) => (
                        <th key={key}>{key.replaceAll("_", " ")}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {rows.map((row, i) => (
                      <tr key={i}>
                        {keys.map((key) => (
                          <td key={key}>
                            {row[key] === null || row[key] === undefined ? (
                              <span className="unavailable">Unavailable</span>
                            ) : (
                              String(row[key])
                            )}
                          </td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <p className="empty-inline">
                This bundle contains no comparison rows. Inspect the complete
                saved JSON for its declared evidence.
              </p>
            )}
            <details className="raw-evidence">
              <summary>Inspect complete saved summary</summary>
              <pre>{JSON.stringify(evidence, null, 2)}</pre>
            </details>
            {evidence.artifacts?.length ? (
              <div className="artifact-links">
                {evidence.artifacts
                  .filter((item) => /^(?:\/[^/]|\.\/)/.test(item.url))
                  .map((item) => (
                    <a key={item.url} href={item.url}>
                      {item.label}
                    </a>
                  ))}
              </div>
            ) : null}
          </>
        )}
      </section>
      <section className="panel session-results">
        <div className="panel-heading">
          <div>
            <h2>API job record</h2>
            <p>In-memory local session · retained by the API</p>
          </div>
        </div>
        {jobs.length ? (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Model</th>
                  <th>Strategy</th>
                  <th>Job state</th>
                  <th>Numerical outcome</th>
                  <th>Best upper bound</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {jobs.map((job) => (
                  <tr key={job.id}>
                    <td>{job.problem?.label ?? job.requested.presetId}</td>
                    <td>{readable(job.requested.strategy)}</td>
                    <td>{readable(job.status)}</td>
                    <td>
                      {job.result ? readable(job.result.status) : "Unavailable"}
                    </td>
                    <td className="mono">{job.result?.worstBound ?? "—"}</td>
                    <td>
                      <button
                        className="text-button"
                        onClick={() => onOpen(job.id)}
                      >
                        Inspect
                        <Arrow />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="empty-inline">
            <p>No API jobs have been submitted in this session.</p>
          </div>
        )}
      </section>
    </>
  );
}
