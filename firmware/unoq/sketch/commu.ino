/***********************************************************************
 Phase 2 - Communication
 - WiFi connection management
 - MQTT connection management (client used later by sendTelemetry)
 - TCP packet server: NodeMCU connects and streams raw sensor frames
 - Buffering + resync so partial/misaligned TCP reads still recover
 - Packet validation via XOR checksum
 **********************************************************************/
#include <WiFi.h>          // ESP32 core. If targeting ESP8266, swap for
                            // <ESP8266WiFi.h> - the API used below is compatible.
#include <PubSubClient.h>  // Install "PubSubClient" by Nick O'Leary via Library Manager
#include "config.h"

extern SensorPacket sensorPacket;

WiFiServer  packetServer(WIFI_PORT);
WiFiClient  nodeClient;
WiFiClient  mqttNet;
PubSubClient mqttClient(mqttNet);

static uint8_t      commBuffer[COMM_BUFFER_SIZE];
static size_t        bufferIndex   = 0;
static unsigned long lastMqttTry   = 0;

static bool connectWiFi();
static bool connectMQTT();
static bool validatePacket(const uint8_t* buf, size_t len);
static void parsePacketIntoStruct(const uint8_t* buf);

// PubSubClient is exposed globally so Phase 6 (sendTelemetry) can publish
// without re-establishing its own connection.

void initializeCommunication()
{
    Serial.println("[COMM] Initializing WiFi...");
    connectWiFi();

    packetServer.begin();
    Serial.print("[COMM] Packet server listening on port ");
    Serial.println(WIFI_PORT);

    mqttClient.setServer(MQTT_BROKER, MQTT_PORT);
    connectMQTT();
}

static bool connectWiFi()
{
    if (WiFi.status() == WL_CONNECTED) return true;

    Serial.print("[COMM] Connecting to WiFi: ");
    Serial.println(WIFI_SSID);

    WiFi.mode(WIFI_STA);
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

    unsigned long start = millis();
    while (WiFi.status() != WL_CONNECTED && millis() - start < WIFI_CONNECT_TIMEOUT_MS)
    {
        delay(250);
        Serial.print(".");
    }
    Serial.println();

    if (WiFi.status() == WL_CONNECTED)
    {
        Serial.print("[COMM] WiFi connected. IP: ");
        Serial.println(WiFi.localIP());
        return true;
    }

    Serial.println("[COMM] WiFi connection FAILED (will retry in loop).");
    return false;
}

static bool connectMQTT()
{
    if (mqttClient.connected()) return true;
    if (WiFi.status() != WL_CONNECTED) return false;
    if (millis() - lastMqttTry < MQTT_RECONNECT_INTERVAL_MS) return false;
    lastMqttTry = millis();

    Serial.print("[COMM] Connecting to MQTT broker...");
    bool ok = (strlen(MQTT_USERNAME) > 0)
                ? mqttClient.connect(MQTT_CLIENT_ID, MQTT_USERNAME, MQTT_PASSWORD)
                : mqttClient.connect(MQTT_CLIENT_ID);

    if (ok)
    {
        Serial.println(" connected.");
        mqttClient.publish(MQTT_TOPIC_STATUS, "online");
    }
    else
    {
        Serial.print(" failed, rc=");
        Serial.println(mqttClient.state());
    }
    return ok;
}

// Returns true exactly once a full, checksum-valid SensorPacket has been
// decoded into the global `sensorPacket`.
bool receiveSensorPacket()
{
    // Keep the transport layer alive
    if (WiFi.status() != WL_CONNECTED) connectWiFi();
    if (!mqttClient.connected()) connectMQTT();
    mqttClient.loop();

    // Accept a NodeMCU connection if we don't have one
    if (!nodeClient || !nodeClient.connected())
    {
        WiFiClient incoming = packetServer.available();
        if (!incoming) return false;
        nodeClient = incoming;
        Serial.println("[COMM] NodeMCU connected.");
        bufferIndex = 0;
    }

    bool packetReady = false;

    while (nodeClient.available() && !packetReady)
    {
        if (bufferIndex >= COMM_BUFFER_SIZE)
        {
            Serial.println("[COMM] Buffer overflow, resetting.");
            bufferIndex = 0;
        }

        commBuffer[bufferIndex++] = (uint8_t)nodeClient.read();

        if (bufferIndex < PACKET_FRAME_SIZE) continue;

        if (commBuffer[0] == PACKET_SYNC_BYTE_1 && commBuffer[1] == PACKET_SYNC_BYTE_2)
        {
            if (validatePacket(commBuffer, PACKET_FRAME_SIZE))
            {
                parsePacketIntoStruct(commBuffer + 2);
                bufferIndex = 0;
                packetReady = true;
            }
            else
            {
                Serial.println("[COMM] Checksum mismatch, dropping frame.");
                bufferIndex = 0;
            }
        }
        else
        {
            // Not aligned to a frame start yet - slide the window by one
            // byte and keep scanning instead of throwing away everything.
            memmove(commBuffer, commBuffer + 1, --bufferIndex);
        }
    }

    return packetReady;
}

static bool validatePacket(const uint8_t* buf, size_t len)
{
    uint8_t checksum = 0;
    for (size_t i = 0; i < len - 1; i++) checksum ^= buf[i];
    return checksum == buf[len - 1];
}

static void parsePacketIntoStruct(const uint8_t* buf)
{
    size_t offset = 0;

    memcpy(sensorPacket.samples, buf + offset, sizeof(Sample) * SAMPLE_WINDOW);
    offset += sizeof(Sample) * SAMPLE_WINDOW;

    memcpy(&sensorPacket.strain, buf + offset, sizeof(float));
    offset += sizeof(float);

    memcpy(&sensorPacket.temperature, buf + offset, sizeof(float));
    offset += sizeof(float);

    memcpy(&sensorPacket.timestamp, buf + offset, sizeof(uint32_t));
}
