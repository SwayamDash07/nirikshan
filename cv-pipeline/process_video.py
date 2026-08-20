"""Process one CCTV video as one physical Nirikshan zone.

Example:
    python process_video.py --input clip.mp4 --zone-id 1 \
      --thresholds thresholds_config.json --output events.json --annotate

For a direct count comparison against the previous nano model, add:
    --compare-model yolov8n.pt
"""

from __future__ import annotations

import argparse
import json
import math
import os
import queue
import signal
import socket
import shutil
import subprocess
import sys
import threading
import time
from collections import deque
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any

import cv2
import numpy as np
import requests
import torch
from ultralytics import YOLO
from privacy import FaceBlurProcessor

from risk_scoring import ZoneRisk, calculate_zone_risk
from signal_utils import (
    BehaviorStateTracker,
    FlowSignalSmoother,
    behavior_candidate,
    detect_hotspots_from_centroids,
    derive_signal_values,
    estimate_flow_direction_from_vectors,
    rotate_flow_direction,
    temporal_reverse_ratio,
)


RISK_COLORS = {
    "LOW": (60, 180, 75),
    "MEDIUM": (0, 215, 255),
    "HIGH": (0, 140, 255),
    "CRITICAL": (0, 0, 255),
}
RISK_ORDER = {"LOW": 0, "MEDIUM": 1, "HIGH": 2, "CRITICAL": 3}
HOTSPOT_GRID_SIZE = 3
HOTSPOT_THRESHOLD = 1.5
HOTSPOT_COLOR = (255, 0, 210)
FLOW_LOCK_PORT_BASE = 43_700


class ZoneProcessLock:
    """Prevent two local CV workers from publishing the same zone telemetry."""

    def __init__(self, zone_id: int) -> None:
        self.port = FLOW_LOCK_PORT_BASE + zone_id
        self._socket: socket.socket | None = None

    def acquire(self) -> None:
        lock_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        try:
            lock_socket.setsockopt(socket.SOL_SOCKET, socket.SO_EXCLUSIVEADDRUSE, 1)
        except (AttributeError, OSError):
            pass
        try:
            lock_socket.bind(("127.0.0.1", self.port))
            lock_socket.listen(1)
        except OSError as error:
            lock_socket.close()
            raise RuntimeError(
                f"Another CV worker already owns zone {self.port - FLOW_LOCK_PORT_BASE}; "
                "refusing to publish duplicate telemetry"
            ) from error
        self._socket = lock_socket

    def release(self) -> None:
        if self._socket is not None:
            self._socket.close()
            self._socket = None


@dataclass
class Detection:
    centroid: tuple[float, float]
    box: tuple[int, int, int, int]


class CentroidTracker:
    """Nearest-centroid tracker for approximate whole-frame motion."""

    def __init__(self, max_distance_pixels: float) -> None:
        self.max_distance = max_distance_pixels
        self.previous: list[tuple[float, float]] = []

    def matched_displacements(self, current: list[Detection]) -> list[tuple[float, float]]:
        current_points = [d.centroid for d in current]
        previous_points = self.previous
        available = set(range(len(self.previous)))
        vectors: list[tuple[float, float]] = []
        for point in current_points:
            if not available:
                break
            nearest = min(available, key=lambda index: math.dist(point, self.previous[index]))
            distance = math.dist(point, self.previous[nearest])
            if distance <= self.max_distance:
                vectors.append((point[0] - self.previous[nearest][0], point[1] - self.previous[nearest][1]))
                available.remove(nearest)
        if len(vectors) < 3 and len(previous_points) >= 3 and len(current_points) >= 3:
            dx = sum(point[0] for point in current_points) / len(current_points) - sum(point[0] for point in previous_points) / len(previous_points)
            dy = sum(point[1] for point in current_points) / len(current_points) - sum(point[1] for point in previous_points) / len(previous_points)
            if math.hypot(dx, dy) >= 2:
                vectors = [(dx, dy)] * min(len(previous_points), len(current_points))
        self.previous = current_points
        return vectors


