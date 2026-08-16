# Nirikshan CV Pipeline

Standalone Python pipeline that treats each video as one CCTV camera covering one physical backend zone. It detects the crowd in the entire visible frame, estimates whole-frame density and movement, computes transparent risk events, and writes an annotated verification video.

There is no in-frame zone splitting. To simulate multiple venue zones, run the pipeline separately for each camera/clip with a different backend Zone ID. All runs can post events to the same Spring Boot backend.

## Setup

Python 3.10 is required for the supported local setup. From this directory, create and activate the project venv:

```bash
python -m venv venv
# Windows PowerShell
.\\venv\\Scripts\\Activate.ps1
python -m pip install --upgrade pip
# CUDA 12.1 PyTorch build (use this before installing the remaining packages)
pip install torch torchvision --index-url https://download.pytorch.org/whl/cu121
pip install -r requirements.txt
```

Set the backend and direct-run interpreter explicitly in Windows PowerShell:

```powershell
$env:NIRIKSHAN_PYTHON = "D:\\Nirikshan\\cv-pipeline\\venv\\Scripts\\python.exe"
[Environment]::SetEnvironmentVariable("NIRIKSHAN_PYTHON", "D:\\Nirikshan\\cv-pipeline\\venv\\Scripts\\python.exe", "User")
```

The pipeline prints `Using device: cuda (GPU name)` when CUDA is available and otherwise prints `Using device: cpu (no GPU detected)`. If `torch.cuda.is_available()` returns `False`, GPU inference is not available and processing falls back to slower CPU inference. The current pipeline uses Ultralytics full-frame inference with augmentation; there is no active SAHI sliced-prediction path. If SAHI is introduced later, pass this same resolved `device` to `get_sliced_prediction`.

The first run downloads the selected Ultralytics weights automatically. The default is `yolov8m.pt`, which generally recalls substantially more people than the nano model in dense scenes. Use `--model yolov8l.pt` when the machine has enough GPU/CPU capacity. Very dense or heavily occluded scenes may be better served by a dedicated crowd-counting model such as CSRNet, which can be evaluated later without changing the risk-scoring interface.

The default person confidence is `0.28`, configurable as `personConfidence` in `thresholds_config.json` or overridden per run with `--confidence 0.25`. `augment: true` enables Ultralytics test-time augmentation to improve small/distant-person recall; it can significantly increase processing time. Set it to `false` for faster processing.

## Camera-to-zone mapping

`zones_config.json` is only a bookkeeping reference and is not consumed by `process_video.py`. The authoritative zone mapping for each run is the required `--zone-id` CLI argument.

```json
[
  {"clipId": "main_gate", "zoneId": 1, "cameraLabel": "Main Gate camera", "zoneName": "Main Gate"},
  {"clipId": "hostel_25", "zoneId": 2, "cameraLabel": "Hostel 25 Gate camera", "zoneName": "Hostel 25 Gate"},
  {"clipId": "cafe", "zoneId": 3, "cameraLabel": "Cafeteria camera", "zoneName": "Cafeteria"},
  {"clipId": "a_block", "zoneId": 4, "cameraLabel": "A Block Entrance camera", "zoneName": "A Block Entrance"},
  {"clipId": "c_block", "zoneId": 5, "cameraLabel": "C Block Gate camera", "zoneName": "C Block Gate"},
  {"clipId": "c_block_2", "zoneId": 5, "cameraLabel": "C Block Gate camera 2", "zoneName": "C Block Gate"}
]
```

The whole visible frame is treated as the camera's physical zone. `thresholds_config.json` contains an explicit `zoneCalibration` entry for each backend zone. These initial visible-area figures are conservative starting calibrations for the actual camera views; refine them from site measurements or counted reference frames, never to artificially increase alert levels.

Campus route semantics are fixed to the site layout: Main Gate is the inbound entrance at `20.36366814775126, 85.81626264649513`, and the separate Main Gate Exit zone is the designated outbound exit at `20.36360968378996, 85.81631763177884`. The existing C Block Gate camera zone remains unchanged. Route recommendations therefore never send people to Main Gate as an exit and no longer invent synthetic Exit A/Exit B locations.

Each zone also has a `cameraHeadingDegrees` value: the compass direction represented by the top edge of that video's frame (`0` = N, `90` = E, `180` = S, `270` = W). The detector first measures movement in camera coordinates, then adds this heading so emitted `directionDegrees` and `dominantDirection` use the standard north-up map orientation. The current values are based on the north-up map arrows supplied for the five camera angles.

