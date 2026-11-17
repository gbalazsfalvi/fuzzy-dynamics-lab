# Publication checklist for v0.1.0

Prepared on 17 November 2026. This is a release procedure, not evidence that
publication checks or remote CI have already passed. The authorized source
destination is the personal public repository
`https://github.com/gbalazsfalvi/fuzzy-dynamics-lab`. The portfolio is a separate
static publication on `https://balazsfalvi.hu/`.

## Public file selection

- [ ] Stage an explicit reviewed file list from the Fuzzy Dynamics Lab directory.
  Inspect `git status --short`, `git diff --cached --stat`, and
  `git diff --cached --name-only` before the first commit. Do not initialize or
  stage the parent workspace or unrelated repositories.
- [ ] Include authored Java source/tests, Gradle scripts/wrapper/catalogs/locks/
  verification metadata, API resources, frontend source/public assets/config/
  lockfile, Python source/tests/locked requirements, reviewed executable research
  protocols/manifests, and selected English method/milestone/research documents.
  Include `LICENSE`, `NOTICE`, `CITATION.cff`, `SECURITY.md`, `AGENTS.md`, root
  README, and `.github/workflows/ci.yml`.
- [ ] Include the six frozen model JSON files in `learning/models/` (one ridge,
  five MLP seeds) and their parity fixture. Record their hashes and match the
  frozen validation/evaluation record. Do not train or select a new model during
  publication preparation.
- [ ] Review each research/document file for local paths, private notes, source
  access limits and author rights. Include source-register metadata and links;
  exclude downloaded papers, extracted article text and local research caches.
- [ ] Exclude `docs/PUBLICATION_HANDOFF.md` from the public repository and all
  downloads: it is a local operations handoff containing machine paths and SSH
  connection metadata. Review `docs/LOCAL_DELIVERY_LEDGER.md` for the same issue
  before deciding whether a sanitized public version is useful.
- [ ] Exclude `.local/`, `.venv/` at every depth, `.gradle/`, build outputs,
  `node_modules/`, `.astro/`, IDE metadata, Python caches, browser profiles,
  test-results, temporary files, deployment scripts/archives/records, secrets,
  private SSH material and `research/sources/`. Check nested ignore rules;
  ignored files can still be force-added, so inspect the actual staged list.
- [ ] Do not stage `artifacts/` wholesale. Select any public evidence through a
  separately reviewed release bundle with a manifest and explicit contents.
  Retain synthetic-versus-experimental provenance and original evidence bytes.

## Licensing and citation

- [ ] Keep the exact Apache-2.0 text in `LICENSE`. It was retrieved from
  `https://www.apache.org/licenses/LICENSE-2.0.txt` on 17 November 2026; the
  retrieved bytes have SHA-256
  `cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30`.
- [ ] Preserve `Copyright 2026 Gergő Balázsfalvi` and the independently reproduced
  2021 Kupka/Škorupová algorithm attribution in `NOTICE`. Do not present the
  published PWL procedure as an original algorithm or a full PSO reproduction.
- [ ] Check third-party runtime/build/font licences and include applicable
  upstream notices in binary/web distributions. Project licensing does not
  relicense linked articles, downloaded publications or dependencies.
- [ ] Verify `CITATION.cff` version `0.1.0`, date `2026-11-17`, author, URL and
  artifact description against the actual release. Retain the distinction
  between a software citation and scientific novelty/peer review.

## Verification and claim status

- [ ] Run a clean locked JDK 21 build with Node 24, then save the exact commands,
  exit codes and revision. Root `clean build` includes API compilation/tests,
  workbench `npm ci`, workbench unit tests/build, formatting, Javadoc and the
  numerical coverage gate. Run workbench `format:check` separately.
- [ ] Install Python 3.13 dependencies with
  `python -m pip install --require-hashes --only-binary=:all: -r learning/requirements.lock`.
  Run `python -m unittest discover -s learning -p 'test_*.py' -v` and the matching
  `-s analysis` command. These use small synthetic fixtures; they must not read
  final outcomes to tune models or regenerate training/evaluation artifacts.