def load_json(path: Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8") as stream:
        return json.load(stream)


def detect_people(model: YOLO, frame: Any, confidence: float, device: str, augment: bool = False,
                  imgsz: int | None = None) -> list[Detection]:
    options = {"conf": confidence, "classes": [0], "augment": augment, "device": device, "verbose": False}
    if imgsz is not None:
        options["imgsz"] = imgsz
    results = model.predict(frame, **options)
    detections: list[Detection] = []
    if not results or results[0].boxes is None:
        return detections
    for box in results[0].boxes.xyxy.cpu().tolist():
        left, top, right, bottom = [round(value) for value in box]
        detections.append(Detection(((left + right) / 2, (top + bottom) / 2), (left, top, right, bottom)))
    return detections


def detect_hotspots(detections: list[Detection], frame_width: int, frame_height: int,
                    grid_size: int = HOTSPOT_GRID_SIZE, threshold: float = HOTSPOT_THRESHOLD) -> list[dict[str, Any]]:
    """Find coarse within-camera crowd concentration using the tested signal helper."""
    return detect_hotspots_from_centroids((detection.centroid for detection in detections), frame_width, frame_height, grid_size, threshold)


def draw_hotspots(frame: Any, hotspots: list[dict[str, Any]], width: int, height: int,
                  grid_size: int = HOTSPOT_GRID_SIZE) -> None:
    cell_width, cell_height = width / grid_size, height / grid_size
    for hotspot in hotspots:
        row, column = [int(value) - 1 for value in hotspot["gridPosition"].split(",")]
        left, top = round(column * cell_width), round(row * cell_height)
        right, bottom = round((column + 1) * cell_width), round((row + 1) * cell_height)
        cv2.rectangle(frame, (left, top), (right, bottom), HOTSPOT_COLOR, max(3, round(width / 280)))
        cv2.putText(frame, f"HOTSPOT {hotspot['relativeDensity']:.1f}x", (left + 8, top + 26),
                    cv2.FONT_HERSHEY_SIMPLEX, max(0.45, min(0.75, width / 2000)), HOTSPOT_COLOR, 2, cv2.LINE_AA)


def make_writer(path: Path, fps: float, width: int, height: int) -> cv2.VideoWriter:
    writer = cv2.VideoWriter(str(path), cv2.VideoWriter_fourcc(*"mp4v"), fps, (width, height))
    if not writer.isOpened():
        raise RuntimeError(f"Could not open annotated video writer: {path}")
    return writer


def transcode_annotation(raw_path: Path, final_path: Path) -> None:
    """Convert OpenCV's broadly available MPEG-4 output to browser-safe H.264."""
    ffmpeg = shutil.which("ffmpeg")
    if not ffmpeg:
        print("Warning: ffmpeg was not found; keeping the raw annotated MP4. Chrome may not play it.", file=sys.stderr)
        if raw_path != final_path:
            raw_path.replace(final_path)
        return
    command = [
        ffmpeg, "-y", "-i", str(raw_path), "-c:v", "libx264", "-pix_fmt", "yuv420p",
        "-movflags", "+faststart", "-an", str(final_path),
    ]
    result = subprocess.run(command, capture_output=True, text=True)
    if result.returncode != 0:
        print(f"Warning: H.264 conversion failed; keeping raw annotation. {result.stderr[-1200:]}", file=sys.stderr)
        if final_path.exists():
            final_path.unlink()
        raw_path.replace(final_path)
        return
    if raw_path != final_path and raw_path.exists():
        raw_path.unlink()
    print(f"Browser-compatible annotated video written: {final_path}")


def draw_text_box(frame: Any, lines: list[str], origin: tuple[int, int], color: tuple[int, int, int],
                  font_scale: float = 0.6, line_gap: int = 8, alpha: float = 0.75) -> None:
    font = cv2.FONT_HERSHEY_SIMPLEX
    thickness = max(1, round(font_scale * 2))
    sizes = [cv2.getTextSize(line, font, font_scale, thickness)[0] for line in lines]
    width = max(size[0] for size in sizes) + 20
    line_height = max(size[1] for size in sizes) + line_gap
    height = line_height * len(lines) + 12
    x, y = max(4, origin[0]), min(origin[1], frame.shape[0] - 4)
    panel = frame.copy()
    cv2.rectangle(panel, (x, y - height), (x + width, y), (15, 20, 25), -1)
    cv2.addWeighted(panel, alpha, frame, 1.0 - alpha, 0, frame)
    for index, line in enumerate(lines):
        baseline = y - height + 24 + index * line_height
        cv2.putText(frame, line, (x + 10, baseline), font, font_scale, color, thickness, cv2.LINE_AA)


def format_video_time(seconds: float) -> str:
    total = max(0, int(seconds))
    return f"{total // 60:02d}:{total % 60:02d}"


def density_trend(samples: deque[tuple[float, float, float]], timestamp_seconds: float, lookback_seconds: float = 5.0) -> str:
    if not samples:
        return "→"
    baseline = next((sample for sample in reversed(samples) if timestamp_seconds - sample[0] >= lookback_seconds), samples[0])
    change = (samples[-1][1] - baseline[1]) / max(abs(baseline[1]), 0.001)
    if change > 0.05:
        return "↑"
    if change < -0.05:
        return "↓"
    return "→"


def rolling_average(samples: deque[tuple[float, float, float]], now: float, window_seconds: float) -> tuple[float, float]:
    recent = [sample for sample in samples if now - sample[0] <= window_seconds]
    if not recent:
        return 0.0, 0.0
    return (
        sum(sample[1] for sample in recent) / len(recent),
        sum(sample[2] for sample in recent) / len(recent),
    )


def perspective_weighted_people(detections: list[Detection], calibration: dict[str, Any]) -> float:
    """Estimate crowd presence for a front-facing camera using box height as a depth proxy.

    Smaller boxes are usually further from the camera and represent more of the
    scene's ground area. We therefore increase their contribution slightly,
    while clamping the result so a missed or unusually small box cannot skew
    the density estimate. Raw detector count is retained for `peopleCount`.
    """
    correction = calibration.get("perspectiveCorrection", {})
    if not correction.get("enabled", False):
        return float(len(detections))
    reference_height = max(float(correction.get("referenceBoxHeightPixels", 100.0)), 1.0)
    min_weight = float(correction.get("minWeight", 0.70))
    max_weight = max(min_weight, float(correction.get("maxWeight", 1.60)))
    return sum(
        min(max(reference_height / max(detection.box[3] - detection.box[1], 1), min_weight), max_weight)
        for detection in detections
    )


def is_near_camera(detection: Detection, calibration: dict[str, Any]) -> bool:
    """Use the same box-height boundary as perspective correction for the HUD."""
    correction = calibration.get("perspectiveCorrection", {})
    if not correction.get("enabled", False):
        return True
    reference_height = max(float(correction.get("referenceBoxHeightPixels", 100.0)), 1.0)
    return (detection.box[3] - detection.box[1]) >= reference_height


def draw_dashed_rectangle(frame: Any, box: tuple[int, int, int, int], color: tuple[int, int, int], thickness: int) -> None:
    """Draw a compact dashed box for farther, perspective-corrected detections."""
    left, top, right, bottom = box
    dash_length, gap = 9, 6
    for start, end in (((left, top), (right, top)), ((right, top), (right, bottom)),
                       ((right, bottom), (left, bottom)), ((left, bottom), (left, top))):
        dx, dy = end[0] - start[0], end[1] - start[1]
        length = max(abs(dx), abs(dy))
        if length == 0:
            continue
        step_x, step_y = dx / length, dy / length
        for offset in range(0, length, dash_length + gap):
            segment_end = min(offset + dash_length, length)
            cv2.line(
                frame,
                (round(start[0] + step_x * offset), round(start[1] + step_y * offset)),
                (round(start[0] + step_x * segment_end), round(start[1] + step_y * segment_end)),
                color,
                thickness,
            )


def draw_trend_arrow(frame: Any, trend: str, x: int, y: int, color: tuple[int, int, int]) -> None:
    if trend == "↑":
        start, end = (x, y + 12), (x, y - 12)
    elif trend == "↓":
        start, end = (x, y - 12), (x, y + 12)
    else:
        start, end = (x - 12, y), (x + 12, y)
    cv2.arrowedLine(frame, start, end, color, 3, tipLength=0.35)


def apply_density_heatmap(frame: Any, detections: list[Detection]) -> Any:
    """Blend a Gaussian density field over detected person centroids."""
    if not detections:
        return frame
    height, width = frame.shape[:2]
    density = np.zeros((height, width), dtype=np.float32)
    kernel_radius = max(12, round(min(width, height) * 0.025))
    for detection in detections:
        x, y = round(detection.centroid[0]), round(detection.centroid[1])
        if 0 <= x < width and 0 <= y < height:
            cv2.circle(density, (x, y), kernel_radius, 1.0, -1)
    density = cv2.GaussianBlur(density, (0, 0), sigmaX=kernel_radius, sigmaY=kernel_radius)
    peak = float(density.max())
    if peak <= 0:
        return frame
    normalized = np.clip(density / peak, 0.0, 1.0)
    color_map = cv2.applyColorMap((normalized * 255).astype(np.uint8), cv2.COLORMAP_JET)
    alpha = (normalized * 0.62)[..., None]
    blended = frame.astype(np.float32) * (1.0 - alpha) + color_map.astype(np.float32) * alpha
    return np.clip(blended, 0, 255).astype(np.uint8)


def resolve_device() -> str:
    if torch.cuda.is_available():
        device_name = torch.cuda.get_device_name(0)
        print(f"Using device: cuda ({device_name})")
        return "cuda"
    if os.environ.get("NIRIKSHAN_REQUIRE_CUDA") == "1":
        raise RuntimeError("CUDA is required for the local zone worker but torch.cuda.is_available() is false; check the CUDA PyTorch environment")
    print("Using device: cpu (no GPU detected)")
    return "cpu"


def load_model(weights: str, device: str) -> YOLO:
    model = YOLO(weights)
    model.to(device)
    return model


def process_video(args: argparse.Namespace) -> list[dict[str, Any]]:
    thresholds = load_json(Path(args.thresholds))
    device = resolve_device()
    model = load_model(args.model, device)
    privacy = FaceBlurProcessor()
    capture = cv2.VideoCapture(str(args.input))
    if not capture.isOpened():
        raise RuntimeError(f"Could not open input video: {args.input}")

    fps = capture.get(cv2.CAP_PROP_FPS) or 25.0
    frame_width = int(capture.get(cv2.CAP_PROP_FRAME_WIDTH))
    frame_height = int(capture.get(cv2.CAP_PROP_FRAME_HEIGHT))
    frame_count = int(capture.get(cv2.CAP_PROP_FRAME_COUNT))
    if args.loop:
        detection_samples_per_second = max(1.0, float(thresholds.get("loopDetectionSamplesPerSecond", 5.0)))
        process_every = max(1, round(fps / detection_samples_per_second))
    else:
        process_every = max(1, int(thresholds.get("processEveryNFrames", 3)))
    emit_every = float(thresholds.get("emitEverySeconds", 1.0))
    lookback = float(thresholds.get("lookbackSeconds", 10.0))
    smoothing_window = float(thresholds.get("rollingAverageSeconds", 3.0))
    zone_calibration = thresholds.get("zoneCalibration", {}).get(str(args.zone_id), {})
    calibration = {**thresholds, **zone_calibration}
    camera_heading_degrees = float(calibration.get("cameraHeadingDegrees", 0.0))
    meters_per_pixel = float(calibration.get("metersPerPixel", 0.02))
    visible_area = max(float(calibration.get("visibleAreaSqMeters", 100.0)), 0.001)
    max_history = max(10, round((lookback + smoothing_window + 5.0) * fps / process_every))
    tracker = CentroidTracker(float(thresholds.get("maxTrackDistancePixels", 120.0)))
    behavior_tracker = BehaviorStateTracker(
        min_samples=int(thresholds.get("flowMinStateSamples", 2)),
        min_duration_seconds=float(thresholds.get("flowMinStateDurationSeconds", 10.0)),
    )
    flow_smoother = FlowSignalSmoother(
        window_samples=int(thresholds.get("flowSmoothingSamples", 5)),
        min_valid_samples=int(thresholds.get("flowSmoothingMinSamples", 2)),
        min_consistency=float(thresholds.get("flowSmoothingMinConsistency", 0.35)),
    )
    raw_history: deque[tuple[float, float, float]] = deque(maxlen=max_history)
    smoothed_history: deque[tuple[float, float, float]] = deque(maxlen=max_history)
    events: list[dict[str, Any]] = []
    confidence = args.confidence if args.confidence is not None else float(thresholds.get("personConfidence", 0.28))
    augment = bool(thresholds.get("augment", False))
    detection_log_path = Path(args.detection_log or f"{Path(args.output).with_suffix('')}_detection_counts.csv")
    comparison_model = load_model(args.compare_model, device) if args.compare_model else None
    detection_log_path.parent.mkdir(parents=True, exist_ok=True)
    detection_log = detection_log_path.open("w", encoding="utf-8")
    detection_log.write("frame,timestamp_seconds,model,confidence,augment,people_detected,manual_reference_count\\n")
    print(
        f"Detection audit: model={args.model}, confidence={confidence:.2f}, augment={augment}, "
        f"visible_area={visible_area:.1f}m2, perspective_correction="
        f"{calibration.get('perspectiveCorrection', {}).get('enabled', False)}"
    )
    if comparison_model is not None:
        print(f"Before/after comparison enabled: before={args.compare_model}, after={args.model}")
    replay_start = datetime.fromisoformat(args.start_time.replace("Z", "+00:00")) if args.start_time else datetime.now(timezone.utc)
    if replay_start.tzinfo is None:
        replay_start = replay_start.replace(tzinfo=timezone.utc)

    writer = None
    annotation_output: Path | None = None
    raw_annotation_output: Path | None = None
    if args.annotate:
        annotation_output = Path(args.annotation_output or f"{Path(args.output).with_suffix('')}_annotated.mp4")
        raw_annotation_output = annotation_output.with_name(f"{annotation_output.stem}_raw.mp4")
        writer = make_writer(raw_annotation_output, fps, frame_width, frame_height)

    frame_index = 0
    loop_iteration = 0
    last_emit = -emit_every
    next_live_emit_at = time.monotonic()
    next_frame_due = time.monotonic()
    live_mode = bool(args.loop or args.post_live)
    # Keep cloud delivery off the camera/inference thread. The queue is
    # deliberately bounded so a slow backend cannot grow worker memory.
    delivery_queue = EventDeliveryQueue(args.post_url or "http://localhost:8080/api/risk-events", args.timeout) if args.loop and args.post_live else None
    latest_risk: ZoneRisk | None = None
    latest_trend = "→"
    latest_density = 0.0
    latest_speed = 0.0
    previous_speed: float | None = None
    latest_flow: dict[str, Any] = estimate_flow_direction_from_vectors([])
    latest_behavior_state = "INSUFFICIENT_DATA"
    previous_direction_degrees: float | None = None
    latest_hotspots: list[dict[str, Any]] = []
    hotspot_started_at: float | None = None
    hotspot_persistence_seconds = 0.0
    current_total_people = 0
    detections: list[Detection] = []
    last_posted_video_seconds: float | None = None
    debug_reported = False
    try:
        while True:
            ok, frame = capture.read()
            if not ok:
                if not args.loop:
                    break
                capture.release()
                capture = cv2.VideoCapture(str(args.input))
                if not capture.isOpened():
                    raise RuntimeError(f"Could not reopen input video after loop {loop_iteration}: {args.input}")
                loop_iteration += 1
                frame_index = 0
                tracker.previous = []
                previous_direction_degrees = None
                previous_speed = None
                behavior_tracker.reset()
                flow_smoother.reset()
                raw_history.clear()
                smoothed_history.clear()
                print(f"LOOP_ITERATION {loop_iteration}", flush=True)
                continue
            timestamp_seconds = frame_index / fps
            is_detection_frame = frame_index % process_every == 0
            # Skipped loop frames are only read to advance the video position and
            # are never detected or emitted. Keep privacy processing for frames
            # that are actually used, plus every frame when annotation output is
            # enabled so written video remains privacy-safe.
            if is_detection_frame or writer is not None:
                frame = privacy.sanitize(frame).frame
            if is_detection_frame:
                detections = detect_people(model, frame, confidence, device=device, augment=augment)
                current_total_people = len(detections)
                latest_hotspots = detect_hotspots(detections, frame_width, frame_height)
                if latest_hotspots:
                    hotspot_started_at = timestamp_seconds if hotspot_started_at is None else hotspot_started_at
                    hotspot_persistence_seconds = max(0.0, timestamp_seconds - hotspot_started_at)
                else:
                    hotspot_started_at = None
                    hotspot_persistence_seconds = 0.0
                weighted_people = perspective_weighted_people(detections, calibration)
                before_count = ""
                if comparison_model is not None:
                    before_count = str(len(detect_people(comparison_model, frame, confidence, device=device, augment=False)))
                    print(
                        f"COUNT frame={frame_index} time={timestamp_seconds:.2f}s "
                        f"before({args.compare_model})={before_count} after({args.model})={current_total_people}"
                    )
                else:
                    print(f"COUNT frame={frame_index} time={timestamp_seconds:.2f}s model={args.model} people={current_total_people}")
                detection_log.write(
                    f"{frame_index},{timestamp_seconds:.3f},{args.model},{confidence:.3f},{augment},"
                    f"{current_total_people},{before_count}\\n"
                )
                detection_log.flush()
                raw_density = weighted_people / visible_area
                movement_vectors = tracker.matched_displacements(detections)
                displacement_pixels = sum(math.hypot(dx, dy) for dx, dy in movement_vectors) / len(movement_vectors) if movement_vectors else 0.0
                raw_speed = displacement_pixels * meters_per_pixel * fps / process_every
                raw_history.append((timestamp_seconds, raw_density, raw_speed))
                latest_density, latest_speed = rolling_average(raw_history, timestamp_seconds, smoothing_window)
                smoothed_history.append((timestamp_seconds, latest_density, latest_speed))

                baseline = next(
                    (sample for sample in reversed(smoothed_history) if timestamp_seconds - sample[0] >= lookback),
                    smoothed_history[0],
                )
                density_increase, speed_drop = derive_signal_values(latest_density, baseline[1], latest_speed, baseline[2])
                latest_risk = calculate_zone_risk(
                    latest_density, density_increase, latest_speed, thresholds, speed_drop=speed_drop
                )
                latest_trend = density_trend(smoothed_history, timestamp_seconds)
                raw_flow = estimate_flow_direction_from_vectors(
                    movement_vectors,
                    min_tracked=int(thresholds.get("flowMinTrackedPeople", 3)),
                    min_displacement=float(thresholds.get("flowMinDisplacementPixels", 2.0)),
                )
                latest_flow = rotate_flow_direction(
                    flow_smoother.update(raw_flow), camera_heading_degrees
                )
                if latest_flow.get("directionDegrees") is not None:
                    latest_flow["reverseMovementRatio"] = round(temporal_reverse_ratio(
                        previous_direction_degrees,
                        float(latest_flow["directionDegrees"]),
                        float(latest_flow.get("reverseMovementRatio", 0.0)),
                    ), 3)
                    previous_direction_degrees = float(latest_flow["directionDegrees"])
                candidate = behavior_candidate(latest_flow, latest_speed, previous_speed, density_increase)
                latest_behavior_state = behavior_tracker.update(candidate, timestamp_seconds)
                previous_speed = latest_speed

                if args.debug_calibration and not debug_reported and current_total_people > 0:
                    print("CALIBRATION DEBUG (first non-empty sample frame)")
                    print(f"  frame: {frame_index} ({timestamp_seconds:.2f}s)")
                    print(f"  raw detected person count: {current_total_people}")
                    print(f"  estimated effective zone area: {visible_area:.2f} m2")
                    print(f"  perspective-corrected detection count: {weighted_people:.2f}")
                    print(f"  density: {weighted_people:.2f} / {visible_area:.2f} = {latest_density:.2f} people/m2")
                    print(f"  resulting risk: {latest_risk.level} (score {latest_risk.score:.3f})")
                    debug_reported = True

                now_monotonic = time.monotonic()
                should_emit = now_monotonic >= next_live_emit_at if args.loop else timestamp_seconds - last_emit >= emit_every
                if should_emit:
                    event_timestamp = datetime.now(timezone.utc) if args.loop else replay_start + timedelta(seconds=timestamp_seconds)
                    events.append({
                        "zoneId": args.zone_id,
                        "timestamp": event_timestamp.isoformat().replace("+00:00", "Z"),
                        "densityScore": round(latest_risk.density, 4),
                        "peopleCount": current_total_people,
                        "movementSpeed": round(latest_risk.speed, 4),
                        "riskLevel": latest_risk.level,
                        "explanation": latest_risk.explanation,
                        "densityChange": round(latest_risk.density_increase, 4),
                        "movementSlowdown": round(latest_risk.speed_drop, 4),
                        "hotspotPersistenceSeconds": round(hotspot_persistence_seconds, 1),
                        "hotspotRegions": latest_hotspots,
                        "dominantDirection": latest_flow.get("dominantDirection", "Unknown"),
                        "directionDegrees": latest_flow.get("directionDegrees"),
                        "directionConfidence": latest_flow.get("directionConfidence", 0.0),
                        "directionalConsistency": latest_flow.get("directionalConsistency", 0.0),
                        "reverseMovementRatio": latest_flow.get("reverseMovementRatio", 0.0),
                        "conflictingMovementRatio": latest_flow.get("conflictingMovementRatio", 0.0),
                        "behaviorState": latest_behavior_state,
                        "behaviorExplanation": (
                            "Direction estimate is insufficient for a reliable flow state."
                            if latest_behavior_state == "INSUFFICIENT_DATA"
                            else f"Observed {latest_behavior_state.lower().replace('_', ' ')} from smoothed tracked-person motion."
                        ),
                        "sourceClipId": args.source_clip_id or Path(args.input).stem,
                        "source": "LIVE",
                    })
                    if live_mode:
                        if args.loop:
                            delivery_queue.submit(events[-1]) if delivery_queue else post_event(events[-1], args.post_url or "http://localhost:8080/api/risk-events", args.timeout)
                            next_live_emit_at = now_monotonic + emit_every
                        else:
                            if last_posted_video_seconds is not None:
                                wait_seconds = max(0.0, timestamp_seconds - last_posted_video_seconds) + args.post_delay
                                if wait_seconds > 0:
                                    time.sleep(wait_seconds)
                            post_event(events[-1], args.post_url or "http://localhost:8080/api/risk-events", args.timeout)
                            last_posted_video_seconds = timestamp_seconds
                    last_emit = timestamp_seconds
                    if args.loop:
                        if len(events) > 600:
                            del events[:-600]
                        Path(args.output).write_text(json.dumps(events, indent=2), encoding="utf-8")

            if writer is not None:
                if args.heatmap_overlay is not False:
                    frame = apply_density_heatmap(frame, detections)
                draw_hotspots(frame, latest_hotspots, frame_width, frame_height)
                level = latest_risk.level if latest_risk else "LOW"
                color = RISK_COLORS[level]
                cv2.rectangle(frame, (2, 2), (frame_width - 3, frame_height - 3), color, max(3, round(frame_width / 350)))
                for detection in detections:
                    if is_near_camera(detection, calibration):
                        cv2.rectangle(frame, detection.box[:2], detection.box[2:], color, max(2, round(frame_width / 600)))
                    else:
                        draw_dashed_rectangle(frame, detection.box, color, max(1, round(frame_width / 900)))
                draw_text_box(frame, [
                    f"NIRIKSHAN | Zone {args.zone_id} | {format_video_time(timestamp_seconds)}",
                    f"People detected: {current_total_people}",
                    f"Density: {latest_density:.2f} people/m2",
                    f"Movement: {latest_speed:.2f} m/s",
                    f"Risk: {level} | trend",
                    "Confidence: solid=near | dashed=far corrected",
                ], (12, 168), color, font_scale=max(0.5, min(0.9, frame_width / 1800)))
                draw_trend_arrow(frame, latest_trend, min(frame_width - 30, 350), 150, color)
                cv2.putText(frame, f"Replay frame {frame_index}/{frame_count}", (12, frame_height - 14),
                            cv2.FONT_HERSHEY_SIMPLEX, 0.5, (230, 230, 230), 1, cv2.LINE_AA)
                writer.write(frame)
            frame_index += 1
            if args.loop:
                next_frame_due += 1.0 / max(fps, 1.0)
                time.sleep(max(0.0, next_frame_due - time.monotonic()))
    finally:
        if delivery_queue is not None:
            delivery_queue.close()
        capture.release()
        if writer is not None:
            writer.release()
        detection_log.close()

    if annotation_output is not None and raw_annotation_output is not None:
        transcode_annotation(raw_annotation_output, annotation_output)

    if args.debug_calibration and not debug_reported:
        print("CALIBRATION DEBUG: no non-empty detection sample was produced")

    Path(args.output).write_text(json.dumps(events, indent=2), encoding="utf-8")
    return events


class EventDeliveryQueue:
    """Bounded, retrying event delivery isolated from the camera loop."""

    def __init__(self, url: str, timeout: float) -> None:
        self.url = url
        self.timeout = timeout
        self.pending: queue.Queue[dict[str, Any] | None] = queue.Queue(maxsize=2)
        self.stop = threading.Event()
        self.thread = threading.Thread(target=self._run, daemon=True, name="risk-event-delivery")
        self.thread.start()

    def submit(self, event: dict[str, Any]) -> None:
        try:
            self.pending.put_nowait(event)
        except queue.Full:
            try:
                self.pending.get_nowait()
            except queue.Empty:
                pass
            try:
                self.pending.put_nowait(event)
                print(f"EVENT_DELIVERY_COALESCED zone={event['zoneId']} reason=backend_slow", flush=True)
            except queue.Full:
                print(f"EVENT_DELIVERY_DROPPED zone={event['zoneId']} reason=queue_full", file=sys.stderr, flush=True)

    def _run(self) -> None:
        while not self.stop.is_set():
            try:
                event = self.pending.get(timeout=0.25)
            except queue.Empty:
                continue
            if event is None:
                return
            # Live telemetry is latest-first. Do not block a zone for tens of
            # seconds retrying an obsolete event while newer readings queue up.
            for attempt in range(1, 2):
                request_started_at = time.perf_counter()
                try:
                    response = requests.post(self.url, json=event, timeout=self.timeout)
                    response.raise_for_status()
                    round_trip_ms = (time.perf_counter() - request_started_at) * 1000
                    print(
                        f"POST {response.status_code}: zone={event['zoneId']} level={event['riskLevel']} "
                        f"timestamp={event['timestamp']} roundTripMs={round_trip_ms:.0f} attempt={attempt}",
                        flush=True,
                    )
                    break
                except requests.RequestException as error:
                    round_trip_ms = (time.perf_counter() - request_started_at) * 1000
                    print(
                        f"EVENT_DELIVERY_FAILED zone={event['zoneId']} attempts={attempt} latest_first=true "
                        f"roundTripMs={round_trip_ms:.0f} error={error}",
                        file=sys.stderr,
                        flush=True,
                    )
            self.pending.task_done()

    def close(self) -> None:
        self.stop.set()
        try:
            self.pending.put_nowait(None)
        except queue.Full:
            pass
        self.thread.join(timeout=1.0)


def post_events(events: list[dict[str, Any]], url: str, timeout: float) -> None:
    for index, event in enumerate(events, start=1):
        response = requests.post(url, json=event, timeout=timeout)
        response.raise_for_status()
        print(f"Posted event {index}/{len(events)}: zone={event['zoneId']} level={event['riskLevel']}")


def post_event(event: dict[str, Any], url: str, timeout: float) -> None:
    try:
        response = requests.post(url, json=event, timeout=timeout)
        response.raise_for_status()
        print(f"POST {response.status_code}: zone={event['zoneId']} level={event['riskLevel']} timestamp={event['timestamp']}")
    except requests.RequestException as error:
        # A temporary backend/network failure must not stop the camera loop or
        # make the persisted upload look deleted. The next live event retries.
        print(f"Warning: risk event POST failed; keeping camera loop alive: {error}", file=sys.stderr, flush=True)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Process one CCTV camera/video as one backend zone")
    parser.add_argument("--input", required=True, help="Input crowd video path")
    parser.add_argument("--zone-id", required=True, type=int, help="Backend Zone ID represented by this camera/video")
    parser.add_argument("--thresholds", required=True, help="Risk-scoring and calibration configuration JSON file")
    parser.add_argument("--output", required=True, help="Output risk-event JSON path")
    parser.add_argument("--model", default="yolov8n.pt", help="Ultralytics model weights (default: yolov8n.pt; use a larger model only with sufficient memory)")
    parser.add_argument("--confidence", type=float, help="Override person confidence threshold for this run")
    parser.add_argument("--debug-calibration", action="store_true", help="Print area, raw count, perspective count, density, and risk for a sample frame")
    parser.add_argument("--compare-model", help="Optional baseline model for per-frame before/after count comparison")
    parser.add_argument("--detection-log", help="CSV path for per-frame detection counts; defaults beside --output")
    parser.add_argument("--annotate", action="store_true", help="Write an annotated MP4 alongside the JSON")
    parser.add_argument("--heatmap-overlay", action=argparse.BooleanOptionalAction, default=None, help="Enable/disable in-frame density heatmap; enabled by default with --annotate")
    parser.add_argument("--annotation-output", help="Explicit annotated MP4 path")
    parser.add_argument("--source-clip-id", help="Source clip ID; defaults to input filename stem")
    parser.add_argument("--start-time", help="Replay start timestamp in ISO 8601; defaults to current UTC time")
    parser.add_argument("--post-url", help="Backend risk-event URL; used with --post-live")
    parser.add_argument("--post-live", action="store_true", help="POST each generated event during processing")
    parser.add_argument("--loop", action="store_true", help="Run continuously, restart at EOF, and timestamp each event with current UTC time")
    parser.add_argument("--post-delay", type=float, default=0.0, help="Extra seconds added between live POSTs")
    parser.add_argument("--timeout", type=float, default=5.0, help="HTTP POST timeout in seconds (default: 5 for live latest-first delivery)")
    return parser.parse_args()


if __name__ == "__main__":
    process_args = parse_args()
    process_lock = ZoneProcessLock(process_args.zone_id)
    def stop_on_signal(_signum: int, _frame: Any) -> None:
        raise KeyboardInterrupt

    if hasattr(signal, "SIGTERM"):
        signal.signal(signal.SIGTERM, stop_on_signal)
    try:
        process_lock.acquire()
        process_video(process_args)
    except KeyboardInterrupt:
        print(f"Stopped zone worker cleanly: zone={process_args.zone_id}", flush=True)
    except (OSError, RuntimeError, ValueError, requests.RequestException) as error:
        print(f"Error: {error}", file=sys.stderr)
        raise SystemExit(1)
    finally:
        process_lock.release()
