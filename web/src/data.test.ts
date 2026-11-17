import { describe, expect, it } from "vitest";
import {
  defaultForm,
  integerTicks,
  isActive,
  numeric,
  profileGrade,
  requestFrom,
  unitPosition,
  validTolerance,
  validateForm,
} from "./data";

describe("exact input display adapter", () => {
  it("labels discrete iteration axes with unique integers including both endpoints", () => {
    for (const end of [0, 1, 3, 6, 13, 128]) {
      const ticks = integerTicks(end);
      expect(ticks[0]).toBe(0);
      expect(ticks.at(-1)).toBe(end);
      expect(ticks.every(Number.isInteger)).toBe(true);
      expect(new Set(ticks).size).toBe(ticks.length);
      expect(ticks).toEqual([...ticks].sort((a, b) => a - b));
    }
  });
  it("reads rational strings without changing the source value", () => {
    expect(numeric("3/4")).toBe(0.75);
    expect(numeric("1e-12")).toBe(1e-12);
    for (const invalid of [
      "1/0",
      "0/0",
      "Infinity",
      "",
      " /2",
      "1/2/3",
      "0xFF",
      "nan",
    ])
      expect(numeric(invalid)).toBeNaN();
  });
  it("keeps chart axes on the declared unit domain", () => {
    expect(unitPosition("0", 50, 600)).toBe(50);
    expect(unitPosition("1/2", 50, 600)).toBe(350);
    expect(unitPosition("1", 50, 600)).toBe(650);
    expect(unitPosition("-0.01", 50, 600)).toBe(50);
    expect(unitPosition("1.01", 50, 600)).toBe(650);
  });
  it("uses maximum for colliding initial memberships rather than adding them", () => {
    const peaks = [
      { center: ["1/2"], width: "1/4", height: "3/4" },
      { center: ["1/2"], width: "1/8", height: "1/2" },
    ];
    expect(profileGrade(peaks, [0.5])).toBe(0.75);
    expect(profileGrade(peaks, [0])).toBe(0);
  });
});
describe("API request validation", () => {
  it("preserves the entered decimal tolerance string", () => {
    const form = {
      ...defaultForm,
      tolerance: "0.10000000000000000000000000000001",
    };
    expect(validateForm(form)).toEqual({});
    expect(requestFrom(form).tolerance).toBe(form.tolerance);
    expect(requestFrom(form)).not.toHaveProperty("modelId");
  });
  it("rejects malformed, fractional and out-of-range resource limits", () => {
    expect(validateForm({ ...defaultForm, precision: "15" })).toHaveProperty(
      "precision",
    );
    expect(validateForm({ ...defaultForm, horizon: "1.5" })).toHaveProperty(
      "horizon",
    );
    expect(validateForm({ ...defaultForm, maxCells: "0" })).toHaveProperty(
      "maxCells",
    );
    expect(validateForm({ ...defaultForm, tolerance: "0" })).toHaveProperty(
      "tolerance",
    );
    expect(validateForm({ ...defaultForm, tolerance: "1" })).toHaveProperty(
      "tolerance",
    );
    expect(validateForm({ ...defaultForm, tolerance: "1e-13" })).toHaveProperty(
      "tolerance",
    );
    expect(
      validateForm({ ...defaultForm, tolerance: "1e-100" }),
    ).toHaveProperty("tolerance");
    expect(() => requestFrom({ ...defaultForm, horizon: "-1" })).toThrow();
  });
  it("checks aggregate memory and precision-work budgets", () => {
    const errors = validateForm({
      ...defaultForm,
      maxCells: "513",
      horizon: "20",
      precision: "64",
      maxRefinements: "512",
    });
    expect(errors).toHaveProperty("maxCells");
    expect(errors).toHaveProperty("maxRefinements");
  });
  it("compares tolerance endpoints exactly at decimal precision", () => {
    expect(validTolerance("0.999999999999999999999999999999999999")).toBe(true);
    expect(validTolerance("0.00000000000099999999999999999999999")).toBe(false);
    expect(validTolerance("1e-12")).toBe(true);
    expect(validTolerance("0.1" + "0".repeat(64))).toBe(false);
  });
  it("requires model identity only for a learned ranking", () => {
    expect(validateForm({ ...defaultForm, strategy: "RANKED" })).toHaveProperty(
      "modelId",
    );
    expect(
      requestFrom({ ...defaultForm, strategy: "RANKED", modelId: "held-model" })
        .modelId,
    ).toBe("held-model");
    expect(
      requestFrom({ ...defaultForm, modelId: "stale-model" }),
    ).not.toHaveProperty("modelId");
  });
});
describe("job lifecycle", () => {
  it("polls every active state and no terminal state", () => {
    for (const state of ["QUEUED", "RUNNING", "CANCELLING"])
      expect(isActive(state)).toBe(true);
    for (const state of ["READY", "CANCELLED", "FAILED"])
      expect(isActive(state)).toBe(false);
  });
});
