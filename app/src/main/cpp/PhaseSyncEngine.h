#ifndef PHASE_SYNC_ENGINE_H
#define PHASE_SYNC_ENGINE_H

#include <cstdint>
#include <cmath>
#include <algorithm>
#include <array>
#include <atomic>

/**
 * Senior DJ Audio DSP Sub-Bass & Percussion Phase Alignment Engine.
 * Modeled after Pioneer Rekordbox, VirtualDJ Pro & Traktor Pro Smart Sync.
 *
 * Features:
 * 1. Zero-Allocation Real-time Audio Callback Operation (NO malloc / new / free).
 * 2. 2nd-Order Butterworth 150 Hz Low-Pass Biquad Filter for Kick & Sub-Bass Isolation.
 * 3. 2nd-Order 200 Hz - 3000 Hz BandPass Biquad Filter for Snare, Clap & Percussion Kit Isolation.
 * 4. Fast Time-Domain Normalized Cross-Correlation (NCC) over a ±30 ms Search Window.
 * 5. Smart Sub-Bass Crossover: Auto-attenuates non-dominant sub-bass to eliminate acoustic phase cancellation.
 */

struct MasterClockState {
    int64_t masterSamplePosition = 0;
    double masterBaseBpm = 128.0;      // Unpitched base BPM of Master track
    double masterEffectiveBpm = 128.0; // Effective BPM = masterBaseBpm * playbackRate
    int64_t masterFirstBeatFrame = 0;
    double masterBeatPhase = 0.0;     // 0.0 to 1.0 inside 1 beat
};

struct SyncAdjustmentResult {
    float pitchRatio = 1.0f;            // Base tempo pitch multiplier (e.g. 1.05f for +5%)
    float microNudgeFactor = 1.0f;      // Smooth Phase Nudge (1.0 ± 0.025f for continuous catch-up)
    float finalPlaybackRate = 1.0f;     // Combined Rate = pitchRatio * microNudgeFactor
    int64_t sampleOffsetCorrection = 0; // Instant discrete sample offset adjustment (Snap Jump)
    float phaseErrorMs = 0.0f;           // Exact low-end phase lag in milliseconds
    bool isLowEndInPhase = false;       // True if phase error < 2ms (Kick-with-Kick 100% Match)
};

class BiquadLowPass150Hz {
public:
    BiquadLowPass150Hz() {
        reset();
        calculateCoefficients(44100.0f);
    }

    void calculateCoefficients(float sampleRate) {
        float f0 = 150.0f; // 150 Hz Low-Pass Cutoff (Sub-Bass / Kick)
        float Q = 0.707107f; // Butterworth Q
        float w0 = 2.0f * 3.14159265358979323846f * f0 / sampleRate;
        float alpha = std::sin(w0) / (2.0f * Q);

        float b0_tmp = (1.0f - std::cos(w0)) / 2.0f;
        float b1_tmp = 1.0f - std::cos(w0);
        float b2_tmp = (1.0f - std::cos(w0)) / 2.0f;
        float a0_tmp = 1.0f + alpha;
        float a1_tmp = -2.0f * std::cos(w0);
        float a2_tmp = 1.0f - alpha;

        b0_ = b0_tmp / a0_tmp;
        b1_ = b1_tmp / a0_tmp;
        b2_ = b2_tmp / a0_tmp;
        a1_ = a1_tmp / a0_tmp;
        a2_ = a2_tmp / a0_tmp;
    }

    inline float processSample(float input) {
        float output = b0_ * input + b1_ * x1_ + b2_ * x2_ - a1_ * y1_ - a2_ * y2_;
        x2_ = x1_;
        x1_ = input;
        y2_ = y1_;
        y1_ = output;
        return output;
    }

    void reset() {
        x1_ = x2_ = y1_ = y2_ = 0.0f;
    }

private:
    float b0_ = 0.0f, b1_ = 0.0f, b2_ = 0.0f;
    float a1_ = 0.0f, a2_ = 0.0f;
    float x1_ = 0.0f, x2_ = 0.0f, y1_ = 0.0f, y2_ = 0.0f;
};

class BiquadBandPassMidKit {
public:
    BiquadBandPassMidKit() {
        reset();
        calculateCoefficients(44100.0f);
    }

