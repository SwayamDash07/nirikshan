import unittest

from risk_scoring import calculate_zone_risk
from signal_utils import (
    BehaviorStateTracker,
    FlowSignalSmoother,
    behavior_candidate,
    bottleneck_detected,
    detect_hotspots_from_centroids,
    detect_hotspots_from_counts,
    derive_signal_values,
    estimate_flow_direction,
    estimate_flow_direction_from_vectors,
    rotate_flow_direction,
    sustained_pattern,
    temporal_reverse_ratio,
)


CONFIG = {
    "normalSpeedMps": 1.4,
    "densityThresholds": {"medium": 1.5, "high": 4, "critical": 6},
    "densityIncreaseThresholds": {"medium": .2, "high": .5, "critical": 1},
    "speedDropThresholds": {"medium": .2, "high": .4, "critical": .6},
    "riskScoreThresholds": {"medium": .2, "high": .65, "critical": .85},
    "riskWeights": {"density": .45, "densityIncrease": .3, "speedDrop": .25},
}


class SignalTests(unittest.TestCase):
    def test_risk_is_explainable_and_deterministic(self):
        result = calculate_zone_risk(4.2, .55, .6, CONFIG)
        self.assertEqual(result.level, "HIGH")
        self.assertIn("increase", result.explanation)
        self.assertEqual(result, calculate_zone_risk(4.2, .55, .6, CONFIG))

    def test_hotspot_and_bottleneck(self):
        self.assertTrue(detect_hotspots_from_counts([[0, 0, 0], [0, 8, 0], [0, 0, 0]]))
        self.assertEqual(detect_hotspots_from_centroids([(50, 50), (51, 52), (52, 51)], 100, 100), [{"gridPosition": "2,2", "relativeDensity": 9.0}])
        self.assertTrue(bottleneck_detected([True] * 8 + [False, True], [.3] * 10, 2, 3))

    def test_signal_derivation_is_the_production_formula(self):
        self.assertEqual(derive_signal_values(3, 2, .5, 1), (0.5, .5))

    def test_sustained_pattern_and_recovery(self):
        self.assertTrue(sustained_pattern(["MEDIUM"] * 4, [1, 1.2, 1.5, 1.7], 20))
        self.assertFalse(sustained_pattern(["LOW"] * 4, [.3, .3, .3, .3], 20))

    def test_direction_estimation_and_insufficient_data(self):
        estimate = estimate_flow_direction([(10, 10), (20, 10), (30, 10)], [(15, 10), (25, 10), (35, 10)])
        self.assertEqual(estimate["dominantDirection"], "E")
        self.assertGreater(estimate["directionConfidence"], .8)
        insufficient = estimate_flow_direction([(10, 10)], [(15, 10)])
        self.assertEqual(insufficient["state"], "INSUFFICIENT_DATA")

    def test_reverse_conflicting_and_behavior_hysteresis(self):
        reverse = estimate_flow_direction_from_vectors([(-10, 0)] * 4)
        self.assertEqual(reverse["dominantDirection"], "W")
        conflict = estimate_flow_direction_from_vectors([(10, 0), (10, 0), (10, 0), (0, -10)])
        self.assertGreaterEqual(conflict["conflictingMovementRatio"], .25)
        self.assertEqual(temporal_reverse_ratio(90, 270), 1.0)
        tracker = BehaviorStateTracker()
        flow = {"state": "OK", "reverseMovementRatio": 0.0, "conflictingMovementRatio": 0.0}
        self.assertEqual(tracker.update(behavior_candidate(flow, 1.0, None), 0), "INSUFFICIENT_DATA")
        self.assertEqual(tracker.update("REVERSE_FLOW", 5), "INSUFFICIENT_DATA")
        self.assertEqual(tracker.update("REVERSE_FLOW", 10), "INSUFFICIENT_DATA")
        self.assertEqual(tracker.update("REVERSE_FLOW", 15), "REVERSE_FLOW")

    def test_flow_smoother_ignores_small_direction_jitter(self):
        smoother = FlowSignalSmoother(window_samples=5, min_valid_samples=2)
        for index, direction in enumerate((88, 92, 90, 94, 89)):
            result = smoother.update({
                "state": "OK",
                "trackedPeople": 4,
                "directionDegrees": direction,
                "dominantDirection": "E",
                "directionConfidence": .85,
                "directionalConsistency": .85,
                "reverseMovementRatio": 0,
                "conflictingMovementRatio": 0,
            })
        self.assertEqual(result["state"], "OK")
        self.assertEqual(result["dominantDirection"], "E")
        self.assertGreater(result["directionConfidence"], .75)

    def test_flow_smoother_rejects_conflicting_recent_directions(self):
        smoother = FlowSignalSmoother(window_samples=3, min_valid_samples=2)
        result = None
        for direction in (0, 180, 0):
            result = smoother.update({
                "state": "OK",
                "trackedPeople": 3,
                "directionDegrees": direction,
                "dominantDirection": "N",
                "directionConfidence": .9,
                "directionalConsistency": .9,
                "reverseMovementRatio": 0,
                "conflictingMovementRatio": 0,
            })
        self.assertEqual(result["state"], "INSUFFICIENT_DATA")

    def test_camera_heading_rotates_video_direction_to_map_direction(self):
        flow = estimate_flow_direction_from_vectors([(0, -10)] * 4)
        rotated = rotate_flow_direction(flow, 135)
        self.assertEqual(rotated["directionDegrees"], 135.0)
        self.assertEqual(rotated["dominantDirection"], "SE")

    def test_camera_heading_preserves_insufficient_direction(self):
        flow = estimate_flow_direction_from_vectors([])
        self.assertEqual(rotate_flow_direction(flow, 180), flow)


if __name__ == "__main__":
    unittest.main()
