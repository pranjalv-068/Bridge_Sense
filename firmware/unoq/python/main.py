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

AI_PC_URL = os.environ.get("AI_PC_URL")
NODE_ID = "N3"

model = BridgeSenseModel()


def read_from_stm32() -> list[float]:
    """TEMPORARY MOCK — replace once the sketch + Bridge API is wired up."""
    import random
    return [
        0.25 + random.uniform(-0.02, 0.02),
        0.035 + random.uniform(-0.005, 0.005),
        0.50 + random.uniform(-0.05, 0.05),
        8.08 + random.uniform(-0.2, 0.2),
        2.00 + random.uniform(-0.1, 0.1),
        0.021 + random.uniform(-0.005, 0.005),
        193.6 + random.uniform(-10, 10),
        30.2 + random.uniform(-1, 1),
    ]


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
        raw = read_from_stm32()
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
        time.sleep(2)


if __name__ == "__main__":
    main()