- [ ] Run `foundationDemo`, `caeDemo`, and then `verifyCaeDemo` in a separate
  invocation. Verify saved evidence without regenerating it. Record a final
  fresh replay if source fingerprints change; never edit a stored digest to
  conceal a mismatch.
- [ ] Confirm wrapper JAR validation, Gradle distribution checksum, strict
  dependency verification and locks. CI must not use `--write-locks`, regenerate
  dependency verification metadata, train models, or run the large comparison,
  learning, validation or held-out evaluation tasks by default.
- [ ] Run the authored workflow on the exact public release revision and record
  its URL/conclusion. YAML parsing and local runs do not establish remote CI
  success. The workflow performs checks only and has no deploy/upload step.
- [ ] Verify the loopback API, packaged workbench and actual browser flows
  locally, including English UI, keyboard/mobile behavior, failure states and
  correct numerical provenance. Browser CI is optional; default CI does not
  assert a browser run. Keep Spring Boot at the project's requested 4.0.9 line.
- [ ] Verify saved final evidence and fixed analysis using their documented
  commands. Publication does not authorize changing the frozen protocol or
  retraining after seeing outcomes. Preserve all methods/seeds/failures,
  unavailable comparisons, resource exits, paired cohorts and timing limits.
- [ ] Update release prose from verified outputs only. Distinguish a proved
  assumption-bound numerical result, certificate, qualified reference,
  empirical-grid observation and measured speed. Keep unresolved full-text
  comparisons and novelty questions explicit. Do not infer joint 2D geometry
  coverage from scalar references or imply that membership is probability.
- [ ] Scan the exact staged files and release archive for tokens, passwords,
  private-key blocks, personal records, machine/account paths, host access
  details and copied/private code. Review matches without printing secrets.
  Hash consistency alone does not establish safe contents or authenticity.

## Release and report assets

- [ ] Choose one final revision for tag `v0.1.0`, citation and release notes.
  Explain reproducible local commands, environment, artifact scope and limits.
  Source publication does not deploy the API or establish production readiness.
- [ ] Build the evidence/report bundle from an explicit allowlist. Include
  readable HTML/Markdown/JSON/CSV, original plot assets and selected compressed
  numerical payloads only where reviewed; retain relative links and provenance.
  Add SHA-256 checksums and verify them after download and extraction.
- [ ] Record artifact byte sizes and hashes, model/protocol/source fingerprints,
  verification dates, and exact release revision. Inspect compressed contents,
  reject unsafe archive paths/symlinks, and omit local logs/environments.
- [ ] Generate screenshots from the actual verified application/report with
  synthetic inputs. Record route/state/date and provide descriptive captions
  and alt text; do not fabricate numerical output or use unrelated imagery.
- [ ] Open the standalone report and all figures at desktop/mobile sizes.
  Render any PDF page by page and inspect clipping, legends, equations, tables,
  typography and links before distributing it. Confirm report values against
  saved analysis and keep certificates separate from reference errors.

## Portfolio publication

- [ ] Preserve all existing portfolio changes, professional anonymity, original
  project routes, ARRP facts, PQC release/evidence links and evidence checksum.
  Preserve the existing bilingual conceptual fuzzy article and math renderer.
- [ ] Add the Lab article/report/project using existing typed content contracts
  and matching English/Hungarian translations. Use actual evidence and fully
  translated captions/alt text; link the public repository/release and reviewed
  report download. Keep project source/reports in English.
- [ ] Verify portfolio formatting, Astro/TypeScript diagnostics, static build,
  internal links, language counterparts, math, report hashes and browser layout.
- [ ] Publish only reviewed static `dist/` bytes to a new versioned release.
  Recheck the live target/configuration, verify uploaded checksums, preserve
  immutable assets, switch atomically and retain rollback. Keep connection
  details and deployment records local; preserve unrelated server hosts.
- [ ] Verify real HTTPS EN/HU pages, source/release/download links, redirects,
  cache headers and report hash after publication. Record the observed result
  before claiming the source release or portfolio publication complete.
