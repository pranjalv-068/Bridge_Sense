import logging
from typing import Dict, Any, Optional
from backend.app.config import MAJOR_MAX
from backend.app.database.database import get_recent_telemetry

logger = logging.getLogger("BridgeSense.Forecast")

def calculate_forecast(node_id: str, current_error: float) -> Dict[str, Any]:
    """
    Fits a linear trend to the recent reconstruction errors of the node,
    and estimates the trend direction and ETA to critical threshold (MAJOR_MAX).
    """
    # 1. Fetch recent telemetry (up to 20 points)
    history = get_recent_telemetry(node_id, limit=20)
    
    # Extract reconstruction errors
    errors = [row["reconstruction_error"] for row in history if row.get("reconstruction_error") is not None]
    
    # If we don't have enough history, default to stable
    if len(errors) < 3:
        import math
        current_health = int(round(100 * math.exp(-current_error / 3.0)))
        return {
            "eta_hours": None,
            "trend": "Stable",
            "eta_days": None,
            "predicted_health": current_health
        }
    
    # Ensure current error is included as the latest point if it isn't already
    # (sometimes database write happens right after forecast or vice versa)
    if abs(errors[-1] - current_error) > 1e-5:
        errors.append(current_error)
        if len(errors) > 20:
            errors.pop(0)
            
    # 2. Fit a simple linear trend: y = slope * x + intercept
    # x is indices [0, 1, ..., n-1] representing time increments of 30 seconds
    n = len(errors)
    x = list(range(n))
    y = errors
    
    sum_x = sum(x)
    sum_y = sum(y)
    sum_xx = sum(val * val for val in x)
    sum_xy = sum(val_x * val_y for val_x, val_y in zip(x, y))
    
    denominator = (n * sum_xx - sum_x * sum_x)
    if denominator == 0:
        slope = 0.0
    else:
        slope = (n * sum_xy - sum_x * sum_y) / denominator
        
    # Standard sample interval is 30 seconds
    step_hours = 30.0 / 3600.0  # 30 seconds in hours
    
    # 3. Determine trend and ETA
    # Define a small threshold for slope to distinguish stable from moving
    slope_threshold = 0.0005 
    
    if slope > slope_threshold:
        trend = "Increasing"
        # Calculate how many steps until we reach MAJOR_MAX (0.880)
        if current_error >= MAJOR_MAX:
            eta_hours = 0.0
        else:
            steps_needed = (MAJOR_MAX - current_error) / slope
            eta_hours = max(0.1, steps_needed * step_hours)
            eta_hours = round(eta_hours, 1)
    elif slope < -slope_threshold:
        trend = "Decreasing"
        eta_hours = None
    else:
        trend = "Stable"
        eta_hours = None
        
    # 4. Predict health in 24 hours
    import math
    steps_in_24h = 24.0 / step_hours
    predicted_error_24h = max(0.0, current_error + slope * steps_in_24h)
    predicted_health = int(round(100 * math.exp(-predicted_error_24h / 3.0)))
    predicted_health = max(0, min(100, predicted_health))
    
    eta_days = round(eta_hours / 24.0, 2) if eta_hours is not None else None
        
    return {
        "eta_hours": eta_hours,
        "trend": trend,
        "eta_days": eta_days,
        "predicted_health": predicted_health
    }
