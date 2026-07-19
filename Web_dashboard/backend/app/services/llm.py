import urllib.request
import json
import logging
from backend.app.config import GEMINI_API_KEY

logger = logging.getLogger("BridgeSense.LLM")

def call_gemini_api(prompt: str) -> str:
    """Queries the Gemini 2.5 Flash model directly via REST API using urllib."""
    url = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key={GEMINI_API_KEY}"
    headers = {"Content-Type": "application/json"}
    payload = {
        "contents": [{
            "parts": [{"text": prompt}]
        }],
        "generationConfig": {
            "maxOutputTokens": 100,
            "temperature": 0.2
        }
    }
    
    try:
        req = urllib.request.Request(
            url, 
            data=json.dumps(payload).encode("utf-8"), 
            headers=headers, 
            method="POST"
        )
        with urllib.request.urlopen(req, timeout=5) as response:
            res_data = json.loads(response.read().decode("utf-8"))
            text = res_data["candidates"][0]["content"]["parts"][0]["text"]
            return text.strip()
    except Exception as e:
        logger.error(f"Gemini API request failed: {e}. Falling back to rules-based summary.")
        return ""

def get_llm_explanation(
    node_id: str,
    severity: str,
    error: float,
    vibration: float,
    strain: float,
    trend: str,
    eta_hours: float = None
) -> str:
    """
    Generates a professional explanation of the structural status.
    Uses Gemini API if available, otherwise falls back to a rules-based generator.
    """
    forecast_text = f"Critical threshold expected in {eta_hours} hours" if eta_hours is not None else "No threshold violation expected in the near term"
    
    # Construct the grounding prompt
    prompt = (
        f"You are a structural health monitoring AI assistant for the BridgeSense platform.\n"
        f"Generate a professional, concise summary and action recommendation based on these facts (EXACTLY two short sentences):\n"
        f"- Node: {node_id}\n"
        f"- Severity level: {severity}\n"
        f"- Reconstruction Error: {error:.3f}\n"
        f"- Vibration: {vibration:.2f} Hz\n"
        f"- Strain: {strain:.1f} µε\n"
        f"- Trend: {trend}\n"
        f"- Forecast: {forecast_text}\n"
        f"Do not invent any additional facts. Be precise, calm, and actionable."
    )
    
    # 1. If GEMINI_API_KEY is configured, try querying the live LLM
    if GEMINI_API_KEY:
        explanation = call_gemini_api(prompt)
        if explanation:
            return explanation
            
    # 2. Fallback: High-quality rule-based explanation
    return generate_fallback_explanation(node_id, severity, error, vibration, strain, trend, eta_hours)

def generate_fallback_explanation(
    node_id: str,
    severity: str,
    error: float,
    vibration: float,
    strain: float,
    trend: str,
    eta_hours: float = None
) -> str:
    """Generates a professional template-based explanation of the structural anomaly."""
    # Determine the status description
    if severity == "Normal":
        return f"Node {node_id} is operating within normal baseline limits with a stable reconstruction error. No actions are required at this time."
    
    anomaly_parts = []
    if vibration > 2.0:
        anomaly_parts.append(f"elevated vibration frequencies ({vibration:.2f} Hz)")
    if strain > 250.0:
        anomaly_parts.append(f"significant tensile strain ({strain:.1f} µε)")
    if not anomaly_parts:
        anomaly_parts.append("mild sensor correlation anomalies")
        
    anomalies_str = " and ".join(anomaly_parts)
    
    if severity == "Minor":
        return (
            f"Node {node_id} shows early signs of drift characterized by {anomalies_str}. "
            f"Routine maintenance inspection should be scheduled during the next normal cycle."
        )
    elif severity == "Major":
        eta_str = f" in approximately {eta_hours} hours" if eta_hours is not None else ""
        return (
            f"Node {node_id} exhibits {anomalies_str} with an increasing trend. "
            f"A critical threshold violation is projected{eta_str}; physical site inspection is recommended within 12 hours."
        )
    elif severity == "Critical":
        return (
            f"Node {node_id} is experiencing severe structural deviations with critical levels of {anomalies_str}. "
            f"Immediate site protocol should be initiated to verify structural integrity and prevent failure."
        )
    
    return f"Node {node_id} has entered a '{severity}' state. Further inspection and verification are recommended."