For front-facing footage, density uses a bounded perspective correction. Detection boxes with smaller pixel heights (usually further from the camera) receive a modestly higher contribution to the density calculation because they represent a larger real-world area. `peopleCount` remains the unmodified detector count, so dashboard headcount remains transparent and easy to audit.

The configured area is an **effective analyzed camera-view area**, not the entire venue footprint. For example, a gate camera may show an active 4m × 3m crowd window even when the wider venue is much larger; the cafeteria is configured with a larger 10m × 3m active footfall region. These are explicit prototype assumptions and should be revised per camera after a manual reference count. The density bands are calibrated around approximately 1–2 people/m² as comfortable, 4 people/m² as busy/uncomfortable, and 6+ people/m² as dangerous. Density bands are a minimum severity (so 4 people/m² cannot be labelled LOW), while the composite movement/rate-of-change score uses more conservative thresholds to avoid false HIGH/CRITICAL alerts from front-facing tracking noise.

This is an accessible prototype substitute for appropriately placed elevated CCTV. Density estimation is inherently more reliable with wide, elevated coverage, and this simple box-size correction cannot fully compensate for occlusion, lens distortion, or an unknown ground plane. This limitation is disclosed rather than hidden.

## Real campus footage runs

Store original footage under `real_footage/` and keep every generated asset in its matching `outputs/` folder. Both C Block clips represent Zone 5 from different camera angles.

| Zone | Video file | Zone ID | Command |
| --- | --- | ---: | --- |
| Main Gate | `real_footage/main_gate.mp4` | 1 | `python process_video.py --input real_footage/main_gate.mp4 --zone-id 1 --thresholds thresholds_config.json --output outputs/main_gate/events.json --annotate --annotation-output outputs/main_gate/annotated.mp4` |
| Hostel 25 Gate | `real_footage/hostel_25.mp4` | 2 | `python process_video.py --input real_footage/hostel_25.mp4 --zone-id 2 --thresholds thresholds_config.json --output outputs/hostel_25/events.json --annotate --annotation-output outputs/hostel_25/annotated.mp4` |
| Cafeteria | `real_footage/cafe.mp4` | 3 | `python process_video.py --input real_footage/cafe.mp4 --zone-id 3 --thresholds thresholds_config.json --output outputs/cafe/events.json --annotate --annotation-output outputs/cafe/annotated.mp4` |
| A Block Entrance | `real_footage/a_block.mp4` | 4 | `python process_video.py --input real_footage/a_block.mp4 --zone-id 4 --thresholds thresholds_config.json --output outputs/a_block/events.json --annotate --annotation-output outputs/a_block/annotated.mp4` |
| C Block Gate camera | `real_footage/c_block.mp4` | 5 | `python process_video.py --input real_footage/c_block.mp4 --zone-id 5 --thresholds thresholds_config.json --output outputs/c_block/events.json --annotate --annotation-output outputs/c_block/annotated.mp4` |
| C Block Gate camera 2 | `real_footage/c_block_2.mp4` | 5 | `python process_video.py --input real_footage/c_block_2.mp4 --zone-id 5 --thresholds thresholds_config.json --output outputs/c_block_2/events.json --annotate --annotation-output outputs/c_block_2/annotated.mp4` |

After any run, create the pitch-ready summary without processing the video again:

```bash
python generate_summary.py --events outputs/main_gate/events.json --output-dir outputs/main_gate/summary
```

Campus clips are expected to show LOW–MEDIUM crowd conditions in normal use. That is an honest result for calm campus activity—not a pipeline failure. Keep the risk thresholds evidence-based; use separately labelled escalation footage when demonstrating early-warning behaviour for severe crowd scenarios.

To sanity-check one sample frame before trusting a calibration, add `--debug-calibration`. It prints raw count, configured effective area, perspective-weighted count, density arithmetic, and resulting risk:

```bash
python process_video.py --input real_footage/hostel_25.mp4 --zone-id 2 --thresholds thresholds_config.json --output outputs/calibration_hostel_25/events.json --debug-calibration
```

Every processed frame is logged to a CSV beside the events file by default (for example `outputs/hostel_25/events_detection_counts.csv`) and is also printed to the console. To compare the new model directly with the previous nano model on the same frames:

```bash
python process_video.py --input real_footage/hostel_25.mp4 --zone-id 2 --thresholds thresholds_config.json --output outputs/hostel_25/events.json --model yolov8m.pt --compare-model yolov8n.pt --confidence 0.28
```

