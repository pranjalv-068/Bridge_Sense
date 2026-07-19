#define BRIDGESENSE_CONFIG_H
#define BRIDGESENSE_CONFIG_H
 
#include <Arduino.h>
 
// ---------------- Project ----------------
#define PROJECT_NAME               "BridgeSense"
#define PROJECT_VERSION            "1.0.0"
#define SERIAL_BAUDRATE            115200
 
// ---------------- Sensor ----------------
#define SENSOR_MPU6050
#define SAMPLE_RATE                200
#define FFT_SIZE                   256
#define AXIS_COUNT                 3
#define SAMPLE_WINDOW               FFT_SIZE
 
// ---------------- DSP ----------------
#define ENABLE_DC_REMOVAL           true
#define ENABLE_HAMMING_WINDOW       true
#define ENABLE_LOW_PASS_FILTER      true
#define ENABLE_HIGH_PASS_FILTER     false
#define FEATURE_COUNT               8
#define LOW_PASS_CUTOFF_HZ          20.0f
#define FFT_FREQ_RESOLUTION         ((float)SAMPLE_RATE / (float)FFT_SIZE)
 
// ---------------- Communication (Phase 2) ----------------
// WiFi
#define WIFI_SSID                   "YOUR_WIFI_SSID"
#define WIFI_PASSWORD               "YOUR_WIFI_PASSWORD"
#define WIFI_CONNECT_TIMEOUT_MS     15000
 
// Raw packet socket — UNOQ acts as TCP server, NodeMCU connects to it
#define WIFI_PORT                   5000
 
// MQTT (used from Phase 6 backend/telemetry, connection lives here)
#define MQTT_BROKER                 "broker.example.com"
#define MQTT_PORT                   1883
#define MQTT_CLIENT_ID              "BridgeSense_UNOQ"
#define MQTT_USERNAME               ""
#define MQTT_PASSWORD               ""
#define MQTT_TOPIC_TELEMETRY        "bridgesense/telemetry"
#define MQTT_TOPIC_STATUS           "bridgesense/status"
#define MQTT_RECONNECT_INTERVAL_MS  5000
 
// Packet framing: [SYNC1][SYNC2][ payload ][CHECKSUM]
#define PACKET_SYNC_BYTE_1          0xAA
#define PACKET_SYNC_BYTE_2          0x55
#define PACKET_PAYLOAD_SIZE         (sizeof(Sample) * SAMPLE_WINDOW + sizeof(float) * 2 + sizeof(uint32_t))
#define PACKET_FRAME_SIZE           (2 + PACKET_PAYLOAD_SIZE + 1)
#define COMM_BUFFER_SIZE            4096
 
// ---------------- Data Structures ----------------
struct Sample
{
    float ax;
    float ay;
    float az;
};
 
struct SensorPacket
{
    Sample samples[SAMPLE_WINDOW];
    float strain;
    float temperature;
    uint32_t timestamp;
};
 
struct FeatureVector
{
    float rms;
    float stddev;
    float peak;
    float dominantFrequency;
    float crestFactor;
    float tilt;
    float strain;
    float temperature;
};
 
struct PredictionResult
{
    float reconstructionError;
    float confidence;
    float healthIndex;
    String severity;
    String edgeState;
    String predictedClass;
};
 
#endif