"""Executable end-to-end smoke test for the admin Scenario Simulator API.

The backend must be running and the supplied token must belong to an ADMIN.
It starts the real Scenario Controller, waits for the real subprocess run to
finish, then checks the normal risk-event, recommendation, and alert APIs.
"""

from __future__ import annotations

import argparse
import time
from typing import Any

import requests


def get_json(session: requests.Session, url: str) -> Any:
    response = session.get(url, timeout=10)
    response.raise_for_status()
    return response.json()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("scenario", choices=("buildup", "surge", "persistent_hotspot", "slowdown", "recovery", "normal_one_way", "slowing_flow", "reverse_movement", "conflicting_movement", "blocked_route", "alternate_exit_recovery", "stampede_precursor", "unusual_behavior"))
    parser.add_argument("--zone-id", type=int, required=True)
    parser.add_argument("--token", required=True, help="ADMIN JWT")
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--speed", type=float, default=20)
    parser.add_argument("--timeout", type=float, default=90)
    args = parser.parse_args()

    session = requests.Session()
    session.headers.update({"Authorization": f"Bearer {args.token}"})
    events_before = get_json(session, f"{args.base_url}/api/zones/{args.zone_id}/risk-events?limit=200")
    event_ids_before = {event.get("id") for event in events_before if event.get("id") is not None}
    response = session.post(f"{args.base_url}/api/admin/scenarios/run", json={"scenarioType": args.scenario, "zoneId": args.zone_id, "speed": args.speed}, timeout=10)
    response.raise_for_status()
    run = response.json()
    run_id = run["runId"]
    print(f"started run={run_id} scenario={args.scenario} zone={args.zone_id}")

    deadline = time.monotonic() + args.timeout
    observed_recommendations: list[dict[str, Any]] = []
    observed_alerts: list[dict[str, Any]] = []
    observed_forecasts: list[dict[str, Any]] = []
    while time.monotonic() < deadline:
        run = get_json(session, f"{args.base_url}/api/admin/scenarios/{run_id}/status")
        print(f"status={run['status']} message={run.get('message', '')}")
        active_recommendations = get_json(session, f"{args.base_url}/api/recommendations?active=true")
        active_alerts = get_json(session, f"{args.base_url}/api/alerts?active=true")
        forecast = get_json(session, f"{args.base_url}/api/zones/{args.zone_id}/risk-forecast")
        observed_forecasts.append(forecast)
        observed_recommendations.extend(item for item in active_recommendations if item.get("zoneId") == args.zone_id)
        observed_alerts.extend(item for item in active_alerts if item.get("zoneId") == args.zone_id)
        if run["status"] in {"COMPLETE", "STOPPED"}:
            break
        time.sleep(0.2)
    else:
        raise SystemExit("scenario did not finish before timeout")

    events = get_json(session, f"{args.base_url}/api/zones/{args.zone_id}/risk-events?limit=200")
    new_events = [event for event in events if event.get("id") not in event_ids_before]
    simulation_events = [event for event in new_events if event.get("source") == "SIMULATION"]
    if not simulation_events:
        raise SystemExit("this run did not ingest a SIMULATION risk event through the normal risk-event API")
    print(f"ingested simulation events={len(simulation_events)}")
    required_forecast_fields = {"currentRisk", "projectedRisk", "densityTrendPerMinute", "confidence", "state", "explanation", "source"}
    if not observed_forecasts or not required_forecast_fields.issubset(observed_forecasts[-1]):
        raise SystemExit("risk forecast API did not return the required predictive fields")
    analytical_by_telemetry: dict[tuple[Any, Any], tuple[Any, ...]] = {}
    analytical_fields = ("currentRisk", "projectedRisk", "confidence", "state", "projectedDensity", "estimatedSecondsToProjectedRisk", "explanation")
    for forecast in observed_forecasts:
        telemetry_key = (forecast.get("lastTelemetryAt"), forecast.get("source"))
        signature = tuple(forecast.get(field) for field in analytical_fields)
        previous_signature = analytical_by_telemetry.setdefault(telemetry_key, signature)
        if previous_signature != signature:
            raise SystemExit("forecast analytical values changed without a new telemetry identity")
    print(f"forecast API state={observed_forecasts[-1]['state']} projected={observed_forecasts[-1]['projectedRisk']}")

    zone = next(item for item in get_json(session, f"{args.base_url}/api/admin/zones") if item.get("id") == args.zone_id)
    if zone.get("currentRiskLevel") != "LOW":
        raise SystemExit("scenario cleanup did not restore the zone to LOW risk")
    if observed_forecasts[-1].get("source") == "SIMULATION":
        raise SystemExit("scenario cleanup left a SIMULATION forecast presented as current")

    recommendations = get_json(session, f"{args.base_url}/api/recommendations?active=true")
    alerts = get_json(session, f"{args.base_url}/api/alerts?active=true")
    # Completion cleanup dismisses/resolves the transient action, so inspect
    # the normal history endpoints too when proving that the action existed.
    recommendation_history = get_json(session, f"{args.base_url}/api/recommendations")
    alert_history = get_json(session, f"{args.base_url}/api/alerts")
    observed_recommendations.extend(item for item in recommendation_history if item.get("zoneId") == args.zone_id)
    observed_alerts.extend(item for item in alert_history if item.get("zoneId") == args.zone_id)
    zone_recommendations = [item for item in recommendations if item.get("zoneId") == args.zone_id]
    zone_alerts = [item for item in alerts if item.get("zoneId") == args.zone_id]
    print(f"active zone recommendations={len(zone_recommendations)} active zone alerts={len(zone_alerts)}")

    if args.scenario != "recovery" and not observed_recommendations:
        raise SystemExit("no active recommendation was observed while the scenario was running")
    if args.scenario in {"surge", "persistent_hotspot", "slowdown"} and not observed_alerts:
        raise SystemExit("no active alert was observed while the scenario was running")
    if any(item.get("source") != "SIMULATION" for item in observed_recommendations + observed_alerts):
        raise SystemExit("an observed scenario recommendation or alert was not labelled SIMULATION")

    if args.scenario == "recovery" and (zone_recommendations or zone_alerts):
        raise SystemExit("recovery left active recommendations or alerts behind")
    print("scenario smoke: PASS")


if __name__ == "__main__":
    main()