The comparison prints `before(yolov8n.pt)=... after(yolov8m.pt)=...` for each processed frame and records both counts in the CSV. Compare those values with a manually counted reference frame; the pipeline does not claim an accuracy improvement without that review.

The output contains one event per time window for the selected backend zone. To post events directly:

```bash
python process_video.py --input real_footage/main_gate.mp4 --zone-id 1 --thresholds thresholds_config.json --output outputs/main_gate/events.json --annotate --annotation-output outputs/main_gate/annotated.mp4 --post-url http://localhost:8080/api/risk-events
```

The annotated HUD shows the whole-frame timestamp, people count, smoothed density, movement speed, overall risk, and a five-second density trend. Bounding boxes and the frame border use the current overall risk color.

Annotated MP4s are automatically converted to browser-compatible H.264 with FFmpeg (`libx264`, `yuv420p`, fast-start metadata), so the result can be opened directly from the admin job page in Chrome. Install FFmpeg and ensure `ffmpeg.exe` is on PATH; without it, the pipeline keeps the OpenCV raw MP4 and prints a compatibility warning.

With `--annotate`, a semi-transparent Gaussian density heatmap is enabled by default. It uses detected person positions and the same blue/green/yellow/orange/red visual progression as the dashboard map. Disable it for a lighter annotation with `--no-heatmap-overlay`.

## Smoothing and risk configuration

`thresholds_config.json` contains the risk formula, calibration, and processing settings. `rollingAverageSeconds` is the T smoothing parameter applied to whole-frame density and speed before risk scoring and event output. There is no zone-hysteresis/N parameter because one video maps to one zone and there is no in-frame zone switching.

## Generate a summary report

After processing a clip, generate a reusable JSON summary, density/risk chart, and HTML report without re-running YOLO:

```bash
python generate_summary.py --events outputs/main_gate/events.json --output-dir outputs/main_gate/summary
```

This creates `summary.json`, `summary_chart.png`, and `summary_report.html`. The report is single-zone-aware and includes the clip's zone ID, peak risk, peak whole-frame density, risk-event counts, triggered HIGH/CRITICAL alerts, and time to first HIGH alert.

## Backend integration demo

1. Start the Spring Boot backend from the repository root:

   ```bash
   mvn spring-boot:run
   ```

2. Generate events locally without posting them:

   ```bash
   python process_video.py --input real_footage/main_gate.mp4 --zone-id 1 --thresholds thresholds_config.json --output outputs/main_gate/events.json
   ```

3. Replay the pre-generated events with their original timestamp gaps:

   ```bash
   python replay_events.py --events outputs/main_gate/events.json --speed 2
   ```

   `--speed 2` replays twice as fast. Use `--speed 1` for real clip timing.

4. Confirm that data landed:

   ```bash
   curl http://localhost:8080/api/health
   curl "http://localhost:8080/api/alerts?active=true"
   ```

The health response includes `status`, `totalZones`, `totalRiskEvents`, and `activeAlerts`.

For a direct live-style POST while the CV pipeline is generating events, use `--post-live`. Events are sent to the default backend URL, and `--post-delay N` adds N seconds between generated event posts:

```bash
python process_video.py --input real_footage/main_gate.mp4 --zone-id 1 --thresholds thresholds_config.json --output outputs/main_gate/events.json --post-live --post-delay 0.5
```

For the admin camera simulator, use `--loop`. It paces frames against the source FPS, reopens the file at EOF, emits approximately one event per real second, and timestamps each event with current UTC wall-clock time. Loop mode posts events immediately by default; it is the local-file equivalent of consuming an RTSP/CCTV source.

```powershell
python process_video.py --input real_footage/main_gate.mp4 --zone-id 1 --thresholds thresholds_config.json --output outputs/live/zone-1/events.json --loop --post-url http://localhost:8080/api/risk-events
```

## Output schema

Each JSON entry matches the backend request contract:

```json
{
  "zoneId": 1,
  "timestamp": "2026-08-08T08:23:43Z",
  "densityScore": 1.25,
  "peopleCount": 42,
  "movementSpeed": 0.55,
  "riskLevel": "HIGH",
  "explanation": "Density reached 1.25 people/m2, a 65% increase over the lookback window, with average movement speed at 0.55 m/s (40% below baseline)",
  "sourceClipId": "main_gate"
}
```

`movementSpeed` is an approximate nearest-centroid estimate, not a calibrated pedestrian trajectory. `densityScore` depends on the per-zone visible-area and perspective calibration and should be validated for each camera.
