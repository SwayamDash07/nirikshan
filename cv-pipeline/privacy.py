"""Local, fail-closed face blurring for every camera frame."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable

import cv2
import numpy as np


class PrivacyProcessingError(RuntimeError):
    """Raised when a frame cannot be privacy-sanitized safely."""


@dataclass(frozen=True)
class PrivacyResult:
    frame: Any
    faces_detected: int


class FaceBlurProcessor:
    """Blur detected faces using OpenCV's bundled local Haar cascade.

    A frame with no detected faces is valid; detector or frame failures are not.
    The caller must use the returned frame for all display and persistence.
    """

    def __init__(self, cascade_path: str | Path | None = None, detector: Any | None = None) -> None:
        path = str(cascade_path or Path(cv2.data.haarcascades) / "haarcascade_frontalface_default.xml")
        if detector is not None:
            self.detector = detector
        elif not hasattr(cv2, "CascadeClassifier"):
            raise PrivacyProcessingError("PRIVACY_PROCESSING_FAILED: OpenCV face detector is unavailable; install opencv-contrib-python<5")
        else:
            self.detector = cv2.CascadeClassifier(path)
        if hasattr(self.detector, "empty") and self.detector.empty():
            raise PrivacyProcessingError(f"PRIVACY_PROCESSING_FAILED: face detector unavailable at {path}")

    def sanitize(self, frame: Any, protected_regions: Iterable[tuple[int, int, int, int]] | None = None) -> PrivacyResult:
        if frame is None or not hasattr(frame, "shape") or frame.size == 0:
            raise PrivacyProcessingError("PRIVACY_PROCESSING_FAILED: empty camera frame")
        try:
            gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
            faces = self.detector.detectMultiScale(gray, scaleFactor=1.1, minNeighbors=5, minSize=(24, 24))
        except Exception as error:
            raise PrivacyProcessingError(f"PRIVACY_PROCESSING_FAILED: face detection error: {error}") from error
        safe = frame.copy()
        regions = [(int(x), int(y), int(width), int(height)) for x, y, width, height in faces]
        regions.extend((int(left), int(top), int(right - left), int(bottom - top)) for left, top, right, bottom in (protected_regions or []))
        for x, y, width, height in regions:
            padding_x = max(2, round(width * 0.35))
            padding_top = max(2, round(height * 0.25))
            padding_bottom = max(2, round(height * 0.45))
            left, top = max(0, x - padding_x), max(0, y - padding_top)
            right = min(safe.shape[1], x + width + padding_x)
            bottom = min(safe.shape[0], y + height + padding_bottom)
            if right <= left or bottom <= top:
                continue
            roi = safe[top:bottom, left:right]
            kernel = max(21, ((min(roi.shape[:2]) // 2) * 2) + 1)
            safe[top:bottom, left:right] = cv2.GaussianBlur(roi, (kernel, kernel), 0)
        return PrivacyResult(safe, len(faces))


def sanitize_frame(frame: Any, processor: FaceBlurProcessor | None = None) -> Any:
    return (processor or FaceBlurProcessor()).sanitize(frame).frame
