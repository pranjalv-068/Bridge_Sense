import math
import time
import logging
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
from backend.app.api.telemetry import router as api_router, get_raw_data, get_processed_data, load_template, RAW_TEMPLATE_PATH, PROCESSED_TEMPLATE_PATH
from backend.app.database.database import init_db, get_db_connection, insert_telemetry, insert_fatigue
from backend.app.services.severity import calculate_severity
from backend.app.services.llm import generate_fallback_explanation
from backend.app.websocket.manager import manager

logger = logging.getLogger("BridgeSense.Main")
logging.basicConfig(level=logging.INFO)

app = FastAPI(title="BridgeSense AI PC Backend", version="2.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(api_router)

def seed_database_if_empty():
    """Seeds historical data from static JSON templates if SQLite database is empty."""
    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute("SELECT COUNT(*) FROM telemetry")
    count = cursor.fetchone()[0]
    conn.close()
    
    if count > 0:
        return
        
    logger.info("Database empty. Seeding historical data from static JSON templates...")
    try:
        raw_data = load_template(str(RAW_TEMPLATE_PATH))
        processed_data = load_template(str(PROCESSED_TEMPLATE_PATH))
        
        nodes = raw_data.get("nodes", [])
        for node in nodes:
            node_id = node["id"]
            battery = node.get("battery", 80)
            signal = node.get("signal", -50)
            history = node.get("history", {})
            vibs = history.get("vibration_hz", [1.0] * 20)
            tilts = history.get("tilt_deg", [0.0] * 20)
            strains = history.get("strain_ue", [120.0] * 20)
            
            n_history = len(vibs)
            for i in range(n_history):
                # Calculate historical timestamp offsets back from now (30s intervals)
                offset_seconds = (n_history - 1 - i) * 30
                timestamp_epoch = time.time() - offset_seconds
                timestamp_str = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(timestamp_epoch))
                
                vib = vibs[i]
                tilt = tilts[i]
                strain = strains[i]
                
                recon_err = round((vib / 5.0) * 0.4 + (strain / 500.0) * 0.6, 3)
                
                from backend.app.services.severity import compute_severity, load_calibration
                calibration = load_calibration(node_id)
                severity_res = compute_severity(
                    reconstruction_error=recon_err,
                    temperature=25.0,
                    frequency=vib,
                    health_trend=0.0,
                    calibration=calibration
                )
                severity_level = severity_res.level
                confidence = round(severity_res.confidence * 100.0, 1)
                severity_score = severity_res.score
                
                llm_summary = generate_fallback_explanation(
                    node_id, severity_level, recon_err, vib, strain, "Stable", None
                )
                
                record = {
                    "node_id": node_id,
                    "timestamp": timestamp_str,
                    "rms": round(vib * 0.1, 3),
                    "stddev": round(vib * 0.01, 3),
                    "peak": round(vib * 0.2, 3),
                    "frequency": vib,
                    "crest_factor": 1.5,
                    "tilt": tilt,
                    "strain": strain,
                    "temperature": 25.0,
                    "reconstruction_error": recon_err,
                    "health_index": int(100 * math.exp(-recon_err / 3.0)),
                    "edge_state": "Alert" if recon_err >= 1.00 else ("Watch" if recon_err >= 0.45 else "Normal"),
                    "severity": severity_level,
                    "confidence": confidence,
                    "forecast_eta": None,
                    "forecast_trend": "Stable",
                    "llm_summary": llm_summary,
                    "battery_pct": battery,
                    "signal_dbm": signal,
                    "severity_score": severity_score
                }
                insert_telemetry(record)
                
        fatigue = processed_data.get("fatigue_analysis", {})
        actuals = fatigue.get("actual_measurement", [])
        
        n_fatigue = len(actuals)
        for i in range(n_fatigue):
            val = actuals[i]
            offset_hours = n_fatigue - 1 - i
            timestamp_epoch = time.time() - (offset_hours * 3600)
            timestamp_str = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(timestamp_epoch))
            
            insert_fatigue(val, timestamp_str, f"Composite fatigue index at {val:.1f}%.")
            
        logger.info("Database seeding completed successfully.")
    except Exception as e:
        logger.error(f"Error seeding database: {e}")

def check_and_update_schema():
    """Checks if telemetry database schema matches latest structure, deleting it if outdated to trigger re-creation."""
    from backend.app.config import DB_PATH
    import sqlite3
    if not DB_PATH.exists():
        return
    try:
        conn = sqlite3.connect(str(DB_PATH))
        cursor = conn.cursor()
        cursor.execute("PRAGMA table_info(telemetry)")
        columns = [row[1] for row in cursor.fetchall()]
        conn.close()
        if "severity_score" not in columns:
            logger.info("Database schema update required. Deleting old bridgesense.db database...")
            DB_PATH.unlink()
            logger.info("Old database file deleted.")
    except Exception as ex:
        logger.error(f"Error checking database schema: {ex}")

@app.on_event("startup")
def startup_event():
    check_and_update_schema()
    init_db()
    seed_database_if_empty()
    logger.info("FastAPI backend startup complete.")

@app.websocket("/ws/telemetry")
async def websocket_endpoint(websocket: WebSocket):
    """WebSocket connection handler for streaming real-time data updates directly to the frontend."""
    await manager.connect(websocket)
    try:
        # Push initial data state immediately on connection
        raw_data = get_raw_data()
        processed_data = get_processed_data()
        
        await websocket.send_json({
            "type": "update",
            "raw_data": raw_data,
            "processed_data": processed_data
        })
        
        while True:
            await websocket.receive_text()
            
    except WebSocketDisconnect:
        manager.disconnect(websocket)
    except Exception as e:
        logger.error(f"WebSocket client error: {e}")
        manager.disconnect(websocket)


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
