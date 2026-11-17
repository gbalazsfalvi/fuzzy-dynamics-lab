# Fuzzy Dynamics Lab workbench

## Platform and stack

Local React and TypeScript web application with Vite. No publication or deployment is authorized.

## Users and purpose

The task brief specifies researchers inspecting finite-horizon fuzzy-state approximations. They select a verified model, run a bounded calculation, inspect the result, and compare saved evidence. The primary interaction is an API job, not an illustrative simulation.

## Constraints

Exact mathematical values remain decimal or rational strings at the API boundary. Browser numbers are used only for display geometry. Membership is not probability. Numerical representatives are discrete; plotting them does not recover a continuous membership function. A certificate, observed reference error, convergence claim, and novelty claim require different evidence. Best and last partitions remain distinct. Resource exits are visible. No fabricated results or universal performance claims.

## Evidence

The implementation follows docs/CAE_METHOD.md, docs/COMPARISON_METHOD.md, and docs/API_CONTRACT.md. Product details are taken from the parent task's explicit brief; no additional product assumptions are required.
