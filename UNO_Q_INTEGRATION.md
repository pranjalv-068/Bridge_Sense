# BridgeSense UNO Q Integration Manual

This document provides instructions and code templates to connect the Qualcomm UNO Q (Edge AI Node) to the BridgeSense AI PC Backend.

---

## 1. Network Topology

For the UNO Q to transmit data to the FastAPI backend, both devices must be on the same local area network (LAN), or the AI PC must have a publicly reachable IP address.

```text
  +-----------------------+              +-----------------------+
  |    Qualcomm UNO Q     |  HTTP POST   |     AI PC Backend     |
  | (Runs Autoencoder AI) | -----------> |  (FastAPI on Port 8000)  |
  +-----------------------+              +-----------------------+
```

1. **Find AI PC Local IP**: On the AI PC terminal, run `ip a` (Linux) or `ipconfig` (Windows) to find your local IP address (e.g., `192.168.1.45`).
2. **Expose Server**: Ensure uvicorn is running and listening on all interfaces (host `0.0.0.0`), which is already configured in the startup scripts (`./backend/run.sh`).

---

## 2. API Telemetry Schema

The FastAPI backend exposes the following endpoint for telemetry updates:

* **Endpoint**: `POST http://<AI_PC_IP>:8000/telemetry` (or `POST http://<AI_PC_IP>:8000/api/node-data`)
* **Content-Type**: `application/json`

### JSON Payload Structure

```json
{
  "node_id": "N3",
  "rms": 0.28,
  "stddev": 0.03,
  "peak": 0.55,
  "frequency": 8.02,
  "crest_factor": 2.01,
  "tilt": 0.021,
  "strain": 188.0,
  "temperature": 31.2,
  "reconstruction_error": 0.63,
  "health_index": 82,
  "edge_state": "Watch"
}
```

*Note: The backend automatically normalizes shorthand IDs like `"N3"` to matching schematic positions like `"NODE_03"`.*

---

## 3. UNO Q C++ Code Template

Below is a complete C++ implementation template using standard Arduino WiFi and HTTP client libraries. Add this function to your UNO Q sketch to transmit the anomaly scores computed by your autoencoder.

```cpp
#include <WiFi.h>
#include <HTTPClient.h>

// WiFi Configuration
const char* ssid     = "YOUR_WIFI_SSID";
const char* password = "YOUR_WIFI_PASSWORD";

// AI PC Server endpoint configuration (Replace with your AI PC's actual local IP address)
const char* ai_pc_endpoint = "http://192.168.1.45:8000/telemetry";

void setup() {
  Serial.begin(115200);
  
  // Connect to WiFi
  WiFi.begin(ssid, password);
  Serial.print("Connecting to WiFi");
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("\nWiFi connected successfully!");
  Serial.print("Local IP Address: ");
  Serial.println(WiFi.localIP());
}

/**
 * Transmits the autoencoder inference results and feature metrics to the AI PC backend.
 */
void sendTelemetryToAIPC(String nodeId, float reconError, int healthIndex, String edgeState, float freq, float strain) {
  if (WiFi.status() == WL_CONNECTED) {
    HTTPClient http;
    
    // Initialize HTTP communication
    http.begin(ai_pc_endpoint);
    http.addHeader("Content-Type", "application/json");
    
    // Construct JSON payload
    // Note: To keep code lightweight without external JSON libraries, we format raw strings
    String jsonPayload = "{";
    jsonPayload += "\"node_id\":\"" + nodeId + "\",";
    jsonPayload += "\"frequency\":" + String(freq, 2) + ",";
    jsonPayload += "\"strain\":" + String(strain, 1) + ",";
    jsonPayload += "\"reconstruction_error\":" + String(reconError, 4) + ",";
    jsonPayload += "\"health_index\":" + String(healthIndex) + ",";
    jsonPayload += "\"edge_state\":\"" + edgeState + "\"";
    jsonPayload += "}";
    
    Serial.print("[HTTP] Sending telemetry POST...");
    int httpResponseCode = http.POST(jsonPayload);
    
    if (httpResponseCode > 0) {
      String response = http.getString();
      Serial.print(" Response code: ");
      Serial.println(httpResponseCode);
      Serial.println("Response payload: " + response);
    } else {
      Serial.print(" Error sending POST: ");
      Serial.println(http.errorToString(httpResponseCode).c_str());
    }
    
    // Close HTTP connection resources
    http.end();
  } else {
    Serial.println("[Error] WiFi Disconnected. Unable to send telemetry.");
  }
}

void loop() {
  // --- MOCK AUTOENCODER INFERENCE OUTPUT ---
  // Replace these with your actual NPU model outputs
  String nodeId = "N3";
  float mockReconstructionError = 0.63; // E.g., model reconstruction error
  int mockHealthIndex = 82;             // 100 * exp(-error/3.0)
  String mockEdgeState = "Watch";        // Edge policy state: "Normal", "Watch", or "Alert"
  
  float mockFreq = 8.02;                // Latest sensor feature
  float mockStrain = 188.0;             // Latest sensor feature
  
  // Send metrics once every 10 seconds (or your specific update interval)
  sendTelemetryToAIPC(nodeId, mockReconstructionError, mockHealthIndex, mockEdgeState, mockFreq, mockStrain);
  
  delay(10000);
}
```

---

## 4. Testing & Verification

1. **Examine Uvicorn Output**:
   When the UNO Q transmits telemetry, you will immediately see a request logging success in the backend logs:
   ```text
   INFO:     192.168.1.102:49811 - "POST /telemetry HTTP/1.1" 200 OK
   ```
2. **WebSocket Broadcast Check**:
   The backend immediately computes forecasting trends, severity grades, and natural language explanations, then pushes a complete JSON update to the dashboard. The React frontend UI updates automatically in real-time without needing browser refreshes.
