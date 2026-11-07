#include "PhaseSyncEngine.h"

PhaseSyncEngine::PhaseSyncEngine() {
    initialize(44100.0f);
}

void PhaseSyncEngine::initialize(float sampleRate) {
    sampleRate_ = (sampleRate > 0.0f) ? sampleRate : 44100.0f;
    masterFilter_.calculateCoefficients(sampleRate_);
    slaveFilter_.calculateCoefficients(sampleRate_);
    masterMidFilter_.calculateCoefficients(sampleRate_);
    slaveMidFilter_.calculateCoefficients(sampleRate_);
    masterLowRing_.fill(0.0f);
    slaveLowRing_.fill(0.0f);
    masterMidRing_.fill(0.0f);
    slaveMidRing_.fill(0.0f);
    ringIndex_ = 0;
}

void PhaseSyncEngine::pushAudioBuffers(
    const float* masterBuffer,
    const float* slaveBuffer,
    size_t numFrames,
    int channels
) {
    if (numFrames == 0 || channels <= 0) return;

    for (size_t i = 0; i < numFrames; ++i) {
        // Mono downmix
        float masterMono = 0.0f;
        float slaveMono = 0.0f;

        if (masterBuffer != nullptr) {
            for (int c = 0; c < channels; ++c) {
                masterMono += masterBuffer[i * channels + c];
            }
            masterMono /= static_cast<float>(channels);
        }

        if (slaveBuffer != nullptr) {
            for (int c = 0; c < channels; ++c) {
                slaveMono += slaveBuffer[i * channels + c];
            }
            slaveMono /= static_cast<float>(channels);
        }

        // Apply Biquad Filters (150 Hz LowPass for Sub-Bass & 800 Hz BandPass for Percussion Kit)
        float masterLow = masterFilter_.processSample(masterMono);
        float slaveLow = slaveFilter_.processSample(slaveMono);

        float masterMid = masterMidFilter_.processSample(masterMono);
        float slaveMid = slaveMidFilter_.processSample(slaveMono);

        // Store into pre-allocated ring buffers (Zero Heap Allocation)
        masterLowRing_[ringIndex_] = masterLow;
        slaveLowRing_[ringIndex_] = slaveLow;
        masterMidRing_[ringIndex_] = masterMid;
        slaveMidRing_[ringIndex_] = slaveMid;

        ringIndex_ = (ringIndex_ + 1) % RING_BUFFER_SIZE;
    }
}

int PhaseSyncEngine::computeLowEndCrossCorrelationLag() {
    int maxLag = static_cast<int>(std::min<size_t>(SEARCH_WINDOW_SAMPLES, RING_BUFFER_SIZE / 3));
    double maxCorr = -1e9;
    int bestLagSamples = 0;

    size_t N = RING_BUFFER_SIZE / 2;

    // Real-time Fast Normalized Cross-Correlation (NCC)
    for (int lag = -maxLag; lag <= maxLag; lag += 2) {
        double sumProduct = 0.0;
        double sumMasterSq = 0.0;
        double sumSlaveSq = 0.0;

        for (size_t n = 0; n < N; ++n) {
            size_t idxMaster = (ringIndex_ + n) % RING_BUFFER_SIZE;
            int idxSlaveSigned = static_cast<int>(ringIndex_ + n + lag);
            while (idxSlaveSigned < 0) idxSlaveSigned += RING_BUFFER_SIZE;
            size_t idxSlave = static_cast<size_t>(idxSlaveSigned) % RING_BUFFER_SIZE;

            double mVal = static_cast<double>(masterLowRing_[idxMaster]);
            double sVal = static_cast<double>(slaveLowRing_[idxSlave]);

            sumProduct += mVal * sVal;
            sumMasterSq += mVal * mVal;
            sumSlaveSq += sVal * sVal;
        }

        double denom = std::sqrt(sumMasterSq * sumSlaveSq);
        double ncc = (denom > 1e-9) ? (sumProduct / denom) : 0.0;

        if (ncc > maxCorr) {
            maxCorr = ncc;
            bestLagSamples = lag;
        }
    }

    return bestLagSamples;
}

