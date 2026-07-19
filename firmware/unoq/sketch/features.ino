#include "config.h"

extern SensorPacket sensorPacket;
extern FeatureVector features;

extern float filteredSignal[FFT_SIZE]; // DC-removed + low-pass filtered vibration
extern float fftMagnitude[FFT_SIZE / 2];

void extractFeatures()
{
    // ---- Time-domain features (from filteredSignal) ----
    float sum        = 0.0f;
    float sumSquares = 0.0f;
    float peak       = 0.0f;

    for (int i = 0; i < FFT_SIZE; i++)
    {
        float v = filteredSignal[i];
        sum += v;
        sumSquares += v * v;

        float absV = fabsf(v);
        if (absV > peak) peak = absV;
    }

    float mean = sum / FFT_SIZE;
    float rms  = sqrtf(sumSquares / FFT_SIZE);

    float varianceSum = 0.0f;
    for (int i = 0; i < FFT_SIZE; i++)
    {
        float d = filteredSignal[i] - mean;
        varianceSum += d * d;
    }
    float stddev = sqrtf(varianceSum / FFT_SIZE);

    float crestFactor = (rms > 1e-6f) ? (peak / rms) : 0.0f;

    // ---- Frequency-domain feature: dominant frequency ----
    // Skip bin 0 (DC) - it's already ~0 after DC removal.
    int   peakBin = 1;
    float peakMag = fftMagnitude[1];
    for (int k = 2; k < FFT_SIZE / 2; k++)
    {
        if (fftMagnitude[k] > peakMag)
        {
            peakMag = fftMagnitude[k];
            peakBin = k;
        }
    }
    float dominantFrequency = peakBin * FFT_FREQ_RESOLUTION;

    // ---- Assemble the 8-value FeatureVector ----
    features.rms               = rms;
    features.stddev            = stddev;
    features.peak               = peak;
    features.dominantFrequency = dominantFrequency;
    features.crestFactor       = crestFactor;

    // Direct sensor passthroughs - no DSP required
    features.tilt        = sensorPacket.tilt;
    features.strain       = sensorPacket.strain;
    features.temperature  = sensorPacket.temperature;
}
