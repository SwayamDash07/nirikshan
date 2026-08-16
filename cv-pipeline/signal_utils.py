"""Small dependency-free signal functions shared by the CV pipeline and tests."""

from __future__ import annotations

from typing import Iterable


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
