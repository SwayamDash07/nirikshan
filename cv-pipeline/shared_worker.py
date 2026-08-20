"""One memory-bounded CV worker for all active Nirikshan video zones."""

from __future__ import annotations

import argparse
import json
import math
import time
from collections import deque
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import cv2

from privacy import FaceBlurProcessor
from process_video import (
    CentroidTracker, Detection, detect_hotspots, detect_people, load_json, load_model,
    perspective_weighted_people, post_event, resolve_device, rolling_average,
)
from risk_scoring import calculate_zone_risk
from signal_utils import (
    BehaviorStateTracker, FlowSignalSmoother, behavior_candidate,
    derive_signal_values, estimate_flow_direction_from_vectors,
    rotate_flow_direction, temporal_reverse_ratio,
)


@dataclass
class Stream:
    zone_id: int
    input_path: str
    source_clip_id: str
    capture: cv2.VideoCapture
    fps: float
    width: int
    height: int
    frame_index: int = 0
    last_emit: float = 0.0
    previous_speed: float | None = None
    previous_direction: float | None = None
    tracker: CentroidTracker = field(default_factory=lambda: CentroidTracker(120.0))
    behavior: BehaviorStateTracker = field(default_factory=lambda: BehaviorStateTracker(2, 10.0))
    smoother: FlowSignalSmoother = field(default_factory=lambda: FlowSignalSmoother(5, 2, 0.35))
    raw_history: deque[tuple[float, float, float]] = field(default_factory=lambda: deque(maxlen=80))
    smoothed_history: deque[tuple[float, float, float]] = field(default_factory=lambda: deque(maxlen=80))


def detect_people_tiled(model: Any, frame: Any, confidence: float, device: str, imgsz: int) -> list[Detection]:
    """Use overlapping tiles so small people in long views occupy more pixels."""
    height, width = frame.shape[:2]
    overlap = 0.20
    tile_width = min(width, max(1, round(width / 2 / (1.0 - overlap))))
    tile_height = min(height, max(1, round(height / 2 / (1.0 - overlap))))
    x_starts = sorted({0, max(0, width - tile_width)})
    y_starts = sorted({0, max(0, height - tile_height)})
    detections: list[Detection] = []
    for top in y_starts:
        for left in x_starts:
            crop = frame[top:top + tile_height, left:left + tile_width]
            for detection in detect_people(model, crop, confidence, device, augment=False, imgsz=imgsz):
                box = (detection.box[0] + left, detection.box[1] + top,
                       detection.box[2] + left, detection.box[3] + top)
                detections.append(Detection(((box[0] + box[2]) / 2, (box[1] + box[3]) / 2), box))
    kept: list[Detection] = []
    for candidate in sorted(detections, key=lambda item: (item.box[2] - item.box[0]) * (item.box[3] - item.box[1]), reverse=True):
        left, top, right, bottom = candidate.box
        overlaps = False
        for existing in kept:
            e_left, e_top, e_right, e_bottom = existing.box
            intersection = max(0, min(right, e_right) - max(left, e_left)) * max(0, min(bottom, e_bottom) - max(top, e_top))
            union = (right - left) * (bottom - top) + (e_right - e_left) * (e_bottom - e_top) - intersection
            if union > 0 and intersection / union >= 0.50:
                overlaps = True
                break
        if not overlaps:
            kept.append(candidate)
    return kept


def read_manifest(path: Path) -> list[dict[str, Any]]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
        return value if isinstance(value, list) else []
    except (FileNotFoundError, json.JSONDecodeError):
        return []


def open_stream(item: dict[str, Any]) -> Stream:
    input_path = str(item["input"])
    capture = cv2.VideoCapture(input_path)
    if not capture.isOpened():
        raise RuntimeError(f"Could not open input video: {input_path}")
    return Stream(
        int(item["zoneId"]), input_path,
        str(item.get("sourceClipId") or Path(input_path).stem), capture,
        capture.get(cv2.CAP_PROP_FPS) or 25.0,
        int(capture.get(cv2.CAP_PROP_FRAME_WIDTH)),
        int(capture.get(cv2.CAP_PROP_FRAME_HEIGHT)),
    )


def sync_streams(streams: dict[int, Stream], manifest: list[dict[str, Any]]) -> None:
    desired = {int(item["zoneId"]): item for item in manifest}
    for zone_id in list(streams):
        item = desired.get(zone_id)
        if item is None or str(item["input"]) != streams[zone_id].input_path:
            streams[zone_id].capture.release()
            del streams[zone_id]
    for zone_id, item in desired.items():
        if zone_id not in streams:
            streams[zone_id] = open_stream(item)
            print(f"SHARED_ZONE_STARTED zone={zone_id}", flush=True)


