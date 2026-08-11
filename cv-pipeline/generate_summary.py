"""Generate a pitch-friendly summary JSON, chart, and HTML report from events.json.

Example:
    python generate_summary.py --events outputs/clip_events.json --output-dir outputs/
"""

from __future__ import annotations

import argparse
import base64
import html
import json
from collections import Counter
from datetime import datetime
from pathlib import Path
from typing import Any

import matplotlib.pyplot as plt


RISK_ORDER = {"LOW": 0, "MEDIUM": 1, "HIGH": 2, "CRITICAL": 3}
RISK_COLORS = {"LOW": "#3cb44b", "MEDIUM": "#f5c542", "HIGH": "#f58231", "CRITICAL": "#e6194b"}


def parse_timestamp(value: str) -> datetime:
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


def format_duration(seconds: float) -> str:
    total = max(0, round(seconds))
    return f"{total // 60:02d}:{total % 60:02d}"


def prepare_events(raw_events: list[dict[str, Any]]) -> tuple[list[dict[str, Any]], datetime]:
    if not raw_events:
        raise ValueError("events file contains no risk events")
    ordered = sorted(raw_events, key=lambda event: parse_timestamp(event["timestamp"]))
    zone_ids = {event["zoneId"] for event in ordered}
    if len(zone_ids) != 1:
        raise ValueError(f"single-zone summary expected exactly one zoneId, found: {sorted(zone_ids)}")
    start = parse_timestamp(ordered[0]["timestamp"])
    prepared = []
    for event in ordered:
        enriched = dict(event)
        enriched["secondsIntoClip"] = round((parse_timestamp(event["timestamp"]) - start).total_seconds(), 3)
        prepared.append(enriched)
    return prepared, start


def build_summary(events: list[dict[str, Any]], start: datetime) -> dict[str, Any]:
    duration = max(event["secondsIntoClip"] for event in events)
    peak_event = max(events, key=lambda event: (RISK_ORDER.get(event["riskLevel"], 0), event["densityScore"]))
    peak_density_event = max(events, key=lambda event: event["densityScore"])

    counts = Counter(event["riskLevel"] for event in events)
    first_high = next((event for event in events if RISK_ORDER.get(event["riskLevel"], 0) >= RISK_ORDER["HIGH"]), None)
    alerts = [
        {
            "timestamp": event["timestamp"],
            "secondsIntoClip": event["secondsIntoClip"],
            "zoneId": event["zoneId"],
            "riskLevel": event["riskLevel"],
            "explanation": event["explanation"],
        }
        for event in events
        if RISK_ORDER.get(event["riskLevel"], 0) >= RISK_ORDER["HIGH"]
    ]
    return {
        "clipSourceId": events[0].get("sourceClipId", "unknown"),
        "zoneId": events[0]["zoneId"],
        "analysisStartTimestamp": start.isoformat().replace("+00:00", "Z"),
        "totalDurationAnalyzedSeconds": duration,
        "totalDurationAnalyzed": format_duration(duration),
        "peakRiskLevel": peak_event["riskLevel"],
        "peakRiskTimestamp": peak_event["timestamp"],
        "peakRiskSecondsIntoClip": peak_event["secondsIntoClip"],
        "peakDensity": peak_density_event["densityScore"],
        "peakDensityTimestamp": peak_density_event["timestamp"],
        "peakDensitySecondsIntoClip": peak_density_event["secondsIntoClip"],
        "riskEventCounts": {level: counts.get(level, 0) for level in ("LOW", "MEDIUM", "HIGH", "CRITICAL")},
        "timeToFirstHighAlertSeconds": first_high["secondsIntoClip"] if first_high else None,
        "timeToFirstHighAlert": format_duration(first_high["secondsIntoClip"]) if first_high else None,
        "alerts": alerts,
    }


def make_chart(events: list[dict[str, Any]], summary: dict[str, Any], output_path: Path) -> None:
    fig, ax = plt.subplots(figsize=(12, 6.5), constrained_layout=True)
    ax.plot(
        [event["secondsIntoClip"] for event in events],
        [event["densityScore"] for event in events],
        marker="o", linewidth=2, markersize=4, label=f"Zone {summary['zoneId']}",
    )

    risk_by_time: dict[float, str] = {}
    for event in events:
        current = risk_by_time.get(event["secondsIntoClip"], "LOW")
        if RISK_ORDER.get(event["riskLevel"], 0) > RISK_ORDER.get(current, 0):
            risk_by_time[event["secondsIntoClip"]] = event["riskLevel"]
    times = sorted(risk_by_time)
    for index, time in enumerate(times):
        end = times[index + 1] if index + 1 < len(times) else max(time + 1.0, summary["totalDurationAnalyzedSeconds"])
        ax.axvspan(time, end, color=RISK_COLORS[risk_by_time[time]], alpha=0.10, linewidth=0)

    ax.set_title(
        f"Nirikshan Single-Zone Crowd Risk Analytics — {summary['clipSourceId']}\n"
        f"Peak risk: {summary['peakRiskLevel']} at {summary['peakRiskSecondsIntoClip']:.1f}s",
        fontsize=15, pad=14,
    )
    ax.set_xlabel("Time into clip (seconds)")
    ax.set_ylabel("Density (people/m²)")
    ax.grid(True, alpha=0.25)
    ax.legend(loc="upper left", frameon=True)
    fig.text(0.99, 0.02, "Background bands: LOW / MEDIUM / HIGH / CRITICAL", ha="right", fontsize=9, color="#555555")
    fig.savefig(output_path, dpi=160, bbox_inches="tight")
    plt.close(fig)


