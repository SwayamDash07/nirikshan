"""Run one independent local YOLO process for each configured video zone.

The supervisor owns no model itself. Every child executes the existing
``process_video.py`` loop, which loads its own YOLO model, applies privacy and
the existing risk/event pipeline, and POSTs aggregate events to the selected
Spring Boot backend.

Manifest entries are objects with ``zoneId``, ``input`` and optionally
``sourceClipId``/``enabled``. Relative input paths are resolved from the
``cv-pipeline`` directory, then from the manifest directory.
"""

from __future__ import annotations

import argparse
import json
import os
import signal
import subprocess
import sys
import threading
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any


PIPELINE_DIR = Path(__file__).resolve().parent


@dataclass
class Worker:
    zone_id: int
    source: str
    process: subprocess.Popen[str]


def load_manifest(path: Path) -> list[dict[str, Any]]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as error:
        raise SystemExit(f"Manifest not found: {path}") from error
    except json.JSONDecodeError as error:
        raise SystemExit(f"Manifest is not valid JSON: {path}: {error}") from error
    if isinstance(value, dict):
        value = value.get("zones", [])
    if not isinstance(value, list):
        raise SystemExit("Manifest must contain a JSON array or an object with a 'zones' array")
    return [entry for entry in value if isinstance(entry, dict)]


def resolve_input(raw: str, manifest_path: Path) -> Path:
    candidate = Path(raw)
    if candidate.is_absolute():
        return candidate.resolve()
    pipeline_candidate = (PIPELINE_DIR / candidate).resolve()
    if pipeline_candidate.exists():
        return pipeline_candidate
    return (manifest_path.parent / candidate).resolve()


def desired_workers(manifest_path: Path, entries: list[dict[str, Any]], limit: int) -> dict[int, tuple[str, Path, str]]:
    desired: dict[int, tuple[str, Path, str]] = {}
    for entry in entries:
        if entry.get("enabled", True) is False:
            continue
        if "zoneId" not in entry or not entry.get("input"):
            print(f"MANIFEST_WARNING skipped entry without zoneId/input: {entry}", flush=True)
            continue
        zone_id = int(entry["zoneId"])
        if zone_id in desired:
            raise SystemExit(f"Duplicate zoneId={zone_id} in manifest; refusing duplicate local worker")
        source = resolve_input(str(entry["input"]), manifest_path)
        if not source.exists():
            print(f"ZONE_DISABLED zone={zone_id} source_missing={source}", flush=True)
            continue
        clip_id = str(entry.get("sourceClipId") or source.stem)
        desired[zone_id] = (str(source), source, clip_id)

    selected = dict(sorted(desired.items())[:limit])
    for zone_id in sorted(set(desired) - set(selected)):
        print(f"ZONE_SKIPPED zone={zone_id} reason=worker_limit limit={limit}", flush=True)
    return selected


def command_for(zone_id: int, input_path: Path, source_clip_id: str, args: argparse.Namespace, output_dir: Path) -> list[str]:
    output = output_dir / f"zone-{zone_id}-events.json"
    return [
        sys.executable,
        str(PIPELINE_DIR / "process_video.py"),
        "--input", str(input_path),
        "--zone-id", str(zone_id),
        "--thresholds", str(Path(args.thresholds).resolve()),
        "--output", str(output),
        "--model", args.model,
        "--post-url", args.target_url,
        "--post-live", "--loop",
        "--source-clip-id", source_clip_id,
        "--timeout", str(args.timeout),
    ]


def terminate_worker(worker: Worker, reason: str) -> None:
    if worker.process.poll() is not None:
        return
    print(f"ZONE_WORKER_STOPPING zone={worker.zone_id} pid={worker.process.pid} reason={reason}", flush=True)
    worker.process.terminate()
    try:
        worker.process.wait(timeout=8)
    except subprocess.TimeoutExpired:
        print(f"ZONE_WORKER_FORCE_STOP zone={worker.zone_id} pid={worker.process.pid}", flush=True)
        worker.process.kill()
        worker.process.wait(timeout=5)