def emit_for_stream(stream: Stream, frame: Any, model: Any, privacy: FaceBlurProcessor,
                    thresholds: dict[str, Any], device: str, post_url: str, timeout: float) -> None:
    clean = privacy.sanitize(frame).frame
    detections = detect_people_tiled(model, clean, float(thresholds.get("personConfidence", 0.24)), device,
                                     int(thresholds.get("sharedInferenceImageSize", 640)))
    calibration = thresholds.get("zoneCalibration", {}).get(str(stream.zone_id), {})
    visible_area = max(float(calibration.get("visibleAreaSqMeters", 100.0)), 0.001)
    weighted_people = perspective_weighted_people(detections, calibration)
    timestamp_seconds = stream.frame_index / max(stream.fps, 1.0)
    vectors = stream.tracker.matched_displacements(detections)
    displacement = sum(math.hypot(dx, dy) for dx, dy in vectors) / len(vectors) if vectors else 0.0
    meters_per_pixel = float(calibration.get("metersPerPixel", thresholds.get("metersPerPixel", 0.02)))
    speed = displacement * meters_per_pixel * stream.fps
    stream.raw_history.append((timestamp_seconds, weighted_people / visible_area, speed))
    density, speed = rolling_average(stream.raw_history, timestamp_seconds, float(thresholds.get("rollingAverageSeconds", 3.0)))
    stream.smoothed_history.append((timestamp_seconds, density, speed))
    baseline = next((sample for sample in reversed(stream.smoothed_history)
                     if timestamp_seconds - sample[0] >= float(thresholds.get("lookbackSeconds", 10.0))), stream.smoothed_history[0])
    density_change, slowdown = derive_signal_values(density, baseline[1], speed, baseline[2])
    risk = calculate_zone_risk(density, density_change, speed, thresholds, speed_drop=slowdown)
    flow = estimate_flow_direction_from_vectors(
        vectors, int(thresholds.get("flowMinTrackedPeople", 3)),
        float(thresholds.get("flowMinDisplacementPixels", 2.0)),
    )
    flow = rotate_flow_direction(flow, float(calibration.get("cameraHeadingDegrees", 0.0)))
    if flow.get("directionDegrees") is not None:
        flow["reverseMovementRatio"] = round(temporal_reverse_ratio(
            stream.previous_direction, float(flow["directionDegrees"]), float(flow.get("reverseMovementRatio", 0.0))), 3)
        stream.previous_direction = float(flow["directionDegrees"])
    flow = stream.smoother.update(flow)
    behavior = stream.behavior.update(behavior_candidate(flow, speed, stream.previous_speed, density_change), timestamp_seconds)
    stream.previous_speed = speed
    event = {
        "zoneId": stream.zone_id,
        "timestamp": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "densityScore": round(risk.density, 4), "peopleCount": len(detections),
        "movementSpeed": round(risk.speed, 4), "riskLevel": risk.level,
        "explanation": risk.explanation, "densityChange": round(risk.density_increase, 4),
        "movementSlowdown": round(risk.speed_drop, 4), "hotspotPersistenceSeconds": 0.0,
        "hotspotRegions": detect_hotspots(detections, stream.width, stream.height),
        "dominantDirection": flow.get("dominantDirection", "Unknown"),
        "directionDegrees": flow.get("directionDegrees"), "directionConfidence": flow.get("directionConfidence", 0.0),
        "directionalConsistency": flow.get("directionalConsistency", 0.0),
        "reverseMovementRatio": flow.get("reverseMovementRatio", 0.0),
        "conflictingMovementRatio": flow.get("conflictingMovementRatio", 0.0),
        "behaviorState": behavior,
        "behaviorExplanation": "Direction estimate is insufficient for a reliable flow state." if behavior == "INSUFFICIENT_DATA" else f"Observed {behavior.lower().replace('_', ' ')} from smoothed tracked-person motion.",
        "sourceClipId": stream.source_clip_id, "source": "LIVE",
    }
    post_event(event, post_url, timeout)


def run(args: argparse.Namespace) -> None:
    thresholds = load_json(Path(args.thresholds))
    device = resolve_device()
    model = load_model(args.model, device)
    privacy = FaceBlurProcessor()
    streams: dict[int, Stream] = {}
    next_manifest_check = 0.0
    next_zone: int | None = None
    emit_every = float(thresholds.get("emitEverySeconds", 1.0))
    try:
        while True:
            now = time.monotonic()
            if now >= next_manifest_check:
                sync_streams(streams, read_manifest(Path(args.manifest)))
                next_manifest_check = now + 1.0
            if not streams:
                time.sleep(0.25)
                continue
            zone_ids = sorted(streams)
            next_zone = zone_ids[0] if next_zone not in streams else zone_ids[(zone_ids.index(next_zone) + 1) % len(zone_ids)]
            stream = streams[next_zone]
            ok, frame = stream.capture.read()
            if not ok:
                stream.capture.release()
                stream.capture = cv2.VideoCapture(stream.input_path)
                ok, frame = stream.capture.read()
            stream.frame_index += 1
            if not ok:
                continue
            sample_every = max(1, round(stream.fps / max(1.0, float(thresholds.get("loopDetectionSamplesPerSecond", 2.0)))))
            if stream.frame_index % sample_every == 0 and now - stream.last_emit >= emit_every:
                emit_for_stream(stream, frame, model, privacy, thresholds, device, args.post_url, args.timeout)
                stream.last_emit = now
    finally:
        for stream in streams.values():
            stream.capture.release()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", required=True)
    parser.add_argument("--thresholds", required=True)
    parser.add_argument("--post-url", required=True)
    parser.add_argument("--model", default="yolov8n.pt", help="YOLO weights (default: yolov8n.pt)")
    parser.add_argument("--timeout", type=float, default=60.0, help="HTTP POST timeout in seconds (default: 60)")
    run(parser.parse_args())


if __name__ == "__main__":
    main()
