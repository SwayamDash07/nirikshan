"""Deterministic, clearly labelled replay fixtures for local demonstrations."""

from __future__ import annotations

import argparse
import json
from datetime import datetime, timedelta, timezone
from pathlib import Path


SCENARIOS = ("normal", "buildup", "surge", "persistent", "slowdown", "recovery")


def generate_scenario(scenario: str, zone_id: int = 1, start: datetime | None = None) -> list[dict]:
    if scenario not in SCENARIOS:
        raise ValueError(f"unknown scenario: {scenario}")
    start = start or datetime.now(timezone.utc).replace(microsecond=0)
    densities = {
        "normal": [0.35, 0.38, 0.36, 0.40],
        "buildup": [0.40, 0.75, 1.20, 1.80, 2.30, 2.80],
        "surge": [0.40, 0.45, 4.50, 5.20, 6.20],
        "persistent": [3.20, 3.50, 3.80, 4.10, 4.30, 4.40, 4.50, 4.40, 4.30, 4.20],
        "slowdown": [1.20, 1.80, 2.40, 3.00, 3.60, 4.20, 4.80, 5.20],
        "recovery": [4.20, 3.20, 2.20, 1.10, 0.50, 0.35],
    }[scenario]
    persistent_speeds = [1.0, 0.70, 0.45, 0.30, 0.20, 0.15, 0.12, 0.10, 0.08, 0.06]
    slowdown_speeds = [1.2, 1.1, 0.9, 0.7, 0.55, 0.4, 0.3, 0.25]
    events = []
    for index, density in enumerate(densities):
        high = density >= 4
        medium = density >= 1.5
        events.append({
            "zoneId": zone_id, "timestamp": (start + timedelta(seconds=index * 5)).isoformat().replace("+00:00", "Z"),
            "densityScore": density, "peopleCount": round(density * 20),
            "movementSpeed": persistent_speeds[index] if scenario == "persistent" else slowdown_speeds[index] if scenario == "slowdown" else (0.45 if high else 1.1),
            "riskLevel": "HIGH" if high else "MEDIUM" if medium else "LOW",
            "explanation": f"DEMO REPLAY: {scenario}; density={density:.2f} people/m2",
            "hotspotRegions": [{"gridPosition": "2,2", "relativeDensity": 2.2}] if scenario == "persistent" or scenario == "slowdown" and density >= 3.6 or high else [],
            "sourceClipId": f"DEMO_REPLAY_{scenario.upper()}",
            "source": "SIMULATION",
        })
    return events


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("scenario", choices=SCENARIOS)
    parser.add_argument("--zone-id", type=int, default=1)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    Path(args.output).write_text(json.dumps(generate_scenario(args.scenario, args.zone_id), indent=2), encoding="utf-8")
    print(f"Wrote DEMO REPLAY fixture: {args.output}")


if __name__ == "__main__":
    main()
