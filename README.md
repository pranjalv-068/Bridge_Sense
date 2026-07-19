# BridgeSense

AI powered Structural Health Monitoring System for Real Time Bridge Monitoring using Arduino UNO Q, NodeMCU, Edge Impulse and Machine Learning.

## System  Architecture

                           BRIDGESENSE AI
              Qualcomm Snapdragon Multiverse Architecture


                    ┌─────────────────────────────────┐
                    │      SENSOR LAYER               │
                    └─────────────────────────────────┘
```
                    Sensor Nodes

┌─────────────────────┐      ┌─────────────────────┐
│ NodeMCU ESP8266 #1  │      │ NodeMCU ESP8266 #2  │
├─────────────────────┤      ├─────────────────────┤
│ • MPU6050           │      │ • MPU6050           │
│ • Strain Gauge      │      │ • Strain Gauge      │
│ • Temperature       │      │ • Temperature       │
│ • Tilt Sensor       │      │ • Tilt Sensor       │
└──────────┬──────────┘      └──────────┬──────────┘
           │                            │
           └────────────┬───────────────┘
                        │
                        ▼
          Wi-Fi TCP Socket Communication
```

## System Architecture

```text
                    BridgeSense Architecture

                 +-------------------------+
                 |     Arduino UNO Q       |
                 |    (Edge AI Gateway)    |
                 +-----------+-------------+
                             │
         Internal MCU ↔ Linux Communication
                             │
        +--------------------+--------------------+
        │                                         │
        ▼                                         ▼

+-------------------------+          +---------------------------+
|      STM32U585          |          | Qualcomm QRB2210 (Linux)  |
|-------------------------|          |---------------------------|
| • Receive Wi-Fi Data    |          | • StandardScaler          |
| • Signal Processing     |          | • Edge Impulse Model      |
| • Low-pass Filter       |          | • Autoencoder Inference   |
| • FFT                   |          | • Reconstruction Error    |
| • Feature Extraction    |          | • Health Index            |
| • RMS                   |          | • Damage Classification   |
| • Mean                  |          | • Anomaly Detection       |
| • Standard Deviation    |          +---------------------------+
| • Peak                  |
| • Dominant Frequency    |
| • Tilt                  |
| • Strain                |
| • Temperature           |
+-------------------------+


                  SNAPDRAGON AI PC
               Qwen3-4B / GenieX Model

• Long term trend analysis

• Future forecasting

• Maintenance recommendation

• Root cause explanation

• Natural language reasoning

• Generate inspection reports

• Historical analytics

• Web Dashboard Backend

                        MOBILE APPLICATION

Engineer Dashboard

• Live Alerts

• Bridge Health

• Notifications

• Crack Image Capture

↓

Image uploaded to AI PC

↓

Computer Vision Model

↓

Compare with

• Sensor AI

• Vibration Analysis

• Strain Analysis

↓

Fusion Decision

↓

Confidence Score

↓

Final Inspection Recommendation

                     WEB DASHBOARD

• Live Sensor Data

• FFT Graph

• Bridge Health

• Historical Trends

• Forecasting

• Sensor Locations


# 🛠️ Hardware

| Component | Description |
|-----------|-------------|
| Arduino UNO Q (Qualcomm QRB2210 + STM32U585) | Edge AI Gateway |
| NodeMCU ESP8266 | Wireless Sensor Node |
| MPU6050 | 6-Axis IMU for Vibration & Tilt Measurement |
| Strain Gauge | Structural Strain Monitoring |
| Temperature Sensor | Ambient/Structure Temperature Monitoring |
| Wi-Fi Network | Sensor-to-Gateway Communication |

---

# 💻 Software

| Software | Purpose |
|----------|---------|
| Arduino App Lab | Edge AI Application Development |
| ArduinoIDE |NodeMCU code |
| Edge Impulse Studio | Model Training & Deployment |
| Python 3 | Edge AI Application and backend |
| React | Dashboard Logic |
| Kotlin | Application |
| Git & GitHub | Version Control |
| VS Code | Source Code Editor |

---

# 🤖 Machine Learning

| Property | Details |
|----------|---------|
| Model Type | Autoencoder |
| Framework | PyTorch |
| Deployment Platform | Edge Impulse |
| Deployment Format | `.eim` (Edge Impulse Model) |
| Target Device | Arduino UNO Q (Qualcomm QRB2210) |
| Input Features | 8 Structural Health Features |
| Output | Reconstructed Feature Vector |
| Inference Type | Edge AI |
| Anomaly Detection | Reconstruction Error |
| Health Metric | Bridge Health Index |
| Deployment Workflow | PyTorch → ONNX → Edge Impulse BYOM → Arduino UNO Q |
---

# 📊 Input Features

| Feature | Description |
|----------|-------------|
| RMS | Root Mean Square |
| Mean | Signal Mean |
| Standard Deviation | Signal Variability |
| Peak Amplitude | Maximum Signal Value |
| Dominant Frequency | FFT Peak Frequency |
| Tilt | Bridge Inclination |
| Strain | Structural Deformation (με) |
| Temperature | Structure Temperature (°C) |


# 🧠 AI Pipeline

```text
Raw Sensor Data
        │
        ▼
Signal Processing
        │
        ▼
Feature Extraction
        │        │
        ▼
Edge Impulse Autoencoder (.eim)
        │
        ▼
Bridge Health Index
        │
        ▼
Normal / Watch / Alert
```

<img width="1600" height="829" alt="WhatsApp Image 2026-07-19 at 12 37 27 PM" src="https://github.com/user-attachments/assets/ef29f852-b549-4e02-975f-5296b23dff0c" />

<img width="1600" height="850" alt="WhatsApp Image 2026-07-19 at 12 37 26 PM" src="https://github.com/user-attachments/assets/817169e6-6c95-42e2-bef2-9aacf5e7a97b" />


<img width="636" height="986" alt="image" src="https://github.com/user-attachments/assets/d96b45eb-30f8-478f-bb1c-22cf56f11fa4" />


