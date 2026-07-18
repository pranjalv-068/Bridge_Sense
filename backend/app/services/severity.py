from backend.app.config import HEALTHY_MAX, WARNING_MAX, MAJOR_MAX

def calculate_severity(reconstruction_error: float) -> dict:
    """
    Determines severity level and confidence based on the reconstruction error 
    and calibrated thresholds.
    """
    # Deterministic confidence based on distance to threshold boundaries
    # Closer to boundaries = slightly lower confidence, far from boundaries = higher confidence
    if reconstruction_error <= HEALTHY_MAX:
        level = "Normal"
        dist = HEALTHY_MAX - reconstruction_error
        confidence = min(99.0, 90.0 + (dist / HEALTHY_MAX) * 9.0)
    elif reconstruction_error <= WARNING_MAX:
        level = "Minor"
        # Middle of the range is highest confidence
        mid = (HEALTHY_MAX + WARNING_MAX) / 2
        dist = abs(reconstruction_error - mid)
        span = (WARNING_MAX - HEALTHY_MAX) / 2
        confidence = min(98.0, 92.0 + (1.0 - (dist / span)) * 6.0)
    elif reconstruction_error <= MAJOR_MAX:
        level = "Major"
        mid = (WARNING_MAX + MAJOR_MAX) / 2
        dist = abs(reconstruction_error - mid)
        span = (MAJOR_MAX - WARNING_MAX) / 2
        confidence = min(97.0, 91.0 + (1.0 - (dist / span)) * 6.0)
    else:
        level = "Critical"
        dist = reconstruction_error - MAJOR_MAX
        confidence = min(99.0, 93.0 + min(6.0, dist * 5.0))
        
    return {
        "level": level,
        "confidence": round(confidence, 1)
    }
