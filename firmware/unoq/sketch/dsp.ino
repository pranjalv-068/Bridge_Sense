/***********************************************************************
 Phase 3 - DSP
 Operates on the raw vibration channel (1 of the 4 sensor inputs).
 strain / temperature / tilt are scalars per window and need no DSP -
 they're passed straight through to FeatureVector in Phase 4.

 Chain: Circular buffer -> DC removal -> Butterworth low-pass
        -> Hamming window -> CMSIS-DSP real FFT -> magnitude spectrum
 **********************************************************************/
#include "config.h"
#include <arm_math.h>   // CMSIS-DSP. Assumes an ARM Cortex-M target (e.g.
                         // STM32). On ESP32 use the ESP-DSP library
                         // (esp_dsp.h, dsps_fft2r_fc32) instead - same
                         // shape, different init/call names.

extern SensorPacket sensorPacket;

// ---------------- Circular buffer ----------------
// Accumulates the incoming vibration stream into an FFT_SIZE window.
// Currently filled in bulk per packet; ready for per-sample streaming
// later without changing anything downstream.
struct CircularBuffer
{
    float  data[FFT_SIZE];
    size_t head;
    size_t count;

    void reset() { head = 0; count = 0; }

    void push(float value)
    {
        data[head] = value;
        head = (head + 1) % FFT_SIZE;
        if (count < FFT_SIZE) count++;
    }

    bool isFull() const { return count == FFT_SIZE; }

    // Copies out oldest -> newest
    void toOrderedArray(float* out) const
    {
        size_t start = (head + FFT_SIZE - count) % FFT_SIZE;
        for (size_t i = 0; i < count; i++)
            out[i] = data[(start + i) % FFT_SIZE];
    }
};

static CircularBuffer ringBuffer;

// Shared with Phase 4 (feature extraction)
float rawVibration[FFT_SIZE];      // raw vibration samples, pre-filter
float filteredSignal[FFT_SIZE];    // DC-removed + low-pass filtered
float windowedSignal[FFT_SIZE];    // filteredSignal * Hamming (FFT input only)
float fftMagnitude[FFT_SIZE / 2];  // one-sided FFT magnitude spectrum

static float hammingWindow[FFT_SIZE];
static arm_rfft_fast_instance_f32 fftInstance;

// ---------------- Butterworth (2nd order low-pass biquad) ----------------
// Coefficients precomputed for SAMPLE_RATE=200Hz, cutoff=LOW_PASS_CUTOFF_HZ
// (20Hz). Regenerate with:
//   scipy.signal.butter(2, LOW_PASS_CUTOFF_HZ/(SAMPLE_RATE/2), btype='low')
// if either constant changes.
static const float BW_B0 =  0.06745535f;
static const float BW_B1 =  0.13491070f;
static const float BW_B2 =  0.06745535f;
static const float BW_A1 = -1.14298050f;
static const float BW_A2 =  0.41280160f;

static float bwX1 = 0, bwX2 = 0, bwY1 = 0, bwY2 = 0;

static float butterworthStep(float x)
{
    float y = BW_B0 * x + BW_B1 * bwX1 + BW_B2 * bwX2 - BW_A1 * bwY1 - BW_A2 * bwY2;
    bwX2 = bwX1; bwX1 = x;
    bwY2 = bwY1; bwY1 = y;
    return y;
}

void initializeSignalProcessing()
{
    ringBuffer.reset();

    for (int n = 0; n < FFT_SIZE; n++)
        hammingWindow[n] = 0.54f - 0.46f * cosf((2.0f * PI * n) / (FFT_SIZE - 1));

    arm_rfft_fast_init_f32(&fftInstance, FFT_SIZE);

    Serial.println("[DSP] Signal processing initialized.");
}

void preprocessSignal()
{
    // 1. Load the raw vibration channel into the circular buffer
    ringBuffer.reset();
    for (int i = 0; i < SAMPLE_WINDOW; i++)
        ringBuffer.push(sensorPacket.vibration[i]);
    ringBuffer.toOrderedArray(rawVibration);

    // 2. DC removal
    float mean = 0.0f;
    if (ENABLE_DC_REMOVAL)
    {
        for (int i = 0; i < FFT_SIZE; i++) mean += rawVibration[i];
        mean /= FFT_SIZE;
    }

    // 3. Butterworth low-pass (state reset per window for reproducibility)
    bwX1 = bwX2 = bwY1 = bwY2 = 0.0f;
    for (int i = 0; i < FFT_SIZE; i++)
    {
        float centered = rawVibration[i] - mean;
        filteredSignal[i] = ENABLE_LOW_PASS_FILTER ? butterworthStep(centered) : centered;
    }

    // 4. Hamming window - FFT input only, amplitude features use filteredSignal
    for (int i = 0; i < FFT_SIZE; i++)
    {
        windowedSignal[i] = ENABLE_HAMMING_WINDOW
                               ? filteredSignal[i] * hammingWindow[i]
                               : filteredSignal[i];
    }
}

void computeFFT()
{
    float fftOutput[FFT_SIZE]; // interleaved packed real-FFT output

    arm_rfft_fast_f32(&fftInstance, windowedSignal, fftOutput, 0);

    // arm_rfft_fast_f32 packs DC (bin 0) and Nyquist (bin N/2) into the
    // first two floats; bins 1..N/2-1 follow as (re, im) pairs.
    fftMagnitude[0] = fabsf(fftOutput[0]);
    for (int k = 1; k < FFT_SIZE / 2; k++)
    {
        float re = fftOutput[2 * k];
        float im = fftOutput[2 * k + 1];
        fftMagnitude[k] = sqrtf(re * re + im * im);
    }
}
