import type { Job } from "./data";

export type JobSelection = { job: Job | null; notice: string };

/** Reconcile only after a successful list request; absence means retention expired or a new session. */
export function reconcileJobSelection(
  current: JobSelection,
  records: Job[],
): JobSelection {
  if (!current.job) return { job: null, notice: "" };
  const retained = records.find((record) => record.id === current.job!.id);
  return retained
    ? { job: retained, notice: "" }
    : {
        job: null,
        notice:
          "The selected job expired or the API session restarted. You can run a new experiment.",
      };
}