def render_html(summary: dict[str, Any], chart_path: Path, output_path: Path) -> None:
    chart_data = base64.b64encode(chart_path.read_bytes()).decode("ascii")
    peak_cards = (
        f"<div class='card'><span>Zone {summary['zoneId']}</span>"
        f"<strong>{summary['peakDensity']:.2f} people/m²</strong>"
        f"<small>{summary['peakDensitySecondsIntoClip']:.1f}s</small></div>"
    )
    counts = "".join(
        f"<div class='count {level.lower()}'><span>{level}</span><strong>{count}</strong></div>"
        for level, count in summary["riskEventCounts"].items()
    )
    alert_rows = "".join(
        f"<tr><td>{alert['secondsIntoClip']:.1f}s</td><td>Zone {html.escape(str(alert['zoneId']))}</td>"
        f"<td><b class='{alert['riskLevel'].lower()}'>{alert['riskLevel']}</b></td>"
        f"<td>{html.escape(alert['explanation'])}</td></tr>"
        for alert in summary["alerts"]
    ) or "<tr><td colspan='4'>No HIGH or CRITICAL alerts were triggered.</td></tr>"
    headline = summary["timeToFirstHighAlert"] or "No HIGH alert"
    json_block = html.escape(json.dumps(summary, indent=2))
    document = f"""<!doctype html>
<html><head><meta charset="utf-8"><title>Nirikshan Summary — {html.escape(summary["clipSourceId"])}</title>
<style>
body{{font-family:Arial,sans-serif;max-width:1200px;margin:32px auto;padding:0 24px;color:#17202a;background:#f5f7fa}}
h1{{margin-bottom:4px}} .subtitle{{color:#667085}} .headline{{background:#14213d;color:#fff;border-radius:12px;padding:20px 24px;margin:24px 0;display:flex;gap:48px;align-items:center}} .headline strong{{font-size:30px;color:#ffd166}}
.grid{{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin:16px 0}} .card,.count{{background:white;padding:16px;border-radius:10px;box-shadow:0 1px 4px #0001}} .card span,.card small,.count span{{display:block;color:#667085}} .card strong{{display:block;font-size:20px;margin:8px 0}} .counts{{display:flex;gap:12px;flex-wrap:wrap}} .count{{min-width:105px}} .count strong{{display:block;font-size:25px;margin-top:6px}} .low{{color:#2f9e44}} .medium{{color:#b7791f}} .high{{color:#e67700}} .critical{{color:#c92a2a}}
img{{width:100%;background:#fff;border-radius:10px;margin:18px 0}} table{{width:100%;border-collapse:collapse;background:#fff;border-radius:10px;overflow:hidden}} th,td{{padding:11px;text-align:left;border-bottom:1px solid #eee;vertical-align:top}} th{{background:#14213d;color:#fff}} pre{{background:#17202a;color:#e6edf3;padding:18px;border-radius:10px;overflow:auto}}
</style></head><body>
<h1>Nirikshan Crowd Risk Summary</h1><div class="subtitle">Clip: {html.escape(summary["clipSourceId"])} · Analyzed: {summary["totalDurationAnalyzed"]}</div>
<div class="headline"><div><span>Peak risk</span><strong class="{summary["peakRiskLevel"].lower()}">{summary["peakRiskLevel"]}</strong><br><small>at {summary["peakRiskSecondsIntoClip"]:.1f}s</small></div><div><span>Early warning headline</span><strong>{html.escape(headline)}</strong><br><small>time to first HIGH/CRITICAL event</small></div></div>
<h2>Peak density for this camera zone</h2><div class="grid">{peak_cards}</div>
<h2>Risk event counts</h2><div class="counts">{counts}</div>
<h2>Density and risk timeline</h2><img src="data:image/png;base64,{chart_data}" alt="Density and risk timeline chart">
<h2>Triggered alerts</h2><table><thead><tr><th>Time</th><th>Zone</th><th>Level</th><th>Explanation</th></tr></thead><tbody>{alert_rows}</tbody></table>
<h2>Summary JSON</h2><pre>{json_block}</pre>
</body></html>"""
    output_path.write_text(document, encoding="utf-8")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Generate JSON, chart, and HTML summary from Nirikshan events")
    parser.add_argument("--events", required=True, help="events.json generated by process_video.py")
    parser.add_argument("--output-dir", required=True, help="Directory for summary.json, summary_chart.png, and summary_report.html")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    raw = json.loads(Path(args.events).read_text(encoding="utf-8"))
    events, start = prepare_events(raw)
    summary = build_summary(events, start)
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    (output_dir / "summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
    make_chart(events, summary, output_dir / "summary_chart.png")
    render_html(summary, output_dir / "summary_chart.png", output_dir / "summary_report.html")
    print(f"Wrote {output_dir / 'summary.json'}")
    print(f"Wrote {output_dir / 'summary_chart.png'}")
    print(f"Wrote {output_dir / 'summary_report.html'}")


if __name__ == "__main__":
    main()
