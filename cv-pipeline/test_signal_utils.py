import unittest

from risk_scoring import calculate_zone_risk
from signal_utils import bottleneck_detected, detect_hotspots_from_centroids, detect_hotspots_from_counts, derive_signal_values, sustained_pattern


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


if __name__ == "__main__":
    unittest.main()
