"""Local privacy smoke test for a sample frame/video.

Usage: python smoke_privacy.py --input sample.mp4 --output sanitized-smoke.mp4
"""

from __future__ import annotations

import argparse
from pathlib import Path

import cv2

from privacy import FaceBlurProcessor


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    capture = cv2.VideoCapture(args.input)
    if not capture.isOpened():
        raise SystemExit(f"Could not open sample video: {args.input}")
    processor = FaceBlurProcessor()
    fps = capture.get(cv2.CAP_PROP_FPS) or 25.0
    width, height = int(capture.get(cv2.CAP_PROP_FRAME_WIDTH)), int(capture.get(cv2.CAP_PROP_FRAME_HEIGHT))
    writer = cv2.VideoWriter(args.output, cv2.VideoWriter_fourcc(*"mp4v"), fps, (width, height))
    if not writer.isOpened():
        raise SystemExit(f"Could not open smoke output: {args.output}")
    frames = 0
    try:
        while True:
            ok, frame = capture.read()
            if not ok:
                break
            writer.write(processor.sanitize(frame).frame)
            frames += 1
    finally:
        capture.release(); writer.release()
    if frames == 0:
        Path(args.output).unlink(missing_ok=True)
        raise SystemExit("Privacy smoke test read no frames")
    print(f"Privacy smoke test passed: sanitized {frames} frames to {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
