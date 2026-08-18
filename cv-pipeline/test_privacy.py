import unittest

import cv2
import numpy as np

from privacy import FaceBlurProcessor, PrivacyProcessingError


class PrivacyProcessorTest(unittest.TestCase):
    class Detector:
        def detectMultiScale(self, gray, scaleFactor, minNeighbors, minSize):
            return ()

    class FaceDetector:
        def detectMultiScale(self, gray, scaleFactor, minNeighbors, minSize):
            return ((20, 20, 40, 40),)

    def test_sanitizes_frame_without_changing_dimensions(self):
        frame = np.zeros((120, 160, 3), dtype=np.uint8)
        result = FaceBlurProcessor(detector=self.Detector()).sanitize(frame)
        self.assertEqual(result.frame.shape, frame.shape)
        self.assertEqual(result.frame.dtype, frame.dtype)

    def test_detector_failure_is_fail_closed(self):
        processor = FaceBlurProcessor(detector=self.Detector())
        processor.detector = type("BrokenDetector", (), {"detectMultiScale": lambda *args, **kwargs: (_ for _ in ()).throw(RuntimeError("detector failed"))})()
        with self.assertRaises(PrivacyProcessingError):
            processor.sanitize(np.zeros((40, 40, 3), dtype=np.uint8))

    def test_detected_face_pixels_are_blurred(self):
        frame = np.zeros((100, 100, 3), dtype=np.uint8)
        frame[20:60, 20:60] = np.indices((40, 40))[0][:, :, None] * 6
        result = FaceBlurProcessor(detector=self.FaceDetector()).sanitize(frame)
        self.assertEqual(result.faces_detected, 1)
        self.assertFalse(np.array_equal(result.frame[20:60, 20:60], frame[20:60, 20:60]))

    def test_empty_frame_is_rejected(self):
        with self.assertRaises(PrivacyProcessingError):
            FaceBlurProcessor(detector=self.Detector()).sanitize(np.empty((0, 0, 3), dtype=np.uint8))


if __name__ == "__main__":
    unittest.main()
