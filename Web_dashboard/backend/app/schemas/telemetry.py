from pydantic import BaseModel
from typing import Optional

class TelemetryPayload(BaseModel):
    node_id: str
    timestamp: Optional[str] = None
    
    rms: Optional[float] = None
    stddev: Optional[float] = None
    peak: Optional[float] = None
    frequency: Optional[float] = None
    vibration_hz: Optional[float] = None
    crest_factor: Optional[float] = None
    
    tilt: Optional[float] = None
    tilt_deg: Optional[float] = None
    strain: Optional[float] = None
    strain_ue: Optional[float] = None
    
    temperature: Optional[float] = None
    temperature_c: Optional[float] = None
    
    reconstruction_error: Optional[float] = None
    health_index: Optional[int] = None
    edge_state: Optional[str] = None
    
    battery_pct: Optional[int] = None
    signal_dbm: Optional[int] = None

class FatiguePayload(BaseModel):
    fatigue_index: float
    timestamp: Optional[str] = None
    summary: Optional[str] = None
