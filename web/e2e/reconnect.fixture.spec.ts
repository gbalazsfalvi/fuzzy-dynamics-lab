import { expect, test } from "@playwright/test";
import type { Page } from "@playwright/test";
import type { Job, Preset } from "../src/data";

// Lifecycle-only fixtures: no certificate, trajectory, or measured result is fabricated.
const preset: Preset = {
  id: "affine",
  label: "Affine contraction",
  dimension: 1,
  map: { family: "affine", parameters: { factor: "1/2" } },
  profile: {
    aggregation: "MAXIMUM",
    peaks: [{ center: ["1/2"], width: "1/3", height: "1" }],
  },
};

async function lifecycleFixture(page: Page, status: string) {
  const state = {
    submitted: false,
    restarted: false,
    retained: false,
    reconnectFails: false,
  };
  const job: Job = {
    id: "00000000-0000-4000-8000-000000000001",
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
    problem: { ...preset, horizon: 6 },
    result: null,
    elapsedNanos: "0",
  };
  await page.route("**/api/**", async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (path === "/api/presets") return route.fulfill({ json: [preset] });
    if (path === "/api/models") return route.fulfill({ json: [] });
    if (path === "/api/jobs" && request.method() === "POST") {
      state.submitted = true;
      return route.fulfill({ status: 202, json: job });
    }
    if (path === "/api/jobs") {
      if (state.restarted && state.reconnectFails)
        return route.fulfill({
          status: 503,
          json: { message: "Lifecycle fixture: API unavailable" },
        });
      const records = state.restarted
        ? state.retained
          ? [{ ...job, status: "CANCELLED", reason: "CANCELLED_BY_USER" }]
          : []
        : state.submitted
          ? [job]
          : [];
      return route.fulfill({ json: records });
    }
    if (path === `/api/jobs/${job.id}`) {
      return state.restarted
        ? route.fulfill({
            status: 404,
            json: {
              code: "JOB_NOT_FOUND",
              message: "Lifecycle fixture: record unavailable",
            },
          })
        : route.fulfill({ json: job });
    }
    return route.fulfill({
      status: 404,
      json: { message: "Unexpected lifecycle fixture route" },
    });
  });
  await page.goto("/");
  await expect(page.getByText("Local API connected")).toBeVisible();
  await page
    .getByRole("button", { name: "Run experiment", exact: true })
    .click();
  await expect(
    page.getByRole("button", { name: "Run experiment", exact: true }),
  ).toBeDisabled();
  await expect(page.locator(".cancel-button")).toBeVisible();
  return state;
}

for (const status of ["QUEUED", "RUNNING", "CANCELLING"]) {
  test(`lifecycle fixture: reconnect clears an expired ${status} job without reloading`, async ({
    page,
  }) => {
    const state = await lifecycleFixture(page, status);
    state.restarted = true;
    await expect(page.getByText(/Job polling failed/)).toBeVisible();
    await page.getByRole("button", { name: "Reconnect", exact: true }).click();
    await expect(
      page.getByRole("button", { name: "Run experiment", exact: true }),
    ).toBeEnabled();
    await expect(
      page.getByText(/selected job expired or the API session restarted/i),
    ).toBeVisible();
    await expect(page.locator(".cancel-button")).toHaveCount(0);
    await expect(page.getByText("Local API connected")).toBeVisible();
  });
}

test("lifecycle fixture: reconnect replaces a retained job with its current terminal record", async ({
  page,
}) => {
  const state = await lifecycleFixture(page, "RUNNING");
  state.restarted = true;
  state.retained = true;
  await expect(page.getByText(/Job polling failed/)).toBeVisible();
  await page.getByRole("button", { name: "Reconnect", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "Run experiment", exact: true }),
  ).toBeEnabled();
  await expect(page.getByText(/The job was cancelled\./)).toBeVisible();
  await expect(
    page.getByText(/selected job expired or the API session restarted/i),
  ).toHaveCount(0);
});

test("lifecycle fixture: a failed reconnect preserves the selected job", async ({
  page,
}) => {
  const state = await lifecycleFixture(page, "RUNNING");
  state.restarted = true;
  state.reconnectFails = true;
  await expect(page.getByText(/Job polling failed/)).toBeVisible();
  await page.getByRole("button", { name: "Reconnect", exact: true }).click();
  await expect(page.getByText(/Cannot reach the local API/)).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Run experiment", exact: true }),
  ).toBeDisabled();
  await expect(page.locator(".cancel-button")).toBeVisible();
  await expect(
    page.getByText(/selected job expired or the API session restarted/i),
  ).toHaveCount(0);
});
