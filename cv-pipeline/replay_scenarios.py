"""Deterministic, clearly labelled replay fixtures for local demonstrations."""

from __future__ import annotations

import argparse
import json
from datetime import datetime, timedelta, timezone
from pathlib import Path


SCENARIOS = (
    "normal", "buildup", "surge", "persistent", "slowdown", "recovery",
    "normal_one_way", "slowing_flow", "reverse_movement", "conflicting_movement",
    "blocked_route", "alternate_exit_recovery",
    "stampede_precursor", "unusual_behavior",
)


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
        "normal_one_way": [0.35, 0.38, 0.36, 0.40],
        "slowing_flow": [1.20, 1.80, 2.40, 3.00, 3.60, 4.20],
        "reverse_movement": [1.0, 1.1, 1.2, 1.3, 1.2, 1.1],
        "conflicting_movement": [1.0, 1.2, 1.4, 1.5, 1.6, 1.5],
        "blocked_route": [1.0, 1.4, 1.9, 2.5, 3.0, 3.2],
        "alternate_exit_recovery": [3.2, 2.8, 2.2, 1.6, 1.1, 0.7],
        "stampede_precursor": [1.5, 2.4, 3.6, 4.8, 5.8, 6.2],
        "unusual_behavior": [1.0, 1.1, 1.2, 1.3, 1.4, 1.5],
    }[scenario]
    persistent_speeds = [1.0, 0.70, 0.45, 0.30, 0.20, 0.15, 0.12, 0.10, 0.08, 0.06]
    slowdown_speeds = [1.2, 1.1, 0.9, 0.7, 0.55, 0.4, 0.3, 0.25]
    events = []
    for index, density in enumerate(densities):
        high = density >= 4
        medium = density >= 1.5
        flow = {
            "normal_one_way": ("E", 90, .86, 0.02, 0.04, "NORMAL_FLOW"),
            "slowing_flow": ("E", 90, .84, 0.04, 0.03, "SLOWING_FLOW"),
            "reverse_movement": ("W", 270, .82, .72, .04, "REVERSE_FLOW"),
            "conflicting_movement": ("E", 90, .42, .12, .58, "CONFLICTING_FLOW"),
            "blocked_route": ("E", 90, .80, .04, .05, "SLOWING_FLOW"),
            "alternate_exit_recovery": ("NE", 45, .78, .02, .04, "NORMAL_FLOW"),
            "stampede_precursor": ("E", 90, .82, .52, .34, "UNUSUAL_BEHAVIOR"),
            "unusual_behavior": ("E", 90, .80, .55, .35, "UNUSUAL_BEHAVIOR"),
        }.get(scenario)
        event = {
            "zoneId": zone_id, "timestamp": (start + timedelta(seconds=index * 5)).isoformat().replace("+00:00", "Z"),
            "densityScore": density, "peopleCount": round(density * 20),
            "movementSpeed": persistent_speeds[index] if scenario == "persistent" else slowdown_speeds[index] if scenario in {"slowdown", "stampede_precursor"} else (0.15 if scenario == "unusual_behavior" and index >= 2 else 0.45 if high else 1.1),
            "riskLevel": "HIGH" if high else "MEDIUM" if medium else "LOW",
            "explanation": f"DEMO REPLAY: {scenario}; density={density:.2f} people/m2",
            "hotspotRegions": [{"gridPosition": "2,2", "relativeDensity": 2.2}] if scenario in {"persistent", "stampede_precursor"} or scenario == "slowdown" and density >= 3.6 or high else [],
            "sourceClipId": f"DEMO_REPLAY_{scenario.upper()}",
            "source": "SIMULATION",
        }
        if flow:
            direction, degrees, confidence, reverse, conflicting, state = flow
            event.update({
                "dominantDirection": direction, "directionDegrees": degrees,
                "directionConfidence": confidence, "directionalConsistency": confidence,
                "reverseMovementRatio": reverse, "conflictingMovementRatio": conflicting,
                "behaviorState": state,
                "behaviorExplanation": f"SIMULATION: deterministic {state.lower().replace('_', ' ')} fixture.",
            })
        events.append(event)
    return events


def generate_propagation_scenario(source_zone_id: int = 1, affected_zone_ids: tuple[int, ...] = (2, 3),
                                  start: datetime | None = None) -> list[dict]:
    """Deterministic connected-zone fixture: source elevates, neighbors rise within 60s."""
    start = start or datetime.now(timezone.utc).replace(microsecond=0)
    result: list[dict] = []
    for zone_id in (source_zone_id, *affected_zone_ids):
        for index, density in enumerate((1.0, 2.0, 4.5) if zone_id == source_zone_id else (0.8, 1.0, 2.0)):
            timestamp = start + timedelta(seconds=index * 20 + (0 if zone_id == source_zone_id else 20))
            result.append({
                "zoneId": zone_id, "timestamp": timestamp.isoformat().replace("+00:00", "Z"),
                "densityScore": density, "peopleCount": round(density * 20),
                "movementSpeed": .25 if density >= 4 else .8, "riskLevel": "HIGH" if density >= 4 else "MEDIUM" if density >= 1.5 else "LOW",
                "explanation": "DEMO REPLAY: panic propagation connected-zone fixture.",
                "hotspotRegions": [{"gridPosition": "2,2", "relativeDensity": 2.0}] if density >= 2 else [],
                "sourceClipId": "DEMO_REPLAY_PANIC_PROPAGATION", "source": "SIMULATION",
            })
    return result


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
