export type Exact = string;
export type Interval = { lower: Exact; upper: Exact };
export type Peak = { center: Exact[]; width: Exact; height: Exact };
export type Preset = {
  id: string;
  label: string;
  dimension: number;
  map: { family: string; parameters: Record<string, Exact> };
  profile: { aggregation: string; peaks: Peak[] };
};
export type Model = { id: string; featureVersion: string };
export type JobRequest = {
  presetId: string;
  strategy: string;
  precision: number;
  horizon: number;
  tolerance: string;
  maxRefinements: number;
  maxCells: number;
  maxDepth: number;
  modelId?: string;
};
export type Cell = {
  path: string;
  initialBox: Interval[];
  membership: {
    grade: Exact;
    gradeError: Exact;
    variation: Exact;
    upperMembership: Exact;
  };
  trajectory: {
    time: number;
    representative: Exact[];
    reachable: Interval[];
    radius: Exact;
    directRadius: Exact;
    propagatedRadius: Exact;
    transitionLipschitz: Exact;
    evaluationResidual: Exact;
  }[];
  bounds: Exact[];
};
export type Snapshot = {
  refinement: number;
  cellCount: number;
  worstBound: Exact;
  bounds: Exact[];
  cells: Cell[];
};
export type Result = {
  status: string;
  reason: string;
  detail: string;
  verified: boolean;
  worstBound: Exact;
  bounds: Exact[];
  refinementCurves: {
    refinement: number;
    parent: string;
    selection: string;
    currentBound: Exact;
    bestBound: Exact;
    activeCells: number;
  }[];
  best: Snapshot | null;
  last: Snapshot | null;
  elapsedNanos: string;
  verificationNanos: string;
};
export type Job = {
  id: string;
  status: string;
  reason: string | null;
  requested: JobRequest;
  problem: Preset & { horizon: number };
  result: Result | null;
  elapsedNanos: string;
};
export type FormValues = Omit<
  JobRequest,
  "precision" | "horizon" | "maxRefinements" | "maxCells" | "maxDepth"
> & {
  precision: string;
  horizon: string;
  maxRefinements: string;
  maxCells: string;
  maxDepth: string;
};

export const defaultForm: FormValues = {
  presetId: "affine",
  strategy: "MAXIMUM_BOUND",
  precision: "34",
  horizon: "6",
  tolerance: "0.1",
  maxRefinements: "128",
  maxCells: "129",
  maxDepth: "24",
};

/** Approximate only for SVG geometry and human-readable short labels. Never used to certify. */
export function numeric(value: string | number): number {
  if (typeof value === "number") return Number.isFinite(value) ? value : NaN;
  const parts = value.split("/");
  if (
    parts.length > 2 ||
    parts.some(
      (part) => !/^[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?$/.test(part),
    )
  )
    return NaN;
  const result = Number(parts[0]) / (parts.length === 2 ? Number(parts[1]) : 1);
  return Number.isFinite(result) ? result : NaN;
}
export function short(value: string | number | undefined, digits = 4): string {
  if (value === undefined) return "—";
  const parsed = numeric(value);
  return Number.isFinite(parsed)
    ? Number(parsed.toPrecision(digits)).toString()
    : String(value);
}
export function isActive(status: string): boolean {
  return ["QUEUED", "RUNNING", "CANCELLING"].includes(status);
}
/** Compare decimal admission limits as integers, avoiding boundary loss in binary floats. */
export function validTolerance(value: string): boolean {
  if (
    !/^(?:0?\.\d{1,64}|[1-9]\d{0,2}(?:\.\d{1,64})?)(?:[eE][+-]?\d{1,2})?$/.test(
      value,
    )
  )
    return false;
  const [mantissa, exponent = "0"] = value.toLowerCase().split("e");
  const [whole, fraction = ""] = mantissa.split(".");
  const numerator = BigInt((whole || "0") + fraction);
  const scale = fraction.length - Number(exponent);
  const n = scale < 0 ? numerator * 10n ** BigInt(-scale) : numerator;
  const denominator = scale > 0 ? 10n ** BigInt(scale) : 1n;
  return n * 1000000000000n >= denominator && n < denominator;
}
export function validateForm(form: FormValues): Record<string, string> {
  const errors: Record<string, string> = {};
  const limits = {
    precision: [16, 64],
    horizon: [0, 20],
    maxRefinements: [0, 512],
    maxCells: [1, 513],
    maxDepth: [0, 24],
  };
  for (const [key, [min, max]] of Object.entries(limits)) {
    const value = form[key as keyof FormValues] ?? "";
    if (!/^\d+$/.test(value) || Number(value) < min || Number(value) > max)
      errors[key] = `Enter an integer from ${min} to ${max}.`;
  }
  if (!validTolerance(form.tolerance))
    errors.tolerance = "Enter a decimal from 1e-12 up to, but excluding, 1.";
  if (!form.presetId) errors.presetId = "Choose a verified preset.";
  if (form.strategy === "RANKED" && !form.modelId)
    errors.modelId = "Choose an available policy model.";
  if (Number(form.maxCells) * (Number(form.horizon) + 1) > 4096)
    errors.maxCells = "Cells × (horizon + 1) must be at most 4096.";
  if (
    (2 * Number(form.maxRefinements) + 1) *
      (Number(form.horizon) + 1) *
      Number(form.precision) >
    800000
  )
    errors.maxRefinements =
      "Reduce refinements, horizon, or precision to fit the API work budget.";
  return errors;
}
export function requestFrom(form: FormValues): JobRequest {
  if (Object.keys(validateForm(form)).length)
    throw new Error("Correct the experiment settings before running.");
  const { modelId: _modelId, ...values } = form;
  return {
    ...values,
    precision: Number(form.precision),
    horizon: Number(form.horizon),
    maxRefinements: Number(form.maxRefinements),
    maxCells: Number(form.maxCells),
    maxDepth: Number(form.maxDepth),
    ...(form.strategy === "RANKED" ? { modelId: form.modelId } : {}),
  };
}

export async function api<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(`/api${path}`, {
    ...options,
    headers: { "Content-Type": "application/json", ...options?.headers },
  });
  const body = (await response.json().catch(() => null)) as unknown;
  if (!response.ok) {
    const message =
      body && typeof body === "object" && "message" in body
        ? String(body.message)
        : `API request failed (${response.status}).`;
    throw new Error(message);
  }
  return body as T;
}

/** The unit domain is fixed by the model contract, not inferred from visible atoms. */
export const UNIT_TICKS = [0, 0.25, 0.5, 0.75, 1];
/** Discrete time and refinement counts never receive fractional tick labels. */
export function integerTicks(maximum: number): number[] {
  const end = Math.max(0, Math.floor(maximum));
  return [
    ...new Set(
      Array.from({ length: 5 }, (_, index) => Math.round((index * end) / 4)),
    ),
  ];
}
export function unitPosition(
  value: Exact | number,
  start: number,
  length: number,
): number {
  const parsed = numeric(value);
  return Number.isFinite(parsed)
    ? start + Math.max(0, Math.min(1, parsed)) * length
    : start;
}
export function profileGrade(peaks: Peak[], coordinates: number[]): number {
  return Math.max(
    0,
    ...peaks.map(
      (peak) =>
        numeric(peak.height) *
        Math.max(
          0,
          1 -
            Math.max(
              ...coordinates.map((x, i) =>
                Math.abs(x - numeric(peak.center[i])),
              ),
            ) /
              numeric(peak.width),
        ),
    ),
  );
}
