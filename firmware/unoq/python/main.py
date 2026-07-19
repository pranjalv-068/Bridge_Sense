import os
import sys
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

# --- TEMPORARY DEBUG ---
app_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
print("App root:", app_root)
print("Contents of app root:", os.listdir(app_root))
bricks_path = os.path.join(app_root, "bricks")
if os.path.isdir(bricks_path):
    print("Contents of bricks/:", os.listdir(bricks_path))
    bridge_sense_path = os.path.join(bricks_path, "bridge_sense")
    if os.path.isdir(bridge_sense_path):
        print("Contents of bricks/bridge_sense/:", os.listdir(bridge_sense_path))
    else:
        print("bricks/bridge_sense/ does NOT exist in container")
else:
    print("bricks/ does NOT exist in container")
# --- END DEBUG ---

from bricks.bridge_sense import BridgeSenseModel
import time
import requests

from net_receiver import NodeReceiver
from features import extract_features

AI_PC_URL = os.environ.get("AI_PC_URL")
NODE_ID = "N3"
WIFI_PORT = int(os.environ.get("WIFI_PORT", 5000))

model = BridgeSenseModel()

receiver = NodeReceiver(port=WIFI_PORT)
receiver.start()


def read_from_node(timeout: float = 5.0):
    """Blocks for a fresh packet from the NodeMCU, runs Phase 3/4
    (DSP + feature extraction), and returns the 8-value feature vector.
    Returns None if nothing arrives within `timeout` seconds."""
    packet = receiver.wait_for_packet(timeout=timeout)
    if packet is None:
        return None
    return extract_features(
        packet["vibration"], packet["strain"], packet["temperature"], packet["tilt"]
    )


def send_to_ai_pc(payload: dict) -> None:
    if not AI_PC_URL:
        print("AI_PC_URL not set — skipping POST")
        return
    try:
        requests.post(AI_PC_URL, json=payload, timeout=2)
    except requests.RequestException as e:
        print(f"Failed to reach AI PC: {e}")


def main():
    while True:
        raw = read_from_node()
        if raw is None:
            print("[MAIN] No packet from NodeMCU yet, waiting...")
            continue

        result = model.infer(raw)
        payload = {
            "node_id": NODE_ID,
            "reconstruction_error": result["reconstruction_error"],
            "health_index": round(result["health_index"]),   # int, not float
            "edge_state": result["edge_state"],
            "frequency": raw[3],
            "strain": raw[6],
            "temperature": raw[7],
        }
        print(payload)
        send_to_ai_pc(payload)


if __name__ == "__main__":
    main()
