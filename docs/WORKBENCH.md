# Local scientific workbench

The `web` directory contains the milestone 06 React and TypeScript application. It consumes the real [local API contract](API_CONTRACT.md), uses self-hosted IBM Plex fonts, and has no cloud services or runtime demonstration data.

## Run the packaged workbench

Use a discoverable JDK 21, Node.js 24 and npm from PowerShell or CMD. From the project root, run:

```powershell
.\gradlew.bat :api:run
```

Open `http://127.0.0.1:4180`. This single command installs the locked frontend dependencies when needed, builds React, packages the generated static files into the API resources, and serves the workbench and `/api` from the same loopback origin. No separate Vite process is required. It does not start training or the held-out evaluation.

The application distribution ZIP also contains `api-0.1.0/bin/api.bat` for Windows (`bin/api` on Unix). The generated Windows launcher uses a quoted `%APP_HOME%\lib\*` classpath so a long installation path does not expand into CMD's command-length limit. Keep the distribution's `lib` directory with the launcher. Run from the repository root to use its frozen `learning/models`, or supply an explicit `--lab.models-directory` pointing to the intended exported models. The normal `:api:run` command already uses the repository root.

To run the real browser integration checks against that packaged application, keep the API running and use another terminal:

```powershell
cd web
npm.cmd exec playwright install chromium
$env:PLAYWRIGHT_BASE_URL = 'http://127.0.0.1:4180'
npm.cmd run test:e2e
```

Playwright's Chromium installation is needed only when the browser is not already available. The tests exercise real jobs rather than mocked numerical responses, and write desktop and mobile PNGs under `web/artifacts/screenshots`. Dependency versions and the npm lockfile are pinned.

## Optional frontend development server

For hot-reloading frontend development, keep `:api:run` running and start Vite in a second terminal:

```powershell
cd web
npm.cmd run dev
```

Open `http://127.0.0.1:5173`. Vite proxies `/api` to `127.0.0.1:4180`. Only the explicit development origin `http://127.0.0.1:5173` with absent or same-origin Fetch Metadata has its Origin rewritten to the backend origin. Foreign origins and cross-site metadata remain intact and are rejected by the backend. The development server binds to loopback. `vite preview` alone does not proxy API requests.

The following frontend checks run from `web`. Use `npm.cmd ci` first if working on the frontend without starting the packaged application:

```powershell
npm.cmd run build
npm.cmd run typecheck
npm.cmd run format:check
npm.cmd test
```

The frontend build outputs `web/dist`; Gradle includes it in the API's static resources automatically. Browser tests default to the optional Vite origin. To use that origin after setting the packaged test URL earlier, remove it with `Remove-Item Env:PLAYWRIGHT_BASE_URL -ErrorAction SilentlyContinue`, then run `npm.cmd run test:e2e` while both development servers are running.

## Working with results

Choose dimension, verified model, strategy, horizon, significant decimal precision, tolerance, and finite budgets. Form validation matches the API admission ranges and combined work limits. Decimal tolerance boundary checks use integer arithmetic; the entered string is sent unchanged. A learned ranking requires an allowlisted API model ID. Every deterministic strategy omits the model field.

Run submits a local job. Active jobs are polled once per second, and Cancel requests cooperative cancellation. The API provides lifecycle and elapsed time, not partial numerical certificates. Session jobs can be reopened, subject to the backend's bounded retention. Terminal resource exits remain visible; READY does not mean CERTIFIED.

After a polling failure, Reconnect refreshes the session job list and the selected job together. If its identifier is still retained, the latest returned record replaces the displayed one. If it is absent after an API restart or retention expiry, the selection is cleared with an explanation and Run becomes available again. A failed reconnect preserves the selection; it does not manufacture an expired state or a numerical result.

The time cursor selects an integer trajectory slice. Best and Last expose the retained incumbent and final complete partition separately. One-dimensional plots use discrete stems and points. Two-dimensional plots use points and reachable boxes. The interval layer can be hidden without changing the numerical result. Plot tooltips and the exact evidence inspector expose source strings; the complete job is exportable as JSON.

Initial one-dimensional membership curves are explicitly labeled sampled previews of the supplied exact peak parameters. Two-dimensional initial previews show individual support boundaries and peak centers. Neither is used for certification. No smoothing joins finite-state representatives into a membership function. Chart coordinates and short labels use binary floats solely for presentation, while the API mathematical values remain strings.

Bound charts show the retained best upper bound and, in refinement mode, the current partition's upper bound. All spatial and membership axes retain the declared `[0,1]` domain. The exact evidence section includes each time bound and cell quantities. A small endograph bound does not imply positive alpha-cut accuracy, infinite-horizon behavior, comparative speed, novelty, or independent mathematical review.

## Saved comparison summary

The optional `web/public/results.json` is read only. When absent, the evidence view explains that no saved summary is installed. It never substitutes fixture values. The supported summary shape is:

```json
{
  "title": "Saved experiment summary",
  "scope": "Declared protocol and cohort scope",
  "generatedAtUtc": "ISO timestamp",
  "sourceSha256": "source fingerprint",
  "verification": {},
  "summary": "Evidence-qualified interpretation",
  "comparisons": [{"method": "method identifier", "certificate": "exact string", "observed_reference": null}],
  "artifacts": [{"label": "Saved artifact", "url": "/evidence/report.json"}]
}
```

Table columns are derived from supplied row fields. Null or missing fields are displayed as unavailable. The complete summary remains inspectable as JSON. Artifact links accept local relative paths only. The view explains the difference between a certificate, an observed reference discrepancy, and comparative evidence.

## Visual system

The Operate surface uses white plotting paper, a pale neutral work area, deep petrol chrome, certified teal lines, and ochre interval/tolerance marks. IBM Plex Sans carries controls and prose; IBM Plex Mono carries measurements. Configuration is a desktop rail and a compact responsive form. The plot canvas, exact data, and evidence states remain keyboard accessible. Reduced motion disables the active-worker pulse. Product and design context are recorded in `web/PRODUCT.md`, `web/DESIGN.md`, and `web/.impeccable/design.json`.
