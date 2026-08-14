"""Transparent, tunable risk scoring for Nirikshan.

This module intentionally contains no OpenCV or detector code. It converts the
latest zone measurements into a normalized score and a risk level, making the
formula easy to audit and tune independently of computer vision.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any


LEVELS = ("LOW", "MEDIUM", "HIGH", "CRITICAL")


@dataclass(frozen=True)
class ZoneRisk:
    score: float
    level: str
    density: float
    density_increase: float
    speed: float
    speed_drop: float
    explanation: str


def _clamp(value: float, lower: float = 0.0, upper: float = 1.0) -> float:
    return max(lower, min(upper, value))


def _severity_from_thresholds(value: float, thresholds: dict[str, float]) -> str:
    if value >= thresholds["critical"]:
        return "CRITICAL"
    if value >= thresholds["high"]:
        return "HIGH"
    if value >= thresholds["medium"]:
        return "MEDIUM"
    return "LOW"


def _percent(value: float) -> int:
    return round(max(0.0, value) * 100)


def calculate_zone_risk(
    density: float,
    density_increase: float,
    movement_speed: float,
    config: dict[str, Any],
    speed_drop: float | None = None,
) -> ZoneRisk:
    """Calculate explainable zone risk from current and historical signals.

    ``density_increase`` and ``speed_drop`` are fractions, e.g. 0.65 means
    65%. Each signal is normalized using configurable thresholds, then combined
    with configurable weights. The final score is deliberately not a learned
    model: it is a transparent decision-support heuristic.
    """
    density_thresholds = config["densityThresholds"]
    increase_thresholds = config["densityIncreaseThresholds"]
    speed_thresholds = config["speedDropThresholds"]
    score_thresholds = config["riskScoreThresholds"]

    density_signal = _clamp(density / density_thresholds["critical"])
    increase_signal = _clamp(density_increase / increase_thresholds["critical"])
    if speed_drop is None:
        normal_speed = max(float(config.get("normalSpeedMps", 1.4)), 0.001)
        speed_drop = _clamp(1.0 - (movement_speed / normal_speed))
    else:
        speed_drop = _clamp(speed_drop)
    speed_signal = _clamp(speed_drop / speed_thresholds["critical"])

    weights = config["riskWeights"]
    total_weight = sum(float(v) for v in weights.values()) or 1.0
    score = (
        float(weights["density"]) * density_signal
        + float(weights["densityIncrease"]) * increase_signal
        + float(weights["speedDrop"]) * speed_signal
    ) / total_weight
    score = round(_clamp(score), 4)

    density_level = _severity_from_thresholds(density, density_thresholds)
    score_level = _severity_from_thresholds(score, score_thresholds)
    level = max((density_level, score_level), key=LEVELS.index)

    explanation = (
        f"Density reached {density:.2f} people/m², a {_percent(density_increase)}% increase "
        f"over the lookback window, with average movement speed at {movement_speed:.2f} m/s "
        f"({_percent(speed_drop)}% below baseline)"
    )
    return ZoneRisk(score, level, density, density_increase, movement_speed, speed_drop, explanation)
