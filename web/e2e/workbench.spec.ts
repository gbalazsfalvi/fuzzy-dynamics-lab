import { expect, test } from "@playwright/test";
import type { Page } from "@playwright/test";
import { mkdir } from "node:fs/promises";

/** Check physical label size and clipping after a responsive chart resize. */
async function expectReadableAxes(page: Page) {
  await expect
    .poll(async () =>
      page
        .locator(".scientific-chart .plot-axes text")
        .evaluateAll((elements) =>
          Math.min(
            ...elements.map((element) => {
              const text = element as SVGGraphicsElement;
              const transform = text.getScreenCTM();
              return transform
                ? parseFloat(getComputedStyle(text).fontSize) *
                    Math.hypot(transform.a, transform.b)
                : 0;
            }),
          ),
        ),
    )
    .toBeGreaterThanOrEqual(11);
  const clipped = await page
    .locator(".scientific-chart .plot-axes text")
    .evaluateAll((elements) =>
      elements.some((element) => {
        const text = element as SVGGraphicsElement;
        const bounds = text.getBoundingClientRect();
        const chart = text.ownerSVGElement!.getBoundingClientRect();
        return (
          bounds.left < chart.left - 1 ||
          bounds.right > chart.right + 1 ||
          bounds.top < chart.top - 1 ||
          bounds.bottom > chart.bottom + 1
        );
      }),
    );
  expect(clipped).toBe(false);
  const overlapping = await page
    .locator(".scientific-chart .plot-axes text")
    .evaluateAll((elements) =>
      elements.some((left, index) =>
        elements.slice(index + 1).some((right) => {
          if (
            (left as SVGGraphicsElement).ownerSVGElement !==
            (right as SVGGraphicsElement).ownerSVGElement
          )
            return false;
          const a = left.getBoundingClientRect();
          const b = right.getBoundingClientRect();
          return (
            Math.min(a.right, b.right) - Math.max(a.left, b.left) > 1 &&
            Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top) > 1
          );
        }),
      ),
    );
  expect(overlapping).toBe(false);
}

test("real API: validation, scalar certificate, time cursor, 2D boxes, and saved evidence", async ({
  page,
}) => {
  await mkdir("artifacts/screenshots", { recursive: true });
  const failures: string[] = [];
  page.on("pageerror", (error) => failures.push(error.message));
  await page.goto("/");
  await expect(page.getByText("Local API connected")).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "Initial state", exact: true }),
  ).toBeVisible();
  await page.getByLabel("Tolerance ε").fill("0");
  await page.getByRole("button", { name: "Run experiment" }).click();
  await expect(
    page.getByText("Enter a decimal from 1e-12 up to, but excluding, 1."),
  ).toBeVisible();
  await page.getByLabel("Tolerance ε").fill("0.1");
  const response = page.waitForResponse(
    (response) =>
      response.url().endsWith("/api/jobs") &&
      response.request().method() === "POST",
  );
  await page.getByRole("button", { name: "Run experiment" }).click();
  expect((await response).status()).toBe(202);
  await expect(
    page.getByRole("heading", { name: "Error certificate", exact: true }),
  ).toBeVisible({ timeout: 60000 });
  await expect(page.getByText("Canonical replay")).toBeVisible();
  await page.locator("#time").fill("3");
  await expect(
    page.getByRole("img", { name: /Finite representatives at time 3/ }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Last", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "Last", exact: true }),
  ).toHaveClass("chosen");
  await page.getByRole("button", { name: "Best", exact: true }).click();
  await page.evaluate(() => document.fonts.ready);
  await page.screenshot({
    path: "artifacts/screenshots/desktop-1d-certificate.png",
    fullPage: true,
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await expectReadableAxes(page);
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  await page.screenshot({
    path: "artifacts/screenshots/mobile-1d-certificate.png",
    fullPage: true,
  });
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page
    .getByRole("combobox", { name: "Dimension", exact: true })
    .selectOption("2");
  await page.getByLabel("Horizon T").fill("3");
  await page.getByLabel("Refinements", { exact: false }).fill("64");
  await page.getByLabel("Active cells").fill("65");
  await page.getByRole("button", { name: "Run experiment" }).click();
  await expect(
    page.getByRole("heading", { name: "Error certificate", exact: true }),
  ).toBeVisible({ timeout: 60000 });
  await expect(
    page.locator(".run-strip").getByText("Coupled tent map", { exact: true }),
  ).toBeVisible();
  await page.locator("#time").fill("2");
  await expect(
    page.locator(".state-chart .reachable-box").first(),
  ).toBeVisible();
  await page.screenshot({
    path: "artifacts/screenshots/desktop-2d-boxes.png",
    fullPage: true,
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await expectReadableAxes(page);
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  await page.screenshot({
    path: "artifacts/screenshots/mobile-2d-boxes.png",
    fullPage: true,
  });
  await page
    .getByRole("button", { name: "Saved evidence", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "Saved evidence", exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "API job record", exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("heading", {
      name: "Frozen held-out comparison",
      exact: true,
    }),
  ).toBeVisible();
  await expect(
    page
      .locator(".evidence-provenance")
      .getByText(/Primary ridge minus maximum-bound certification difference/),
  ).toBeVisible();
  await page.screenshot({
    path: "artifacts/screenshots/mobile-comparison.png",
    fullPage: true,
  });
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.screenshot({
    path: "artifacts/screenshots/desktop-comparison.png",
    fullPage: true,
  });
  expect(failures).toEqual([]);
});

test("loopback API preserves rejection of foreign-origin writes", async ({
  request,
}) => {
  const response = await request.post("/api/jobs", {
    headers: {
      Origin: "https://foreign.example",
      "Sec-Fetch-Site": "cross-site",
    },
    data: {
      presetId: "affine",
      strategy: "MAXIMUM_BOUND",
      precision: 34,
      horizon: 1,
      tolerance: "0.1",
      maxRefinements: 1,
      maxCells: 2,
      maxDepth: 4,
    },
  });
  expect(response.status()).toBe(403);
  expect((await response.json()).code).toBe("LOCAL_ORIGIN_REQUIRED");
});
