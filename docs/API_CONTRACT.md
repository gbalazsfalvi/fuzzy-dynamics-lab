# Local workbench API, version 1

The Java 21 server uses pinned Spring Boot 4.0.9 and runs with `./gradlew :api:run` from the project root. The default origin is `http://127.0.0.1:4180`. The [Spring Boot 4.0 requirements](https://docs.spring.io/spring-boot/4.0/system-requirements.html) support Java 21 and Gradle 9. The API is deliberately local and unauthenticated: startup rejects a bind address other than `127.0.0.1`. It rejects non-loopback clients, non-loopback Host names, browser cross-site writes, and mismatched Origin headers. Serve the frontend on the same origin. There is no wildcard CORS allowance. Local command-line clients may omit Origin.

Mathematical decimals and fractions are JSON strings. Integer resource counts, dimensions, time indices, and refinement indices are JSON integers. Binary floating-point model weights are used only for selection inside the allowlisted artifact format, never exported as certificate mathematics.

## Routes

* `GET /api/health` returns `{"status":"UP","service":"fuzzy-dynamics-lab","mode":"LOCAL_RESEARCH"}`.
* `GET /api/presets` returns four records with IDs `affine`, `tent`, `logistic`, and `coupled-tent`. Each has `id`, `label`, `dimension`, `map`, and `profile` fields. Example record:

```json
{"id":"affine","label":"Affine contraction","dimension":1,"map":{"family":"affine","parameters":{"factor":"1/2"}},"profile":{"aggregation":"MAXIMUM","peaks":[{"center":["1/2"],"width":"1/3","height":"1"}]}}
```

The scalar tent preset uses peaks `(center,width,height)=(1/4,1/5,1)` and `(3/4,1/6,3/5)`. Logistic uses parameter `4` and the same peak as affine. Coupled tent uses coupling `1/4` and a square-domain tent peak with center `["1/3","2/3"]`, width `"1/3"`, height `"1"`. Profiles combine peaks by maximum, and memberships are not probabilities.

* `GET /api/models` returns an array such as `[{"id":"ridge","featureVersion":"cae-cell-features-v1"}]`; the actual feature version is taken from each validated artifact. The array is empty when no valid models exist. Startup loads at most 32 files from configured `lab.models-directory` (default `learning/models`), rejects symlinks, files over 1 MiB, and invalid `DenseRankingModel` records. Restart after exporting models. Client values are IDs, never filenames, paths, or Java class names.
* `POST /api/jobs` with Content-Type `application/json` returns HTTP 202, a `Location: /api/jobs/{UUID}` header, and the current job record. Example request:

```json
{"presetId":"affine","strategy":"MAXIMUM_BOUND","precision":34,"horizon":6,"tolerance":"0.1","maxRefinements":128,"maxCells":129,"maxDepth":24}
```

Strategies are `MAXIMUM_BOUND`, `UNIFORM`, `NEXT_STEP_BOUND`, `PREDICTED_GAIN_PER_WORK`, and `RANKED`. Only `RANKED` requires `modelId`; other strategies must omit it or use null. `RANKED` uses a validated frozen dense ranking model and the same core certificate recipe and FIFO fairness period 5 as all policies.

* `GET /api/jobs/{UUID}` returns the job record below. `GET /api/jobs` returns retained full records, newest first. Records may expire when a newer job evicts the oldest terminal record.
* `DELETE /api/jobs/{UUID}` requests cooperative cancellation and returns the current record with HTTP 200. A queued job becomes `CANCELLED` immediately; a running job becomes `CANCELLING` until the worker acknowledges it. Deleting a terminal job is idempotent and leaves its record. A cancellation request does not delete historical evidence.

## Finite admission limits

The request body is limited to 4096 bytes. All fields in the request example are required; `modelId` is conditional as described above, and unknown fields are rejected. Precision is 16–64 significant decimal digits, horizon 0–20, maxRefinements 0–512, maxCells 1–513, and maxDepth 0–24. Tolerance must be a decimal **string**, between `"1e-12"` inclusive and `"1"` exclusive, with at most 64 fractional digits and a one or two digit exponent. Fractions are accepted in preset output but not as tolerance input. Resource settings must also satisfy `(2*maxRefinements+1)*(horizon+1)*precision <= 800000` and `maxCells*(horizon+1) <= 4096`.

There is one numerical worker, at most two queued jobs, and at most twenty retained job records. Overload returns HTTP 429 without retaining a rejected request. Oldest terminal jobs are evicted first. Shutdown requests cancellation for every queued and running job, interrupts the worker, and waits for its termination. Bounds may stop at finite budgets without reaching tolerance.

## Job and evidence shape

```json
{
  "id":"00000000-0000-0000-0000-000000000000",
  "status":"QUEUED",
  "reason":null,
  "requested":{"presetId":"affine","strategy":"MAXIMUM_BOUND","precision":34,"horizon":6,"tolerance":"0.1","maxRefinements":128,"maxCells":129,"maxDepth":24,"modelId":null},
  "problem":{"id":"affine","label":"Affine contraction","dimension":1,"horizon":6,"map":{"family":"affine","parameters":{"factor":"1/2"}},"profile":{"aggregation":"MAXIMUM","peaks":[{"center":["1/2"],"width":"1/3","height":"1"}]}},
  "result":null,
  "elapsedNanos":"0"
}
```

The lifecycle status is `QUEUED`, `RUNNING`, `CANCELLING`, `READY`, `CANCELLED`, or `FAILED`. Polling exposes lifecycle progress and elapsed time; there is no partial numerical certificate while a solver is running. `READY` means the outcome has been processed and both available snapshots passed canonical `CertificateVerifier` recomputation. It does **not** mean tolerance was reached. A READY result has:

```json
{"status":"BUDGET_EXHAUSTED","reason":"MAX_REFINEMENTS","detail":"The refinement budget was reached.","verified":true,"worstBound":"0.5","bounds":["0.5"],"refinementCurves":[],"best":{"refinement":0,"cellCount":1,"worstBound":"0.5","bounds":["0.5"],"cells":[]},"last":{"refinement":0,"cellCount":1,"worstBound":"0.5","bounds":["0.5"],"cells":[]},"elapsedNanos":"12345","verificationNanos":"12345"}
```

This last snippet documents field types, not an actual run: the cell arrays are abbreviated. `status` and `reason` are the core outcome and stopping reason: `CERTIFIED/TOLERANCE_REACHED`, `BUDGET_EXHAUSTED/MAX_REFINEMENTS|MAX_CELLS|MAX_DEPTH`, or `NUMERICAL_FAILURE/ARITHMETIC_FAILURE`. Cancellation and execution failures have `result:null` and never manufacture a certificate. Numerical failure during initialization can have absent best/last (`null`) and empty bounds; `verified:true` then describes processing of available snapshots only.

Each refinement curve entry is `{"refinement":1,"parent":"","selection":"MAXIMUM_BOUND","currentBound":"0.5","bestBound":"0.5","activeCells":2}`. `best` is the earliest retained smallest horizon bound and `last` the final complete partition. They can differ, because refinement can increase error. Both snapshot objects have `refinement`, `cellCount`, `worstBound`, `bounds` (time zero through horizon), and complete `cells` arrays. Each cell has this shape:

```json
{"path":"0","initialBox":[{"lower":"0","upper":"0.5"}],"membership":{"grade":"0.25","gradeError":"0","variation":"0.75","upperMembership":"1"},"bounds":["0.75"],"trajectory":[{"time":0,"representative":["0.25"],"reachable":[{"lower":"0","upper":"0.5"}],"radius":"0.25","directRadius":"0.25","propagatedRadius":"0.25","transitionLipschitz":"0","evaluationResidual":"0"}]}
```

The cell snippet also illustrates fields rather than claiming a certificate for the example request. `initialBox` remains in initial coordinates; `reachable` and `representative` are in transported coordinates. Membership grade is transported unchanged. Radius bounds infinity-norm spatial error; cell `bounds` use the grade-capped endograph metric. Shared boundaries are intentionally retained. The full zero baseline is implicit.

## Errors

Errors use `{ "code": "INVALID_REQUEST", "message": "..." }` without stack traces. HTTP 400 means invalid JSON, types, UUID, preset/model, or admission budget; HTTP 403 uses `LOCAL_ORIGIN_REQUIRED`; HTTP 404 uses `JOB_NOT_FOUND`; HTTP 429 uses `CAPACITY_REACHED` and `Retry-After: 2`. Unsupported media types/methods follow standard Spring HTTP 415/405 behavior. A job failing after admission becomes `FAILED` with `reason:"EXECUTION_FAILED"` and no result; exception internals and local paths are not exposed.

The API uses the shared canonical numerical verifier, not a formal proof checker. It does not make a novelty, speedup, or arbitrary-tolerance convergence claim.