def start_worker(zone_id: int, source: str, source_path: Path, clip_id: str, args: argparse.Namespace, output_dir: Path) -> Worker:
    environment = os.environ.copy()
    if not args.allow_cpu:
        environment["NIRIKSHAN_REQUIRE_CUDA"] = "1"
    process = subprocess.Popen(
        command_for(zone_id, source_path, clip_id, args, output_dir),
        cwd=str(PIPELINE_DIR),
        env=environment,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        bufsize=1,
    )
    print(f"ZONE_WORKER_STARTED zone={zone_id} pid={process.pid} source={source} target={args.target_url}", flush=True)
    worker = Worker(zone_id, source, process)
    threading.Thread(target=log_output, args=(worker,), daemon=True, name=f"zone-{zone_id}-log").start()
    return worker


def log_output(worker: Worker) -> None:
    if worker.process.stdout is None:
        return
    for line in worker.process.stdout:
        line = line.rstrip()
        if line:
            print(f"ZONE_WORKER zone={worker.zone_id} pid={worker.process.pid} | {line}", flush=True)


def run(args: argparse.Namespace) -> int:
    manifest_path = Path(args.manifest).resolve()
    output_dir = (PIPELINE_DIR / "outputs" / "live").resolve()
    output_dir.mkdir(parents=True, exist_ok=True)
    workers: dict[int, Worker] = {}
    failed_sources: dict[int, str] = {}
    stopping = False

    def request_stop(_signum: int, _frame: Any) -> None:
        nonlocal stopping
        stopping = True

    signal.signal(signal.SIGINT, request_stop)
    if hasattr(signal, "SIGTERM"):
        signal.signal(signal.SIGTERM, request_stop)

    print(
        f"LOCAL_CV_RUNNER_STARTED manifest={manifest_path} workers={args.workers} "
        f"model={args.model} target={args.target_url} gpu_required={not args.allow_cpu}",
        flush=True,
    )
    try:
        while not stopping:
            entries = load_manifest(manifest_path)
            desired = desired_workers(manifest_path, entries, args.workers)

            for zone_id, worker in list(workers.items()):
                selected = desired.get(zone_id)
                if selected is None or selected[0] != worker.source:
                    terminate_worker(worker, "zone disabled or source changed")
                    del workers[zone_id]
                    failed_sources.pop(zone_id, None)
                    continue
                exit_code = worker.process.poll()
                if exit_code is not None:
                    print(
                        f"ZONE_WORKER_FAILED zone={zone_id} pid={worker.process.pid} "
                        f"exit_code={exit_code}; no automatic restart (inspect GPU/VRAM/model settings)",
                        flush=True,
                    )
                    failed_sources[zone_id] = worker.source
                    del workers[zone_id]

            for zone_id, (source, source_path, clip_id) in desired.items():
                if zone_id in workers or failed_sources.get(zone_id) == source:
                    continue
                workers[zone_id] = start_worker(zone_id, source, source_path, clip_id, args, output_dir)

            time.sleep(max(0.5, args.manifest_poll_seconds))
    finally:
        for worker in workers.values():
            terminate_worker(worker, "runner shutdown")
        print(f"LOCAL_CV_RUNNER_STOPPED workers={len(workers)}", flush=True)
    return 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run independent local YOLO workers, one per zone")
    parser.add_argument("--manifest", default="outputs/live/zones.json", help="Zone manifest JSON (default: outputs/live/zones.json)")
    parser.add_argument("--target-url", "--url", dest="target_url", default="http://localhost:8080/api/risk-events", help="Risk-event POST target")
    parser.add_argument("--workers", type=int, default=6, help="Maximum concurrent zone processes (default: 6)")
    parser.add_argument("--thresholds", default=str(PIPELINE_DIR / "thresholds_config.json"), help="Threshold/calibration JSON")
    parser.add_argument("--model", default="yolo26s.pt", help="Independent YOLO weights loaded by every zone process")
    parser.add_argument("--timeout", type=float, default=10.0, help="Risk-event POST timeout")
    parser.add_argument("--manifest-poll-seconds", type=float, default=2.0, help="Manifest/source reconciliation interval")
    parser.add_argument("--allow-cpu", action="store_true", help="Allow local CPU inference; GPU is required by default")
    args = parser.parse_args()
    if args.workers <= 0:
        raise SystemExit("--workers must be greater than zero")
    if args.timeout <= 0 or args.manifest_poll_seconds <= 0:
        raise SystemExit("--timeout and --manifest-poll-seconds must be greater than zero")
    return args


if __name__ == "__main__":
    raise SystemExit(run(parse_args()))
