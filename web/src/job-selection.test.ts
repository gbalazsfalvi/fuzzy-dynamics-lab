import { describe, expect, it } from "vitest";
import { reconcileJobSelection } from "./job-selection";
import type { Job } from "./data";

// Selection tests use lifecycle records without numerical results.
function lifecycleJob(status: string, id = "retained-job"): Job {
  return {
    id,
    status,
    reason: null,
    requested: {
      presetId: "affine",
      strategy: "MAXIMUM_BOUND",
      precision: 34,
      horizon: 6,
      tolerance: "0.1",
      maxRefinements: 128,
      maxCells: 129,
      maxDepth: 24,
    },
    problem: {
      id: "affine",
      label: "Affine contraction",
      dimension: 1,
      horizon: 6,
      map: { family: "affine", parameters: { factor: "1/2" } },
      profile: {
        aggregation: "MAXIMUM",
        peaks: [{ center: ["1/2"], width: "1/3", height: "1" }],
      },
    },
    result: null,
    elapsedNanos: "0",
  };
}

describe("successful reconnect job selection", () => {
  it("clears every missing active state with a recovery explanation", () => {
    for (const status of ["QUEUED", "RUNNING", "CANCELLING"]) {
      const next = reconcileJobSelection(
        { job: lifecycleJob(status), notice: "" },
        [],
      );
      expect(next.job).toBeNull();
      expect(next.notice).toMatch(/expired or the API session restarted/);
    }
  });
  it("takes the complete returned record for the same identifier", () => {
    const terminal = lifecycleJob("CANCELLED");
    const next = reconcileJobSelection(
      { job: lifecycleJob("RUNNING"), notice: "old notice" },
      [terminal],
    );
    expect(next.job).toBe(terminal);
    expect(next.notice).toBe("");
  });
  it("does not select another session's job in place of a missing selection", () => {
    const next = reconcileJobSelection(
      { job: lifecycleJob("RUNNING"), notice: "" },
      [lifecycleJob("RUNNING", "other-job")],
    );
    expect(next.job).toBeNull();
  });
  it("keeps an empty selection instead of reopening history automatically", () => {
    expect(
      reconcileJobSelection({ job: null, notice: "old notice" }, [
        lifecycleJob("CANCELLED"),
      ]),
    ).toEqual({ job: null, notice: "" });
  });
});
