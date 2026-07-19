#include <Wire.h>
#include <WiFi.h>
#include <PubSubClient.h>
#include <ArduinoJson.h>

const char* ssid="Pranjal";
const char* password="12345678";
const char* mqttServer="10.241.204.76";
const int mqttPort=1883;
const char* topic="bridgesense/sensor";

WiFiClient espClient;
PubSubClient client(espClient);

// Sample structure without time
struct Sample {
  float pitch;
  float freq;
  float strain;
  float temp;
};

Sample samples[50];
int sampleIndex=0;

float lastGy=0;
unsigned long lastPeak=0;
float freqHz=0;

// MPU6050 I2C address
#define MPU6050_ADDR 0x68

void connectWiFi() {
  WiFi.begin(ssid,password);
  while(WiFi.status()!=WL_CONNECTED) {
    delay(500);
  }
}

void connectMQTT() {
  while(!client.connected()) {
    client.connect("ESP32BridgeSense");
    if(!client.connected()) delay(1000);
  }
}

void mpuWrite(uint8_t reg, uint8_t data) {
  Wire.beginTransmission(MPU6050_ADDR);
  Wire.write(reg);
  Wire.write(data);
  Wire.endTransmission();
}

void mpuRead(uint8_t reg, uint8_t* buf, uint8_t len) {
  Wire.beginTransmission(MPU6050_ADDR);
  Wire.write(reg);
  Wire.endTransmission(false);
  Wire.requestFrom(MPU6050_ADDR, len);
  for(int i=0;i<len && Wire.available();i++) {
    buf[i]=Wire.read();
  }
}

void setupMPU() {
  // Wake up MPU6050
  mpuWrite(0x6B, 0x00); // PWR_MGMT_1 = 0
  // Set accelerometer ±8g
  mpuWrite(0x1C, 0x10); // ACCEL_CONFIG
  // Set gyro ±500 deg/s
  mpuWrite(0x1B, 0x08); // GYRO_CONFIG
}

void setup() {
  Serial.begin(115200);
  Wire.begin(21,22);
  setupMPU();

  connectWiFi();
  while (!client.connected()) {
    if (client.connect("ESP32BridgeSense")) {
        Serial.println("MQTT Connected");
    } else {
        Serial.print("Failed, rc=");
        Serial.println(client.state());
        delay(1000);
    }
}
  connectMQTT();
}

float computePitch(float ax,float ay,float az) {
  return atan2(ax, sqrt(ay*ay+az*az)) * 180.0 / PI;
}

void publishBatch() {
  StaticJsonDocument<8192> doc;
  doc["device"]="bridge_node_01";
  JsonArray arr=doc.createNestedArray("samples");

  for(int i=0;i<50;i++) {
    JsonObject o=arr.createNestedObject();
    o["pitch"]=samples[i].pitch;
    o["frequency"]=samples[i].freq;
    o["strain"]=samples[i].strain;
    o["temperature"]=samples[i].temp;
  }

  char buffer[8192];
  size_t n=serializeJson(doc,buffer);
  client.publish(topic,buffer,n);
}

void loop() {
  if(WiFi.status()!=WL_CONNECTED) connectWiFi();
  if(!client.connected()) connectMQTT();
  client.loop();

  static unsigned long lastSample=0;
  if(millis()-lastSample>=100) {
    lastSample=millis();

    uint8_t rawData[14];
    mpuRead(0x3B, rawData, 14); // accel+gyro+temp

    int16_t ax = (rawData[0]<<8)|rawData[1];
    int16_t ay = (rawData[2]<<8)|rawData[3];
    int16_t az = (rawData[4]<<8)|rawData[5];
    int16_t tempRaw = (rawData[6]<<8)|rawData[7];
    int16_t gx = (rawData[8]<<8)|rawData[9];
    int16_t gy = (rawData[10]<<8)|rawData[11];
    int16_t gz = (rawData[12]<<8)|rawData[13];

    // Scale factors
    float ax_g = ax/4096.0;   // ±8g
    float ay_g = ay/4096.0;
    float az_g = az/4096.0;
    float gy_dps = gy/65.5;   // ±500 deg/s

    float pitch = computePitch(ax_g, ay_g, az_g);

    // crude vibration frequency detection
    if(lastGy<20 && gy_dps>=20) {
      unsigned long now=millis();
      if(lastPeak!=0) {
        freqHz=1000.0/(now-lastPeak);
      }
      lastPeak=now;
    }
    lastGy=gy_dps;

    float strain=120+(freqHz*30)+random(-5,6);
    float tempC=(tempRaw/340.0)+36.53; // datasheet formula

    samples[sampleIndex].pitch=pitch;
    samples[sampleIndex].freq=freqHz;
    samples[sampleIndex].strain=strain;
    samples[sampleIndex].temp=tempC;

    Serial.printf("%d Pitch=%.2f  Freq=%.2fHz  Strain=%.1f Temp=%.1f\n",
                  sampleIndex,pitch,freqHz,strain,tempC);

    sampleIndex++;
    if(sampleIndex>=50) {
      publishBatch();
      sampleIndex=0;
    }
  }
}