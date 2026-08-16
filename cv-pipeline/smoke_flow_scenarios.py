"""Offline smoke test for deterministic flow/route simulator fixtures."""

from replay_scenarios import SCENARIOS, generate_scenario


FLOW_SCENARIOS = {
    "normal_one_way": "NORMAL_FLOW",
    "slowing_flow": "SLOWING_FLOW",
    "reverse_movement": "REVERSE_FLOW",
    "conflicting_movement": "CONFLICTING_FLOW",
    "blocked_route": "SLOWING_FLOW",
    "alternate_exit_recovery": "NORMAL_FLOW",
}


def main() -> None:
    for scenario, expected_state in FLOW_SCENARIOS.items():
        events = generate_scenario(scenario, zone_id=1)
        assert events and all(event["source"] == "SIMULATION" for event in events)
        assert all(event["behaviorState"] == expected_state for event in events)
        assert all("dominantDirection" in event and "directionConfidence" in event for event in events)
    print(f"flow scenario smoke: PASS ({len(FLOW_SCENARIOS)} labelled scenarios)")


if __name__ == "__main__":
    main()