int PhaseSyncEngine::computeTransientOffset() {
    size_t N = RING_BUFFER_SIZE / 2;
    int bestLag = 0;
    float maxDerivativeMatch = -1e9f;

    int searchRange = static_cast<int>(SEARCH_WINDOW_SAMPLES / 2);
    for (int lag = -searchRange; lag <= searchRange; lag += 1) {
        float derivativeCorrelation = 0.0f;
        for (size_t n = 1; n < N; ++n) {
            size_t idxM = (ringIndex_ + n) % RING_BUFFER_SIZE;
            size_t idxM_prev = (ringIndex_ + n - 1) % RING_BUFFER_SIZE;

            int idxS_signed = static_cast<int>(ringIndex_ + n + lag);
            while (idxS_signed < 0) idxS_signed += RING_BUFFER_SIZE;
            size_t idxS = static_cast<size_t>(idxS_signed) % RING_BUFFER_SIZE;

            int idxS_prev_signed = static_cast<int>(idxS_signed - 1);
            while (idxS_prev_signed < 0) idxS_prev_signed += RING_BUFFER_SIZE;
            size_t idxS_prev = static_cast<size_t>(idxS_prev_signed) % RING_BUFFER_SIZE;

            // Combined Low (Kick) and Mid (Snare/Clap) derivative transients
            float dM_low = std::max(0.0f, masterLowRing_[idxM] - masterLowRing_[idxM_prev]);
            float dS_low = std::max(0.0f, slaveLowRing_[idxS] - slaveLowRing_[idxS_prev]);

            float dM_mid = std::max(0.0f, masterMidRing_[idxM] - masterMidRing_[idxM_prev]);
            float dS_mid = std::max(0.0f, slaveMidRing_[idxS] - slaveMidRing_[idxS_prev]);

            derivativeCorrelation += (0.6f * dM_low * dS_low + 0.4f * dM_mid * dS_mid);
        }

        if (derivativeCorrelation > maxDerivativeMatch) {
            maxDerivativeMatch = derivativeCorrelation;
            bestLag = lag;
        }
    }
    return bestLag;
}

float PhaseSyncEngine::determineBpmMultiplier(double slaveBpm, double targetBpm) {
    if (slaveBpm <= 0.0 || targetBpm <= 0.0) return 1.0f;
    double unityRatio = slaveBpm / targetBpm;
    double unityRatioSquare = unityRatio * unityRatio;
    if (unityRatioSquare > 2.0) {
        return 2.0f; // Slave is ~2x faster, target rate multiplied by 2 to align 1:2
    } else if (unityRatioSquare < 0.5) {
        return 0.5f; // Slave is ~0.5x slower, target rate multiplied by 0.5 to align 2:1
    }
    return 1.0f;
}

