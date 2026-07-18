import os
import json
import time
import logging
from typing import Dict, Any, List
from fastapi import APIRouter, WebSocket, WebSocketDisconnect, HTTPException
from backend.app.schemas.telemetry import TelemetryPayload, FatiguePayload
from backend.app.database.database import (
    insert_telemetry, 
    insert_fatigue, 
    get_recent_telemetry, 
    get_all_latest_telemetry,
    get_recent_fatigue
)
from backend.app.services.severity import calculate_severity
from backend.app.services.forecast import calculate_forecast
from backend.app.services.llm import get_llm_explanation
from backend.app.websocket.manager import manager
from backend.app.config import PROJECT_DIR

logger = logging.getLogger("BridgeSense.API")
router = APIRouter()

RAW_TEMPLATE_PATH = PROJECT_DIR / "src" / "data" / "rawSensorData.json"
PROCESSED_TEMPLATE_PATH = PROJECT_DIR / "src" / "data" / "npuProcessedData.json"

def normalize_node_id(node_id: str) -> str:
    """Normalizes N1/N3 to NODE_01/NODE_03 formatting to match static templates."""
    if node_id.startswith("N") and not node_id.startswith("NODE_"):
        try:
            num = int(node_id[1:])
            return f"NODE_{num:02d}"
        except ValueError:
            pass
    return node_id

def load_template(path: str) -> Dict[str, Any]:
    if os.path.exists(path):
        with open(path, "r") as f:
            return json.load(f)
    logger.error(f"Template not found at {path}")
    return {}

@router.get("/api/raw-data")
def get_raw_data():
    """Constructs raw telemetry data by combining static layout with SQLite history."""
    template = load_template(RAW_TEMPLATE_PATH)
    if not template:
        raise HTTPException(status_code=500, detail="Raw data template missing")
        
    latest_db_records = get_all_latest_telemetry()
    if not latest_db_records:
        return template  # Return static data if DB is empty
        
    # Update nodes with latest metrics from DB
    active_alerts = []
    nodes = template.get("nodes", [])
    
    for node in nodes:
        node_id = node["id"]
        if node_id in latest_db_records:
            record = latest_db_records[node_id]
            
            severity_level = record["severity"]
            status = "good"
            if severity_level == "Critical":
                status = "critical"
            elif severity_level in ["Minor", "Major"]:
                status = "warning"
                
            node["status"] = status
            
            if record["battery_pct"] is not None:
                node["battery"] = record["battery_pct"]
                node["battery_pct"] = record["battery_pct"]
            if record["signal_dbm"] is not None:
                node["signal"] = record["signal_dbm"]
                node["signal_dbm"] = record["signal_dbm"]
                
            node["latest"] = {
                "vibration_hz": round(record["frequency"] or 0.0, 2),
                "tilt_deg": round(record["tilt"] or 0.0, 3),
                "strain_ue": round(record["strain"] or 0.0, 1)
            }
            
            history = get_recent_telemetry(node_id, limit=20)
            node["history"] = {
                "vibration_hz": [round(h["frequency"] or 0.0, 2) for h in history],
                "tilt_deg": [round(h["tilt"] or 0.0, 3) for h in history],
                "strain_ue": [round(h["strain"] or 0.0, 1) for h in history]
            }
            
            if status == "critical":
                active_alerts.append({
                    "id": f"ALERT-{record['id']}",
                    "severity": "critical",
                    "node_id": node_id,
                    "message": f"Critical Structural Anomaly: high reconstruction error ({record['reconstruction_error']:.3f})",
                    "bridge": template.get("bridge", {}).get("name", "Morbi Bridge"),
                    "sector": template.get("bridge", {}).get("sector", "Sector Alpha"),
                    "timestamp": record["timestamp"]
                })
                
    active_nodes = [n for n in nodes if n.get("operating", True)]
    if active_nodes:
        cluster = template.setdefault("cluster_telemetry", {
            "timestamps": [], "vibration_hz": [], "tilt_deg": [], "strain_ue": []
        })
        
        avg_vib = sum(n["latest"].get("vibration_hz", 0.0) for n in active_nodes) / len(active_nodes)
        avg_tilt = sum(n["latest"].get("tilt_deg", 0.0) for n in active_nodes) / len(active_nodes)
        avg_strain = sum(n["latest"].get("strain_ue", 0.0) for n in active_nodes) / len(active_nodes)
        
        cluster["vibration_avg_current"] = round(avg_vib, 2)
        cluster["tilt_avg_current"] = round(avg_tilt, 3)
        cluster["strain_avg_current"] = round(avg_strain, 1)
        
        # Pull history timeline of averages
        # For simplicity, we align to the last 20 timestamps available in the database
        first_node = active_nodes[0]["id"]
        node_history = get_recent_telemetry(first_node, limit=20)
        
        cluster["timestamps"] = [h["timestamp"].split("T")[-1][:8] for h in node_history]
        
        # Calculate historical averages step by step
        hist_vib, hist_tilt, hist_strain = [], [], []
        history_by_node = {n["id"]: get_recent_telemetry(n["id"], limit=20) for n in active_nodes}
        
        min_len = min(len(h) for h in history_by_node.values())
        for i in range(min_len):
            step_vib = sum(history_by_node[n["id"]][i]["frequency"] or 0.0 for n in active_nodes) / len(active_nodes)
            step_tilt = sum(history_by_node[n["id"]][i]["tilt"] or 0.0 for n in active_nodes) / len(active_nodes)
            step_strain = sum(history_by_node[n["id"]][i]["strain"] or 0.0 for n in active_nodes) / len(active_nodes)
            
            hist_vib.append(round(step_vib, 2))
            hist_tilt.append(round(step_tilt, 3))
            hist_strain.append(round(step_strain, 1))
            
        cluster["vibration_hz"] = hist_vib
        cluster["tilt_deg"] = hist_tilt
        cluster["strain_ue"] = hist_strain
        
    template["active_alert"] = active_alerts[0] if active_alerts else None
    return template

