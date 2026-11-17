"""Freeze base-problem partitions before labels and final measurements."""
import hashlib
import json
import random
from fractions import Fraction
from pathlib import Path


def build_manifest():
    """Return exact rational model definitions with disjoint mathematical base problems."""
    rng = random.Random(1711202604)
    problems = []
    partitions = [("train", 64), ("validation", 24), ("test-id-1d", 30),
                  ("test-id-2d", 30), ("test-ood-1d", 30), ("test-ood-2d", 30)]
    for split_number, (split, count) in enumerate(partitions):
        for index in range(count):
            dimension = (1 if index % 2 == 0 else 2) if split in ("train", "validation") else (
                1 if split.endswith("1d") else 2)
            ood = "ood" in split
            if dimension == 2:
                kind = "COUPLED_TENT"
                parameter = "1/2" if ood else rng.choice(["1/10", "1/4"])
            else:
                kind = ["AFFINE_CONTRACTION", "TENT", "LOGISTIC"][index % 3]
                parameter = {"AFFINE_CONTRACTION": "19/20" if ood else rng.choice(["1/4", "1/2", "3/4"]),
                             "TENT": None,
                             "LOGISTIC": "4" if ood else rng.choice(["3", "16/5", "7/2", "37/10"])}[kind]
            denominator = 1009 + 2 * split_number
            centers = [str(Fraction(rng.randint(150, 850), denominator)) for _ in range(dimension)]
            width = str(Fraction(rng.randint(20, 65) if ood else rng.randint(100, 240), 1000))
            shape = index % 4
            peaks = [{"center": centers, "width": width, "height": "1"}]
            if shape in (1, 2):
                peaks.append({"center": [str(1 - Fraction(c)) for c in centers], "width": width,
                              "height": "1/100" if shape == 1 else "3/4"})
            if shape == 3:
                peaks[0]["height"] = "2/3"
            identifier = f"{split}-{index:03d}"
            problems.append({"id": identifier, "baseProblemId": identifier, "split": split,
                             "definition": {"map": kind, "parameter": parameter, "peaks": peaks,
                                            "horizon": rng.choice([6, 8]) if ood else rng.choice([2, 4])}})
    return {"id": "learning-and-evaluation-split-v1", "date": "2026-11-17",
            "stage": "FROZEN_BEFORE_LABELS_AND_FINAL_EVALUATION", "generationSeed": 1711202604,
            "precision": 24, "labelMaxRefinements": 48, "labelMaxCells": 49, "labelMaxDepth": 12,
            "fairnessPeriod": 5, "labelTolerance": "0.000001", "modelSeeds": [11, 23, 37, 51, 71],
            "featureVersion": "cae-cell-features-v1",
            "primaryTarget": "signed local horizon-bound reduction from a complete trial split",
            "selectionRule": "max(0,predictedGain)/estimatedChildWork", "problems": problems}


def main():
    """Write a new frozen version or require byte-identical regeneration of the existing one."""
    root = Path(__file__).resolve().parents[1]
    target = root / "research/protocols/learning-and-evaluation-split-v1.json"
    payload = json.dumps(build_manifest(), indent=2) + "\n"
    if target.exists() and target.read_text(encoding="utf-8") != payload:
        raise RuntimeError("Frozen splits cannot be silently replaced; create a new version.")
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(payload, encoding="utf-8")
    print(json.dumps({"manifest": str(target), "sha256": hashlib.sha256(payload.encode()).hexdigest(),
                      "baseProblems": len(build_manifest()["problems"])}, indent=2))


if __name__ == "__main__":
    main()