    void calculateCoefficients(float sampleRate) {
        float f0 = 800.0f; // 800 Hz Center Frequency (Snare / Clap / Percussion Kit)
        float Q = 0.5f;
        float w0 = 2.0f * 3.14159265358979323846f * f0 / sampleRate;
        float alpha = std::sin(w0) / (2.0f * Q);

        float b0_tmp = alpha;
        float b1_tmp = 0.0f;
        float b2_tmp = -alpha;
        float a0_tmp = 1.0f + alpha;
        float a1_tmp = -2.0f * std::cos(w0);
        float a2_tmp = 1.0f - alpha;

        b0_ = b0_tmp / a0_tmp;
        b1_ = b1_tmp / a0_tmp;
        b2_ = b2_tmp / a0_tmp;
        a1_ = a1_tmp / a0_tmp;
        a2_ = a2_tmp / a0_tmp;
    }

    inline float processSample(float input) {
        float output = b0_ * input + b1_ * x1_ + b2_ * x2_ - a1_ * y1_ - a2_ * y2_;
        x2_ = x1_;
        x1_ = input;
        y2_ = y1_;
        y1_ = output;
        return output;
    }

    void reset() {
        x1_ = x2_ = y1_ = y2_ = 0.0f;
    }

private:
    float b0_ = 0.0f, b1_ = 0.0f, b2_ = 0.0f;
    float a1_ = 0.0f, a2_ = 0.0f;
    float x1_ = 0.0f, x2_ = 0.0f, y1_ = 0.0f, y2_ = 0.0f;
};

class PhaseSyncEngine {
public:
    static constexpr size_t RING_BUFFER_SIZE = 2048; // Pre-allocated 2048 samples (~46ms at 44.1kHz)
    static constexpr int SEARCH_WINDOW_SAMPLES = 1323; // ±30 ms search range at 44.1 kHz
    static constexpr float SNAP_JUMP_THRESHOLD_MS = 10.0f; // Threshold above which instant Snap Jump triggers

    PhaseSyncEngine();
    ~PhaseSyncEngine() = default;

    void initialize(float sampleRate = 44100.0f);

    // Call from Real-Time Audio Render Callback (Zero Heap Allocation)
    void pushAudioBuffers(
        const float* masterBuffer,
        const float* slaveBuffer,
        size_t numFrames,
        int channels = 2
    );

    // Dual Sync Method: Instant Snap Jump + Continuous Pitch-Bend Lock
    SyncAdjustmentResult calculateSyncAdjustment(
        const MasterClockState& masterClock,
        int64_t slaveSamplePosition,
        double slaveBaseBpm,
        int64_t slaveFirstBeatFrame,
        bool isSyncTriggered = false
    );

    // Smart Crossover: Calculates Auto Low-EQ attenuation when both decks are live
    static void calculateSmartBassEQ(
        float masterVolume,
        float slaveVolume,
        float masterLowEq,
        float slaveLowEq,
        float& outMasterLowEq,
        float& outSlaveLowEq
    );

    // Multiplier for Half / Double BPM matching (e.g., 70 BPM vs 140 BPM)
    static float determineBpmMultiplier(double slaveBpm, double targetBpm);

private:
    float sampleRate_ = 44100.0f;

    // Pre-allocated static Ring Buffers for Low-Pass and Mid-Pass Filtered Audio Signals
    std::array<float, RING_BUFFER_SIZE> masterLowRing_{};
    std::array<float, RING_BUFFER_SIZE> slaveLowRing_{};
    std::array<float, RING_BUFFER_SIZE> masterMidRing_{};
    std::array<float, RING_BUFFER_SIZE> slaveMidRing_{};
    size_t ringIndex_ = 0;

    BiquadLowPass150Hz masterFilter_;
    BiquadLowPass150Hz slaveFilter_;
    BiquadBandPassMidKit masterMidFilter_;
    BiquadBandPassMidKit slaveMidFilter_;

    // Fast Cross-Correlation over ±30ms Pre-allocated Buffer
    int computeLowEndCrossCorrelationLag();

    // Micro-AI Transient Peak Matcher for Bass-on-Bass & Kit-on-Kit attack alignment
    int computeTransientOffset();
};

#endif // PHASE_SYNC_ENGINE_H