@router.get("/api/processed-data")
def get_processed_data():
    """Constructs processed metrics and inferences by merging template and SQLite."""
    template = load_template(PROCESSED_TEMPLATE_PATH)
    if not template:
        raise HTTPException(status_code=500, detail="Processed data template missing")
        
    latest_db_records = get_all_latest_telemetry()
    if not latest_db_records:
        return template
        
    node_inferences = template.get("node_inferences", [])
    updated_inferences = []
    
    for inf in node_inferences:
        node_id = inf["node_id"]
        if node_id in latest_db_records:
            record = latest_db_records[node_id]
            
            # Map statuses
            severity_level = record["severity"]
            status = "good"
            classification = "nominal"
            if severity_level == "Critical":
                status = "critical"
                classification = "anomaly"
            elif severity_level in ["Minor", "Major"]:
                status = "warning"
                classification = "warning"
                
            inf["status"] = status
            inf["classification"] = classification
            inf["anomaly_score"] = round(record["reconstruction_error"] or 0.0, 3)
            inf["confidence"] = round((record["confidence"] or 95.0) / 100.0, 2)
            inf["explanation"] = record["llm_summary"] or "Normal operation."
            inf["severity_level"] = severity_level
            inf["forecast_eta"] = record["forecast_eta"]
            inf["forecast_trend"] = record["forecast_trend"]
            
        updated_inferences.append(inf)
        
    template["node_inferences"] = updated_inferences
    
    fatigue_history = get_recent_fatigue(limit=24)
    if fatigue_history:
        fatigue = template.setdefault("fatigue_analysis", {})
        fatigue["hours_actual"] = [h["timestamp"].split("T")[-1][:5] for h in fatigue_history]
        fatigue["actual_measurement"] = [round(h["fatigue_index"], 2) for h in fatigue_history]
        
        fatigue["predictive_maintenance_trend"] = [round(h["fatigue_index"] * 0.98, 2) for h in fatigue_history]
        last_entry = fatigue_history[-1]
        last_val = last_entry["fatigue_index"]
        last_time_str = last_entry["timestamp"].split("T")[-1][:5]
        
        try:
            last_h = int(last_time_str.split(":")[0])
        except ValueError:
            last_h = 12
            
        hours_forecast, forecast_projection = [], []
        for i in range(1, 7):
            next_h = (last_h + i) % 24
            hours_forecast.append(f"{next_h:02d}:00")
            forecast_projection.append(round(last_val * (1.0 + i * 0.005), 2))
            
        fatigue["hours_forecast"] = hours_forecast
        fatigue["forecast_projection"] = forecast_projection
        fatigue["summary"] = last_entry["summary"] or "Fatigue accumulation is within nominal margins."
        
    return template

