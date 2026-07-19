import asyncio
import json
import random
from typing import List, Dict
from fastapi import FastAPI, WebSocket, WebSocketDisconnect, UploadFile, File, Form
from fastapi.responses import JSONResponse
import uvicorn
import os

app = FastAPI(title="BridgeSense AI PC Server")

# Ensure uploads directory exists
UPLOAD_DIR = "uploads"
os.makedirs(UPLOAD_DIR, exist_ok=True)

# ─── WebSocket Connection Manager ──────────────────────────────────────────

class ConnectionManager:
    def __init__(self):
        self.active_connections: List[WebSocket] = []

    async def connect(self, websocket: WebSocket):
        await websocket.accept()
        self.active_connections.append(websocket)
        print(f"[WS] Client connected. Total: {len(self.active_connections)}")

    def disconnect(self, websocket: WebSocket):
        if websocket in self.active_connections:
            self.active_connections.remove(websocket)
            print(f"[WS] Client disconnected. Total: {len(self.active_connections)}")

    async def broadcast(self, message: str):
        for connection in self.active_connections:
            try:
                await connection.send_text(message)
            except Exception as e:
                print(f"[WS] Error sending message: {e}")

manager = ConnectionManager()

# ─── State Simulation (Simulating UnoQ Sensor Data) ──────────────────────

class BridgeSimulator:
    def __init__(self):
        self.nodes = [
            {"node_id": 1, "node_name": "Node-01 (Mid-Span)", "status": "normal", "vibration": 1.2, "tilt": 0.5, "temperature": 28.0},
            {"node_id": 2, "node_name": "Node-02 (Support P1)", "status": "normal", "vibration": 0.8, "tilt": 0.2, "temperature": 27.5}
        ]

    def update_sensors(self):
        # Randomly fluctuate sensor values
        for node in self.nodes:
            node["vibration"] = round(node["vibration"] + random.uniform(-0.1, 0.1), 2)
            node["tilt"] = round(node["tilt"] + random.uniform(-0.05, 0.05), 2)
            node["temperature"] = round(node["temperature"] + random.uniform(-0.2, 0.2), 1)

            # Randomly trigger a warning/critical state for demo purposes
            if random.random() < 0.05: # 5% chance per tick
                node["status"] = "warning"
                node["vibration"] += 2.0
                print(f"[SIM] Node {node['node_id']} spiked!")
            elif node["status"] == "warning" and random.random() < 0.2:
                node["status"] = "normal" # Recover

        return {
            "type": "node_update",
            "nodes": self.nodes
        }

    def generate_alert(self):
        if random.random() < 0.1: # 10% chance per tick
            node = random.choice(self.nodes)
            return {
                "type": "alert",
                "id": str(random.randint(1000, 9999)),
                "node_id": node["node_id"],
                "node_name": node["node_name"],
                "alert_type": "AI_ANOMALY_DETECTED",
                "severity": "WARNING",
                "message": f"Elevated vibrations ({node['vibration']} m/s²) detected. Inspection recommended."
            }
        return None

simulator = BridgeSimulator()

# ─── Endpoints ─────────────────────────────────────────────────────────────

@app.get("/ping")
async def ping():
    return {"status": "ok", "message": "BridgeSense AI PC is online"}

@app.websocket("/ws")
async def websocket_endpoint(websocket: WebSocket):
    await manager.connect(websocket)
    try:
        while True:
            data = await websocket.receive_text()
            # Handle messages from the Android app (e.g., photo upload notifications)
            try:
                msg = json.loads(data)
                if msg.get("type") == "photo_uploaded":
                    print(f"[AI] App uploaded {msg.get('num_detections')} detections for Node {msg.get('node_id')}.")
                    # In a real system, the AI PC would map these detections back to the digital twin
            except json.JSONDecodeError:
                pass
    except WebSocketDisconnect:
        manager.disconnect(websocket)

@app.post("/upload")
async def upload_photo(
    node_id: int = Form(...),
    detections: str = Form(...),
    image: UploadFile = File(...)
):
    try:
        # Save the uploaded image
        file_path = os.path.join(UPLOAD_DIR, f"node_{node_id}_{image.filename}")
        with open(file_path, "wb") as buffer:
            buffer.write(await image.read())
        
        # Parse the YOLOX bounding boxes sent by the app
        det_list = json.loads(detections)
        
        print(f"\n--- [UPLOAD RECEIVED] ---")
        print(f"Node: {node_id}")
        print(f"Image saved to: {file_path}")
        print(f"Detections ({len(det_list)}):")
        for d in det_list:
            print(f"  - {d['label']} ({int(d['confidence']*100)}%) Box:[{d['bbox_left']:.2f}, {d['bbox_top']:.2f}, {d['bbox_right']:.2f}, {d['bbox_bottom']:.2f}]")
        print(f"-------------------------\n")

        return JSONResponse(content={"status": "success", "message": "Photo and data received"})

    except Exception as e:
        print(f"[Error] Upload failed: {e}")
        return JSONResponse(content={"status": "error", "message": str(e)}, status_code=500)


# ─── Background Broadcast Task ─────────────────────────────────────────────

async def broadcast_sensor_data():
    while True:
        await asyncio.sleep(2.0) # Stream at 0.5 Hz for demo
        if manager.active_connections:
            # 1. Broadcast Node Statuses
            update_msg = simulator.update_sensors()
            await manager.broadcast(json.dumps(update_msg))
            
            # 2. Broadcast Alerts (randomly)
            alert_msg = simulator.generate_alert()
            if alert_msg:
                await manager.broadcast(json.dumps(alert_msg))

@app.on_event("startup")
async def startup_event():
    asyncio.create_task(broadcast_sensor_data())

if __name__ == "__main__":
    print("Starting BridgeSense Server...")
    print("Ensure Android app connects to this machine's local IP address.")
    uvicorn.run("server:app", host="0.0.0.0", port=8080, reload=True)
