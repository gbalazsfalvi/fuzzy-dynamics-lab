# Security and supported use

Fuzzy Dynamics Lab 0.1.x is a local research application. Its numerical
certificates concern the declared mathematical model and arithmetic assumptions;
they do not certify application security, correctness of every implementation
path, or suitability for a safety-critical or production control system.

Run the API and workbench only on loopback on a trusted workstation. The packaged
API defaults to `127.0.0.1:4180`; the development frontend also binds to loopback.
Keep those defaults. Do not expose either service through a public bind address,
port forwarding, reverse proxy or tunnel. The local request guards and bounded
jobs do not provide a production authentication or authorization boundary.

The local OS account, Java/Python/Node runtimes and project files are trusted.
Use synthetic inputs and the included public models. Do not submit credentials,
personal records, private source code or confidential research data. A static
portfolio report is a separate publication artifact; publishing it or this
repository does not authorize hosting a remotely accessible solver.

## Reporting a concern

For a numerical discrepancy, use the repository issue tracker with a minimal
synthetic example, exact release revision, declared assumptions and expected
versus observed results. Distinguish a certificate violation from an upper-only
reference exceedance or an empirical-grid discrepancy.

For an application vulnerability, use GitHub's private vulnerability reporting
for this repository if enabled. If that option is unavailable, open an issue
requesting a private reporting channel without exploit details. Never include
credentials, private files, personal information, live server access details or
real sensitive payloads in a public issue or attachment. Remove local paths and
account identifiers from logs before sharing them.

There is no production support commitment or guaranteed response time. Fixes
target the current development version and are documented with their applicable
research and application scope. Dependency locks and checksums help reproduce
inputs; hashes alone do not authenticate experimental evidence.