SyncAdjustmentResult PhaseSyncEngine::calculateSyncAdjustment(
    const MasterClockState& masterClock,
    int64_t slaveSamplePosition,
    double slaveBaseBpm,
    int64_t slaveFirstBeatFrame,
    bool isSyncTriggered
) {
    SyncAdjustmentResult result;

    if (masterClock.masterBaseBpm <= 0.0 || slaveBaseBpm <= 0.0 || sampleRate_ <= 0.0f) {
        return result;
    }

    // 1. TEMPO MATCHING with Half/Double Multiplier (Mixxx algorithm)
    double masterEffectiveBpm = masterClock.masterEffectiveBpm;
    if (masterEffectiveBpm <= 0.0) masterEffectiveBpm = masterClock.masterBaseBpm;

    float bpmMultiplier = determineBpmMultiplier(slaveBaseBpm, masterEffectiveBpm);
    double targetBpm = masterEffectiveBpm * static_cast<double>(bpmMultiplier);

    float basePitchRatio = static_cast<float>(targetBpm / slaveBaseBpm);
    result.pitchRatio = std::clamp(basePitchRatio, 0.5f, 2.0f);

    // 2. BEATGRID KICK-ON-KICK PHASE CALCULATION (1-Beat Snap Alignment)
    double masterFramesPerBeat = (60.0 / masterClock.masterBaseBpm) * static_cast<double>(sampleRate_);
    double slaveFramesPerBeat = (60.0 / slaveBaseBpm) * static_cast<double>(sampleRate_);

    // Master 1-Beat Kick Phase (in fraction 0.0 to 1.0)
    double masterRelSamples = static_cast<double>(masterClock.masterSamplePosition - masterClock.masterFirstBeatFrame);
    double masterBeatPhaseSamples = std::fmod(masterRelSamples, masterFramesPerBeat);
    if (masterBeatPhaseSamples < 0.0) masterBeatPhaseSamples += masterFramesPerBeat;
    double masterBeatPhaseRatio = masterBeatPhaseSamples / masterFramesPerBeat;

    // Slave 1-Beat Kick Phase (in fraction 0.0 to 1.0)
    double slaveRelSamples = static_cast<double>(slaveSamplePosition - slaveFirstBeatFrame);
    double slaveBeatPhaseSamples = std::fmod(slaveRelSamples, slaveFramesPerBeat);
    if (slaveBeatPhaseSamples < 0.0) slaveBeatPhaseSamples += slaveFramesPerBeat;
    double slaveBeatPhaseRatio = slaveBeatPhaseSamples / slaveFramesPerBeat;

    // Minimum Circular Beat Distance Ratio [-0.5, +0.5] within 1 Beat
    double beatPhaseDiffRatio = slaveBeatPhaseRatio - masterBeatPhaseRatio;
    while (beatPhaseDiffRatio > 0.5) beatPhaseDiffRatio -= 1.0;
    while (beatPhaseDiffRatio < -0.5) beatPhaseDiffRatio += 1.0;

    // Exact grid phase error in Slave audio file sample frames
    double gridPhaseErrorSamples = beatPhaseDiffRatio * slaveFramesPerBeat;

    // 3. MICRO TRANSIENT PEAK MATCHING & SUB-BASS CROSS-CORRELATION
    int transientLagSamples = computeTransientOffset();
    int lowEndLagSamples = computeLowEndCrossCorrelationLag();

    // Bounded fine-tuning window (max ±12ms) to prevent off-grid percussion from shifting the beat
    double maxTransientWindowSamples = 0.012 * static_cast<double>(sampleRate_);
    double transientCorr = std::clamp(
        0.50 * static_cast<double>(transientLagSamples) + 0.25 * static_cast<double>(lowEndLagSamples),
        -maxTransientWindowSamples,
        +maxTransientWindowSamples
    );

    double totalPhaseErrorSamples = gridPhaseErrorSamples + transientCorr;

    // 4. SYNC TRIGGERED (1-Shot Snap Jump on SYNC button press)
    if (isSyncTriggered) {
        result.sampleOffsetCorrection = static_cast<int64_t>(totalPhaseErrorSamples);
        result.microNudgeFactor = 1.0f;
        result.finalPlaybackRate = result.pitchRatio;
        result.phaseErrorMs = static_cast<float>((totalPhaseErrorSamples * 1000.0) / static_cast<double>(sampleRate_));
        result.isLowEndInPhase = (std::abs(result.phaseErrorMs) < 2.0f);
        return result;
    }

    // 5. CONTINUOUS CLOSED-LOOP PHASE LOCK (30 FPS)
    float phaseErrorMs = static_cast<float>((totalPhaseErrorSamples * 1000.0) / static_cast<double>(sampleRate_));
    result.phaseErrorMs = phaseErrorMs;
    result.isLowEndInPhase = (std::abs(phaseErrorMs) < 2.0f);

    // Auto Micro-Snap or Trainwreck recovery (Mixxx threshold = 0.2 beat / ~150ms or phase drift > 12ms)
    constexpr float TRAINWRECK_THRESHOLD_MS = 150.0f;
    constexpr float MAX_NUDGE_CAP = 0.05f; // Max ±5% smooth pitch bend adjustment (Mixxx kSyncAdjustmentCap)

    if (std::abs(phaseErrorMs) > 12.0f || std::abs(phaseErrorMs) > TRAINWRECK_THRESHOLD_MS) {
        result.sampleOffsetCorrection = static_cast<int64_t>(totalPhaseErrorSamples);
        result.microNudgeFactor = 1.0f;
    } else {
        result.sampleOffsetCorrection = 0;
        // Proportional pitch bend capped at MAX_NUDGE_CAP (±5%)
        float normalizedPhaseError = static_cast<float>(totalPhaseErrorSamples / slaveFramesPerBeat);
        float nudge = -std::clamp(normalizedPhaseError * 0.7f, -MAX_NUDGE_CAP, MAX_NUDGE_CAP);
        result.microNudgeFactor = 1.0f + nudge;
    }

    result.finalPlaybackRate = std::clamp(result.pitchRatio * result.microNudgeFactor, 0.5f, 2.0f);

    return result;
}

void PhaseSyncEngine::calculateSmartBassEQ(
    float masterVolume,
    float slaveVolume,
    float masterLowEq,
    float slaveLowEq,
    float& outMasterLowEq,
    float& outSlaveLowEq
) {
    outMasterLowEq = masterLowEq;
    outSlaveLowEq = slaveLowEq;

    // Smart Crossover: If both decks are active and both have heavy Low-EQ (>0.7f),
    // automatically attenuate non-dominant deck's bass by -3dB (0.707f) to prevent acoustic cancellation
    if (masterVolume > 0.3f && slaveVolume > 0.3f && masterLowEq > 0.7f && slaveLowEq > 0.7f) {
        if (masterVolume >= slaveVolume) {
            outSlaveLowEq = slaveLowEq * 0.707f; // Attenuate slave bass -3dB
        } else {
            outMasterLowEq = masterLowEq * 0.707f; // Attenuate master bass -3dB
        }
    }
}
