"""Exact finite checks for the proposed certificate; these do not prove the continuous result."""
from fractions import Fraction as F
from pathlib import Path
import json
import random

RNG = random.Random(20261117)


def distance(left, right):
    """Use the spatial infinity norm required by the proposed product metric."""
    return max(abs(a - b) for a, b in zip(left, right))


def endograph_distance(states, left, right):
    """Evaluate Hausdorff distance between finite-universe endographs exactly."""
    def directed(source, target):
        # The distance along a source vertical segment is largest at its top.
        return max(
            min(
                max(distance(x, y), max(F(0), source[i] - target[j]))
                for j, y in enumerate(states)
            )
            for i, x in enumerate(states)
        )
    return max(directed(left, right), directed(right, left))


def run_case(dimension):
    if dimension == 1:
        states = [(F(i, 8),) for i in range(9)]
    else:
        states = [(F(i, 2), F(j, 2)) for i in range(3) for j in range(3)]
    size = len(states)
    mapping = [RNG.randrange(size) for _ in states]
    initial = [F(RNG.randrange(17), 16) for _ in states]
    order = list(range(size))
    RNG.shuffle(order)
    cells = []
    while order:
        take = RNG.randint(1, min(4, len(order)))
        indices, order = order[:take], order[take:]
        representative = RNG.choice(indices)
        grade = min(F(1), max(F(0), initial[representative] + F(RNG.randint(-2, 2), 16)))
        cells.append({
            "indices": indices,
            "representative": representative,
            "grade": grade,
            "sigma": abs(grade - initial[representative]),
            "kappa": max(abs(initial[i] - initial[representative]) for i in indices),
            "upper": max(initial[i] for i in indices),
            "approximate": representative,
        })
    lipschitz = max(
        distance(states[mapping[i]], states[mapping[j]]) / distance(x, y)
        for i, x in enumerate(states) for j, y in enumerate(states) if i != j
    )
    exact_locations = list(range(size))
    checks = 0
    grade_caps_helped = 0
    for time in range(6):
        exact = [F(0) for _ in states]
        approximate = [F(0) for _ in states]
        for i, location in enumerate(exact_locations):
            exact[location] = max(exact[location], initial[i])
        bounds = []
        raw_bounds = []
        previous_radii = []
        previous_centers = []
        for cell in cells:
            center = cell["approximate"]
            radius = max(distance(states[exact_locations[i]], states[center]) for i in cell["indices"])
            q = max(radius, cell["kappa"] + cell["sigma"])
            bounds.append(min(max(cell["upper"], cell["grade"]), q))
            raw_bounds.append(q)
            approximate[center] = max(approximate[center], cell["grade"])
            previous_radii.append(radius)
            previous_centers.append(center)
        actual = endograph_distance(states, exact, approximate)
        certificate = max(bounds)
        simple = min(F(1), max(raw_bounds))
        assert actual <= certificate <= simple
        grade_caps_helped += certificate < simple
        checks += 1
        next_locations = [mapping[i] for i in exact_locations]
        for index, cell in enumerate(cells):
            center = previous_centers[index]
            following = mapping[center]
            if RNG.random() < 0.25:
                following = RNG.randrange(size)
            residual = distance(states[mapping[center]], states[following])
            next_radius = max(distance(states[next_locations[i]], states[following]) for i in cell["indices"])
            assert next_radius <= lipschitz * previous_radii[index] + residual
            cell["approximate"] = following
        exact_locations = next_locations
    return checks, grade_caps_helped


checks = 0
grade_caps_helped = 0
for dimension in (1, 2):
    for _ in range(120):
        count, cap_count = run_case(dimension)
        checks += count
        grade_caps_helped += cap_count

states = [(F(0),), (F(1, 2),), (F(1),)]
actual_invalid = endograph_distance(states, [F(1)] * 3, [F(1), F(0), F(0)])
assert actual_invalid == F(1)  # An invented zero radius would wrongly claim zero.

result = {
    "date": "2026-11-17",
    "scope": "Finite-universe exact rational feasibility checks, not a continuous proof or application benchmark.",
    "seed": 20261117,
    "instances": 240,
    "time_slices_checked": checks,
    "dimensions": [1, 2],
    "assertions": [
        "Actual max-product endograph distance does not exceed the proposed grade-aware certificate.",
        "The grade-aware certificate does not exceed the simple certificate.",
        "Spatial radius propagation includes representative residuals.",
    ],
    "grade_cap_strictly_tighter_time_slices": grade_caps_helped,
    "negative_control": {
        "invented_certificate": "0",
        "actual_error": str(actual_invalid),
        "finding": "A claimed radius must be verified; trusting a supplied bound is unsound.",
    },
    "scientific_novelty": "Not established.",
}
target = Path(__file__).with_suffix(".json")
target.write_text(json.dumps(result, indent=2), encoding="utf-8")
print(json.dumps(result, indent=2))
