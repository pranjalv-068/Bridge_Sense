import sqlite3
import logging
from typing import List, Dict, Any
from backend.app.config import DB_PATH

logger = logging.getLogger("BridgeSense.Database")

def get_db_connection():
    conn = sqlite3.connect(str(DB_PATH))
    conn.row_factory = sqlite3.Row
    return conn

def init_db():
    """Initializes SQLite database tables."""
    conn = get_db_connection()
    cursor = conn.cursor()
    
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS telemetry (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            node_id TEXT NOT NULL,
            timestamp TEXT NOT NULL,
            rms REAL,
            stddev REAL,
            peak REAL,
            frequency REAL,
            crest_factor REAL,
            tilt REAL,
            strain REAL,
            temperature REAL,
            reconstruction_error REAL,
            health_index INTEGER,
            edge_state TEXT,
            severity TEXT,
            confidence REAL,
            forecast_eta REAL,
            forecast_trend TEXT,
            llm_summary TEXT,
            battery_pct INTEGER,
            signal_dbm INTEGER,
            severity_score REAL
        )
    """)
    
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS fatigue (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            timestamp TEXT NOT NULL,
            fatigue_index REAL NOT NULL,
            summary TEXT
        )
    """)
    
    conn.commit()
    conn.close()
    logger.info(f"Database initialized at {DB_PATH}")

def insert_telemetry(data: Dict[str, Any]) -> int:
    """Inserts a new telemetry record and returns the inserted ID."""
    conn = get_db_connection()
    cursor = conn.cursor()
    
    query = """
        INSERT INTO telemetry (
            node_id, timestamp, rms, stddev, peak, frequency, crest_factor,
            tilt, strain, temperature, reconstruction_error, health_index,
            edge_state, severity, confidence, forecast_eta, forecast_trend, 
            llm_summary, battery_pct, signal_dbm, severity_score
        ) VALUES (
            :node_id, :timestamp, :rms, :stddev, :peak, :frequency, :crest_factor,
            :tilt, :strain, :temperature, :reconstruction_error, :health_index,
            :edge_state, :severity, :confidence, :forecast_eta, :forecast_trend, 
            :llm_summary, :battery_pct, :signal_dbm, :severity_score
        )
    """
    
    cursor.execute(query, data)
    inserted_id = cursor.lastrowid
    conn.commit()
    conn.close()
    return inserted_id

def get_recent_telemetry(node_id: str, limit: int = 50) -> List[Dict[str, Any]]:
    """Retrieves recent telemetry records for a specific node, ordered by timestamp ascending."""
    conn = get_db_connection()
    cursor = conn.cursor()
    
    cursor.execute("""
        SELECT * FROM telemetry 
        WHERE node_id = ? 
        ORDER BY id DESC 
        LIMIT ?
    """, (node_id, limit))
    
    rows = cursor.fetchall()
    conn.close()
    
    return [dict(row) for row in reversed(rows)]

def get_all_latest_telemetry() -> Dict[str, Dict[str, Any]]:
    """Gets the single latest telemetry record for each unique node."""
    conn = get_db_connection()
    cursor = conn.cursor()
    
    # Retrieve the row with the max id for each node
    cursor.execute("""
        SELECT t1.* FROM telemetry t1
        INNER JOIN (
            SELECT node_id, MAX(id) as max_id FROM telemetry GROUP BY node_id
        ) t2 ON t1.id = t2.max_id
    """)
    
    rows = cursor.fetchall()
    conn.close()
    return {row["node_id"]: dict(row) for row in rows}

def insert_fatigue(fatigue_index: float, timestamp: str, summary: str = "") -> int:
    """Inserts a new fatigue analysis record."""
    conn = get_db_connection()
    cursor = conn.cursor()
    
    cursor.execute("""
        INSERT INTO fatigue (timestamp, fatigue_index, summary)
        VALUES (?, ?, ?)
    """, (timestamp, fatigue_index, summary))
    
    inserted_id = cursor.lastrowid
    conn.commit()
    conn.close()
    return inserted_id

def get_recent_fatigue(limit: int = 24) -> List[Dict[str, Any]]:
    """Retrieves recent fatigue entries, ordered by timestamp ascending."""
    conn = get_db_connection()
    cursor = conn.cursor()
    
    cursor.execute("""
        SELECT * FROM fatigue
        ORDER BY id DESC
        LIMIT ?
    """, (limit,))
    
    rows = cursor.fetchall()
    conn.close()
    return [dict(row) for row in reversed(rows)]
