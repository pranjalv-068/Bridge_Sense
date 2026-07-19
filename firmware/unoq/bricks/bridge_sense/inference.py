import math
import os
from edge_impulse_linux.runner import ImpulseRunner

MEAN = [0.250417, 0.035011, 0.500787, 8.077152, 1.999820, 0.020836, 193.574581, 30.237696]
STD  = [0.012107, 0.002061, 0.031381, 0.040102, 0.080097, 0.002218, 10.351989, 5.571147]

NORMAL_MAX_ERROR = 0.45
WATCH_MAX_ERROR = 1.00
HEALTH_INDEX_SCALE = 3.0


class BridgeSenseModel:
    def __init__(self):
        eim_path = os.environ.get("EIM_PATH")
        if not eim_path:
            raise RuntimeError("EIM_PATH variable not set — check app.yaml")
        self.runner = ImpulseRunner(eim_path)
        info = self.runner.init()
        print("Loaded model:", info.get("project", {}).get("name"))

    def scale(self, raw: list[float]) -> list[float]:
        return [(raw[i] - MEAN[i]) / STD[i] for i in range(8)]

    def _extract_output(self, result: dict) -> list[float]:
        return result["result"]["freeform"][0]

    def infer(self, raw_features: list[float]) -> dict:
        scaled = self.scale(raw_features)
        result = self.runner.classify(scaled)
        reconstructed = self._extract_output(result)

        error = sum((scaled[i] - reconstructed[i]) ** 2 for i in range(8)) / 8
        health_index = max(0.0, min(100.0, 100.0 * math.exp(-error / HEALTH_INDEX_SCALE)))

        if error < NORMAL_MAX_ERROR:
            edge_state = "Normal"
        elif error < WATCH_MAX_ERROR:
            edge_state = "Watch"
        else:
            edge_state = "Alert"

        return {
            "reconstruction_error": round(error, 4),
            "health_index": round(health_index, 1),
            "edge_state": edge_state,
        }

    def close(self):
        self.runner.stop()