async def process_incoming_telemetry(payload: TelemetryPayload) -> Dict[str, Any]:
    """Helper pipeline to analyze, forecast, and generate explanation for telemetry."""
    node_id = normalize_node_id(payload.node_id)
    timestamp = payload.timestamp or time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
    
    rms = payload.rms or 0.1
    stddev = payload.stddev or 0.01
    peak = payload.peak or 0.2
    freq = payload.frequency or payload.vibration_hz or 1.2
    crest = payload.crest_factor or 1.5
    tilt = payload.tilt or payload.tilt_deg or 0.01
    strain = payload.strain or payload.strain_ue or 120.0
    temp = payload.temperature or payload.temperature_c or 25.0
    
    recon_err = payload.reconstruction_error
    if recon_err is None:
        recon_err = round((freq / 5.0) * 0.4 + (strain / 500.0) * 0.6, 3)
        
    health_idx = payload.health_index
    if health_idx is None:
        import math
        health_idx = int(100 * math.exp(-recon_err / 3.0))
        
    edge_state = payload.edge_state or ("Watch" if recon_err > 0.312 else "Normal")
    
    # 1. Severity Engine
    severity_data = calculate_severity(recon_err)
    severity_level = severity_data["level"]
    confidence = severity_data["confidence"]
    
    # 2. Forecasting Engine
    forecast_data = calculate_forecast(node_id, recon_err)
    eta_hours = forecast_data["eta_hours"]
    trend = forecast_data["trend"]
    
    # 3. LLM Service
    llm_summary = get_llm_explanation(
        node_id=node_id,
        severity=severity_level,
        error=recon_err,
        vibration=freq,
        strain=strain,
        trend=trend,
        eta_hours=eta_hours
    )
    
    record = {
        "node_id": node_id,
        "timestamp": timestamp,
        "rms": rms,
        "stddev": stddev,
        "peak": peak,
        "frequency": freq,
        "crest_factor": crest,
        "tilt": tilt,
        "strain": strain,
        "temperature": temp,
        "reconstruction_error": recon_err,
        "health_index": health_idx,
        "edge_state": edge_state,
        "severity": severity_level,
        "confidence": confidence,
        "forecast_eta": eta_hours,
        "forecast_trend": trend,
        "llm_summary": llm_summary,
        "battery_pct": payload.battery_pct or 100,
        "signal_dbm": payload.signal_dbm or -50
    }
    
    insert_telemetry(record)
    
    return record

@router.post("/api/node-data")
@router.post("/telemetry")
async def receive_telemetry(payload: TelemetryPayload):
    """Handles incoming telemetry payloads from edge nodes."""
    try:
        record = await process_incoming_telemetry(payload)
        

        await manager.broadcast({
            "type": "update",
            "raw_data": raw_data,
            "processed_data": processed_data
        })
        
        return {"status": "success", "message": f"Telemetry processed for node {record['node_id']}"}
    except Exception as e:
        logger.error(f"Error processing telemetry POST: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/api/fatigue-data")
@router.post("/fatigue")
async def receive_fatigue(payload: FatiguePayload):
    """Handles incoming fatigue analytics payloads."""
    timestamp = payload.timestamp or time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
    summary = payload.summary or f"Composite fatigue index at {payload.fatigue_index:.1f}%."
    
    try:
        insert_fatigue(payload.fatigue_index, timestamp, summary)
        
        raw_data = get_raw_data()
        processed_data = get_processed_data()
        
        await manager.broadcast({
            "type": "update",
            "raw_data": raw_data,
            "processed_data": processed_data
        })
        
        return {"status": "success", "message": "Fatigue analytics telemetry updated"}
    except Exception as e:
        logger.error(f"Error processing fatigue POST: {e}")
        raise HTTPException(status_code=500, detail=str(e))
