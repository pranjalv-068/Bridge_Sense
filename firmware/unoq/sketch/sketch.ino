/***********************************************************************
  1. Receive raw sensor packet from NodeMCU
  2. Signal Conditioning
  3. FFT
  4. Feature Extraction
  5. AI Inference
  6. Send results to Dashboard
 **********************************************************************/

#include "config.h"



void initializeCommunication();
void initializeSignalProcessing();
void initializeML();


bool receiveSensorPacket();

void preprocessSignal();

void computeFFT();

void extractFeatures();

void runInference();

void sendTelemetry();


SensorPacket sensorPacket;

FeatureVector features;

PredictionResult prediction;
void setup()
{
    Serial.begin(SERIAL_BAUDRATE);

    Serial.println();
    Serial.println("======================================");
    Serial.println("      BridgeSense Sensor Node");
    Serial.println("======================================");
    Serial.println();

    initializeCommunication();

    initializeSignalProcessing();

    initializeML();

    Serial.println("BridgeSense Ready.");
}


void loop()
{

    if(receiveSensorPacket())
    {
        // Remove DC Offset + Filter Noise
        preprocessSignal();


        // FFT
        computeFFT();


        // Extract Features        
        extractFeatures();

        //Dragonwing AI
        runInference();

        // Dashboard
        sendTelemetry();

    }

}