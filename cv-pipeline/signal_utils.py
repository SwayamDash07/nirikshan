"""Small dependency-free signal functions shared by the CV pipeline and tests."""

from __future__ import annotations

from collections import Counter, deque
from math import atan2, cos, degrees, hypot, radians, sin
from typing import Iterable


FLOW_STATES = (
    "NORMAL_FLOW", "RISING_FLOW", "SLOWING_FLOW", "REVERSE_FLOW",
    "CONFLICTING_FLOW", "UNUSUAL_BEHAVIOR", "INSUFFICIENT_DATA",
)


def _angular_difference(left: float, right: float) -> float:
    return abs((left - right + 180.0) % 360.0 - 180.0)


def direction_name(degrees_from_north: float) -> str:
    names = ("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    return names[int(((degrees_from_north % 360.0) + 22.5) // 45) % 8]


def rotate_flow_direction(flow: dict[str, float | str | int], camera_heading_degrees: float = 0.0) -> dict[str, float | str | int]:
    """Convert a camera-frame direction into the north-up map frame.

    The detector reports 0 degrees as the top edge of the video and increases
    clockwise. ``camera_heading_degrees`` is the compass heading represented by
    that top edge, so the world/map direction is their clockwise sum.
    """
    direction = flow.get("directionDegrees")
    if direction is None:
        return flow
    rotated = (float(direction) + float(camera_heading_degrees)) % 360.0
    adjusted = dict(flow)
    adjusted["directionDegrees"] = round(rotated, 2)
    adjusted["dominantDirection"] = direction_name(rotated)
    return adjusted


def estimate_flow_direction_from_vectors(vectors: Iterable[tuple[float, float]],
                                         min_tracked: int = 3,
                                         min_displacement: float = 2.0) -> dict[str, float | str | int]:
    """Estimate camera-frame direction using matched centroid displacement vectors.

    Degrees are compass-like: 0=N, 90=E, 180=S, 270=W. Image y increases
    downward, so the y component is inverted for the compass conversion.
    """
    vectors = [(float(dx), float(dy)) for dx, dy in vectors if hypot(dx, dy) >= min_displacement]
    if len(vectors) < min_tracked:
        return {"state": "INSUFFICIENT_DATA", "trackedPeople": len(vectors),
                "directionDegrees": None, "dominantDirection": "Unknown",
                "directionConfidence": 0.0, "directionalConsistency": 0.0,
                "reverseMovementRatio": 0.0, "conflictingMovementRatio": 0.0}

    angles = [degrees(atan2(dx, -dy)) % 360.0 for dx, dy in vectors]
    resultant_x = sum(cos(radians(angle)) for angle in angles)
    resultant_y = sum(sin(radians(angle)) for angle in angles)
    dominant = degrees(atan2(resultant_y, resultant_x)) % 360.0
    consistency = hypot(resultant_x, resultant_y) / len(angles)
    reverse = sum(_angular_difference(angle, dominant) >= 120.0 for angle in angles) / len(angles)
    conflicting = sum(60.0 <= _angular_difference(angle, dominant) < 120.0 for angle in angles) / len(angles)
    confidence = max(0.0, min(1.0, consistency * min(1.0, len(vectors) / float(min_tracked))))
    return {"state": "OK", "trackedPeople": len(vectors), "directionDegrees": round(dominant, 2),
            "dominantDirection": direction_name(dominant), "directionConfidence": round(confidence, 3),
            "directionalConsistency": round(consistency, 3),
            "reverseMovementRatio": round(reverse, 3), "conflictingMovementRatio": round(conflicting, 3)}


def estimate_flow_direction(previous_centroids: Iterable[tuple[float, float]],
                            current_centroids: Iterable[tuple[float, float]],
                            max_distance: float = 120.0, min_tracked: int = 3,
                            min_displacement: float = 2.0) -> dict[str, float | str | int]:
    """Match current centroids to the nearest previous centroids and estimate flow."""
    previous = [(float(x), float(y)) for x, y in previous_centroids]
    current = [(float(x), float(y)) for x, y in current_centroids]
    available = set(range(len(previous)))
    vectors: list[tuple[float, float]] = []
    for x, y in current:
        if not available:
            break
        index = min(available, key=lambda candidate: hypot(x - previous[candidate][0], y - previous[candidate][1]))
        dx, dy = x - previous[index][0], y - previous[index][1]
        if hypot(dx, dy) <= max_distance:
            vectors.append((dx, dy))
            available.remove(index)
    result = estimate_flow_direction_from_vectors(vectors, min_tracked, min_displacement)
    # Nearest-neighbour matching can pair people at the same spacing during a
    # uniform crowd translation. Use the tracked-set centroid shift as a
    # conservative fallback rather than claiming insufficient data.
    if result["state"] == "INSUFFICIENT_DATA" and len(previous) >= min_tracked and len(current) >= min_tracked:
        dx = sum(x for x, _ in current) / len(current) - sum(x for x, _ in previous) / len(previous)
        dy = sum(y for _, y in current) / len(current) - sum(y for _, y in previous) / len(previous)
        result = estimate_flow_direction_from_vectors([(dx, dy)] * min(len(previous), len(current)), min_tracked, min_displacement)
    return result


def temporal_reverse_ratio(previous_direction_degrees: float | None,
                           current_direction_degrees: float | None,
                           current_reverse_ratio: float = 0.0) -> float:
    """Treat a sustained 120°+ turn from the prior flow as temporal reversal."""
    if previous_direction_degrees is None or current_direction_degrees is None:
        return current_reverse_ratio
    if _angular_difference(previous_direction_degrees, current_direction_degrees) >= 120.0:
        return max(current_reverse_ratio, 1.0)
    return current_reverse_ratio


class BehaviorStateTracker:
    """Hold a behavior state until a candidate persists for two samples/10 seconds."""

    def __init__(self, min_samples: int = 2, min_duration_seconds: float = 10.0) -> None:
        self.min_samples = min_samples
        self.min_duration_seconds = min_duration_seconds
        self.state = "INSUFFICIENT_DATA"
        self.candidate = None
        self.candidate_since = None
        self.candidate_samples = 0

    def reset(self) -> None:
        self.state = "INSUFFICIENT_DATA"
        self.candidate = None
        self.candidate_since = None
        self.candidate_samples = 0

    def update(self, candidate: str, timestamp_seconds: float) -> str:
        if candidate not in FLOW_STATES:
            candidate = "UNUSUAL_BEHAVIOR"
        if candidate == self.state:
            self.candidate = candidate
            self.candidate_since = timestamp_seconds
            self.candidate_samples = 1
            return self.state
        if candidate != self.candidate:
            self.candidate = candidate
            self.candidate_since = timestamp_seconds
            self.candidate_samples = 1
            return self.state if self.state != "INSUFFICIENT_DATA" else "INSUFFICIENT_DATA"
        self.candidate_samples += 1
        duration = timestamp_seconds - (self.candidate_since or timestamp_seconds)
        if self.candidate_samples >= self.min_samples and duration >= self.min_duration_seconds:
            self.state = candidate
        return self.state


class FlowSignalSmoother:
    """Aggregate recent per-frame flow estimates before behavior classification.

    Centroid matching is intentionally lightweight, but individual frame
    estimates can jump when people cross or detections are briefly reassigned.
    Keeping a short weighted vector history makes the direction and the
    reverse/conflict ratios represent the recent movement pattern instead of
    one noisy frame.
    """

    def __init__(self, window_samples: int = 5, min_valid_samples: int = 2, min_consistency: float = 0.35) -> None:
        self.samples: deque[dict[str, float | str | int]] = deque(maxlen=max(1, window_samples))
        self.min_valid_samples = max(1, min_valid_samples)
        self.min_consistency = max(0.0, min(1.0, min_consistency))

    def reset(self) -> None:
        self.samples.clear()

    def update(self, flow: dict[str, float | str | int]) -> dict[str, float | str | int]:
        if flow.get("state") == "OK" and flow.get("directionDegrees") is not None:
            self.samples.append(dict(flow))
        if len(self.samples) < self.min_valid_samples:
            return flow

        x = y = total_weight = confidence = reverse = conflicting = tracked = 0.0
        for sample in self.samples:
            direction = float(sample["directionDegrees"])
            weight = max(0.05, float(sample.get("directionConfidence", 0.0)))
            radians_value = radians(direction)
            x += cos(radians_value) * weight
            y += sin(radians_value) * weight
            total_weight += weight
            confidence += float(sample.get("directionConfidence", 0.0))
            reverse += float(sample.get("reverseMovementRatio", 0.0))
            conflicting += float(sample.get("conflictingMovementRatio", 0.0))
            tracked += float(sample.get("trackedPeople", 0))

        if total_weight <= 0:
            return flow
        consistency = min(1.0, hypot(x, y) / total_weight)
        direction = degrees(atan2(y, x)) % 360.0
        smoothed_confidence = min(1.0, max(0.0, confidence / len(self.samples) * consistency))
        return {
            "state": "OK" if consistency >= self.min_consistency else "INSUFFICIENT_DATA",
            "trackedPeople": round(tracked / len(self.samples)),
            "directionDegrees": round(direction, 2) if consistency >= self.min_consistency else None,
            "dominantDirection": direction_name(direction) if consistency >= self.min_consistency else "Unknown",
            "directionConfidence": round(smoothed_confidence, 3),
            "directionalConsistency": round(consistency, 3),
            "reverseMovementRatio": round(max(0.0, min(1.0, reverse / len(self.samples))), 3),
            "conflictingMovementRatio": round(max(0.0, min(1.0, conflicting / len(self.samples))), 3),
        }


def behavior_candidate(flow: dict[str, float | str | int], speed: float,
                       previous_speed: float | None, density_change: float = 0.0) -> str:
    if flow.get("state") != "OK":
        return "INSUFFICIENT_DATA"
    if float(flow.get("reverseMovementRatio", 0.0)) >= 0.45:
        return "REVERSE_FLOW"
    if float(flow.get("conflictingMovementRatio", 0.0)) >= 0.30:
        return "CONFLICTING_FLOW"
    if previous_speed and speed < previous_speed * 0.75:
        return "SLOWING_FLOW"
    if density_change >= 0.20 or (previous_speed and speed > previous_speed * 1.15):
        return "RISING_FLOW"
    return "NORMAL_FLOW"


def detect_hotspots_from_counts(counts: list[list[int]], threshold: float = 1.5) -> list[dict[str, float | str]]:
    flat = [value for row in counts for value in row]
    average = sum(flat) / len(flat) if flat else 0.0
    if average <= 0:
        return []
    return [
        {"gridPosition": f"{row + 1},{column + 1}", "relativeDensity": round(counts[row][column] / average, 3)}
        for row in range(len(counts)) for column in range(len(counts[row]))
        if counts[row][column] > 0 and counts[row][column] / average > threshold
    ]


def detect_hotspots_from_centroids(centroids: Iterable[tuple[float, float]], frame_width: int, frame_height: int,
                                   grid_size: int = 3, threshold: float = 1.5) -> list[dict[str, float | str]]:
    counts = [[0 for _ in range(grid_size)] for _ in range(grid_size)]
    for x, y in centroids:
        column = min(grid_size - 1, max(0, int(x / max(frame_width, 1) * grid_size)))
        row = min(grid_size - 1, max(0, int(y / max(frame_height, 1) * grid_size)))
        counts[row][column] += 1
    return detect_hotspots_from_counts(counts, threshold)


def derive_signal_values(current_density: float, baseline_density: float, current_speed: float,
                         baseline_speed: float) -> tuple[float, float]:
    density_change = (current_density - baseline_density) / max(abs(baseline_density), 0.001)
    movement_slowdown = (baseline_speed - current_speed) / max(abs(baseline_speed), 0.001)
    return max(0.0, density_change), max(0.0, movement_slowdown)


def bottleneck_detected(hotspot_flags: Iterable[bool], slowdown_values: Iterable[float], density_start: float, density_end: float) -> bool:
    hotspots = list(hotspot_flags)
    slowdowns = list(slowdown_values)
    return len(hotspots) >= 10 and sum(hotspots) >= 7 and sum(value >= 0.20 for value in slowdowns) >= 5 and density_end >= density_start


def sustained_pattern(risk_levels: Iterable[str], densities: Iterable[float], span_seconds: float) -> bool:
    levels = list(risk_levels)
    values = list(densities)
    if len(levels) < 3 or len(values) != len(levels) or span_seconds < 20:
        return False
    medium_plus = sum(level in {"MEDIUM", "HIGH", "CRITICAL"} for level in levels) / len(levels)
    high_plus = sum(level in {"HIGH", "CRITICAL"} for level in levels) / len(levels)
    rising = sum(b >= a for a, b in zip(values, values[1:]))
    return medium_plus >= 0.65 or high_plus >= 0.40 or (values[-1] - values[0] >= 0.35 and rising >= max(2, len(values) // 2))
