import json
from dataclasses import dataclass
from pathlib import Path
from backend.app.config import BACKEND_DIR

CALIBRATION_PATH = BACKEND_DIR / "config" / "calibration.json"

@dataclass
class SeverityResult:
    level: str      # Normal | Minor | Major | Critical
    score: float     # 0-100
    confidence: float

def load_calibration(node_id: str, path: Path = CALIBRATION_PATH) -> dict:
    if path.exists():
        try:
            with open(path, "r") as f:
                data = json.load(f)
                from backend.app.api.telemetry import normalize_node_id
                norm_id = normalize_node_id(node_id)
                if norm_id in data:
                    return data[norm_id]
                if node_id in data:
                    return data[node_id]
                return data.get("default")
        except Exception:
            pass
    return {
        "baseline_temperature": 30.0,
        "baseline_frequency": 8.0,
        "severity_thresholds": { "normal": 25, "minor": 50, "major": 75 }
    }

def compute_severity(
    reconstruction_error: float,
    temperature: float,
    frequency: float,
    health_trend: float,   # e.g. slope of last N health_index values, negative = degrading
    calibration: dict,
) -> SeverityResult:
    baseline_temperature = calibration["baseline_temperature"]
    baseline_frequency = calibration["baseline_frequency"]
    thresholds = calibration["severity_thresholds"]

    # Normalize each factor to a 0-1 range before weighting
    error_factor = min(reconstruction_error / 1.0, 1.0)  # 1.0 ~= your 'major' threshold
    temp_factor = min(abs(temperature - baseline_temperature) / 20.0, 1.0)
    freq_factor = min(abs(frequency - baseline_frequency) / 4.0, 1.0)
    trend_factor = min(max(-health_trend, 0) / 5.0, 1.0)  # only penalize degrading trend

    score = (
        40 * error_factor
        + 20 * temp_factor
        + 20 * freq_factor
        + 20 * trend_factor
    )

    if score < thresholds["normal"]:
        level = "Normal"
    elif score < thresholds["minor"]:
        level = "Minor"
    elif score < thresholds["major"]:
        level = "Major"
    else:
        level = "Critical"

    # Heuristic reliability/confidence score (0.7 to 1.0)
    confidence = 0.7 + 0.3 * error_factor

    return SeverityResult(level=level, score=round(score, 1), confidence=round(confidence, 3))

def calculate_severity(reconstruction_error: float) -> dict:
    """
    Legacy wrapper for startup seeding/fallback, using default calibration.
    """
    calibration = {
        "baseline_temperature": 30.0,
        "baseline_frequency": 8.0,
        "severity_thresholds": { "normal": 25, "minor": 50, "major": 75 }
    }
    res = compute_severity(
        reconstruction_error=reconstruction_error,
        temperature=25.0,
        frequency=8.0,
        health_trend=0.0,
        calibration=calibration
    )
    return {
        "level": res.level,
        "confidence": round(res.confidence * 100.0, 1)  # old UI expects confidence in 0-100 range
    }
