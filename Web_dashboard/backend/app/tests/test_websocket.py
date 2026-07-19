import sys
import asyncio
import json
import urllib.request
from pathlib import Path

# Add Web_dashboard to path
web_dashboard_dir = Path(__file__).resolve().parent.parent.parent.parent.parent
if str(web_dashboard_dir) not in sys.path:
    sys.path.insert(0, str(web_dashboard_dir))

import websockets

async def main():
    uri = "ws://localhost:8000/ws/telemetry"
    print(f"Connecting to WebSocket at {uri}...")
    
    async with websockets.connect(uri) as websocket:
        print("Connected! Waiting for initial connection payload...")
        
        # 1. Receive initial state message
        initial_msg = await websocket.recv()
        initial_data = json.loads(initial_msg)
        print("Received initial payload type:", initial_data.get("type"))
        assert initial_data.get("type") == "update", "First message should be update"
        
        # 2. Trigger a POST request to inject telemetry
        payload = {
            "node_id": "NODE_03",
            "temperature": 32.5,
            "frequency": 7.9,
            "reconstruction_error": 0.35,
            "health_index": 88,
            "vibration_hz": 7.9,
            "strain_ue": 150.0
        }
        
        print("\nSending POST request to trigger telemetry processing...")
        req = urllib.request.Request(
            "http://localhost:8000/telemetry",
            data=json.dumps(payload).encode("utf-8"),
            headers={"Content-Type": "application/json"},
            method="POST"
        )
        
        # Open in separate executor to avoid blocking asyncio loop
        loop = asyncio.get_running_loop()
        response = await loop.run_in_executor(None, lambda: urllib.request.urlopen(req).read().decode())
        print("POST response:", response)
        
        # 3. Receive the broadcasted updates on the WebSocket
        print("\nWaiting for WebSocket broadcast update...")
        
        # The API broadcasts two separate messages:
        # Message 1: The standard dashboard update (type: "update")
        msg1 = await websocket.recv()
        data1 = json.loads(msg1)
        print("Received WebSocket Message 1 type:", data1.get("type"))
        
        # Verify the node inference matches the new data
        node_inferences = data1.get("processed_data", {}).get("node_inferences", [])
        node_03_inference = next((node for node in node_inferences if node["node_id"] == "NODE_03"), None)
        
        print("Node 03 Inference state in Message 1:")
        print(" - Anomaly score:", node_03_inference.get("anomaly_score"))
        print(" - Severity Level:", node_03_inference.get("severity_level"))
        
        assert node_03_inference is not None
        assert node_03_inference["anomaly_score"] == 0.35
        assert node_03_inference["severity_level"] == "Normal"
        
        # Message 2: The clean decision payload
        msg2 = await websocket.recv()
        data2 = json.loads(msg2)
        print("\nReceived WebSocket Message 2 (Decision Payload):")
        print(json.dumps(data2, indent=2))
        
        assert "edge" in data2
        assert "severity" in data2
        assert "forecast" in data2
        assert data2["severity"]["score"] == 17.1
        assert data2["edge"]["edge_state"] == "Normal"
        
        print("\n🎉 ALL E2E WebSocket & Integration Tests Passed Successfully!")

if __name__ == "__main__":
    asyncio.run(main())
