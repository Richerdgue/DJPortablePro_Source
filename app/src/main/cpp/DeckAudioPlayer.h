#ifndef DECK_AUDIO_PLAYER_H
#define DECK_AUDIO_PLAYER_H

#include "AudioDecoder.h"
#include "RingBuffer.h"
#include "DspFilters.h"
#include "AudioEffects.h"
#include <atomic>
#include <thread>
#include <mutex>
#include <string>
#include <vector>

enum class PlayerState {
    EMPTY,
    LOADED,
    PLAYING,
    PAUSED,
    STOPPED
};

class DeckAudioPlayer {
public:
    DeckAudioPlayer();
    ~DeckAudioPlayer();

    bool load(const std::string& path);
    void play();
    void pause();
    void stop();
    void seek(int64_t frameIndex);
    void release();

    void setPlaybackRate(float rate);
    float getPlaybackRate() const { return playbackRate_.load(); }
    void setDspParams(float low, float mid, float high, float filter, float gain);
    void setVolume(float volume);

    // --- PRO DJ EFFECTS & ISOLATOR EQ ---
    void setEqGains(float low, float mid, float high) { isolatorEq_.setGains(low, mid, high); }
    void setEqKills(bool killLow, bool killMid, bool killHigh) { isolatorEq_.setKills(killLow, killMid, killHigh); }
    void setDelayParams(float beatFraction, float feedback, float wet, bool freeze) {
        float currentBpm = gridBpm_.load();
        if (currentBpm > 40.0f) {
            stereoDelay_.setBpmSync(currentBpm, beatFraction, feedback, wet, freeze);
            pingPongEcho_.setBpmSync(currentBpm, beatFraction, feedback, 0.70f, wet);
        } else {
            stereoDelay_.setParameters(0.5f, feedback, wet, freeze);
            pingPongEcho_.setBpmSync(128.0f, beatFraction, feedback, 0.70f, wet);
        }
    }
    void setReverbParams(float roomSize, float wet) { studioReverb_.setParameters(roomSize, wet); }
    void setBitcrusherParams(float factor, float wet) { bitcrusher_.setParameters(factor, wet); }
    void setBeatRollParams(float fraction, bool active) { beatRoll_.setRoll(gridBpm_.load(), fraction, active); }
    void setCompressorParams(float thresholdDb, float ratio, float attackMs, float releaseMs) {
        studioCompressor_.setParameters(thresholdDb, ratio, attackMs, releaseMs, true);
    }

    void setDeviceTier(int32_t tier);

    /**
     * Toggles scratch mode.
     */
    void setScratchMode(bool enabled);

    /**
     * Sets instantaneous scratch velocity (1.0 = normal forward).
     */
    void setScratchVelocity(float velocity);

    /**
     * Updates the beatgrid information for sync logic.
     */
    void setBeatGrid(float bpm, int64_t firstBeatFrame);

    /**
     * Returns the current phase of the beat (0.0 to 1.0).
     */
    double getBeatPhase() const;

    /**
     * Sets the downbeat offset within the current beat grid.
     */
    void setDownbeatPhase(double phase);

    /**
     * Returns the current downbeat offset in the beat cycle.
     */
    double getDownbeatPhase() const;

    /**
     * Aligns this deck's phase to match a target phase.
     */
    void alignPhase(double targetPhase);

    /**
     * Activates a loop between two points.
     */
    void setLoop(int64_t startFrame, int64_t endFrame, bool enabled);

    void render(float* outputBuffer, int32_t numFrames, bool additive = false);

    void setSyncActive(bool active) { isSyncActive_.store(active); }
    bool isSyncActive() const { return isSyncActive_.load(); }

    int64_t getPosition() const { return position_.load(); }
    int64_t getDuration() const { return totalFrames_; }
    int32_t getSampleRate() const { return sampleRate_; }
    int32_t getChannelCount() const { return channelCount_; }
    float getBpm() const { return gridBpm_.load(); }
    int64_t getFirstBeatFrame() const { return gridFirstBeatFrame_.load(); }

private:
    AudioDecoder* decoder_ = nullptr;
    RingBuffer* ringBuffer_ = nullptr;
    DeckDsp dsp_;

    // Pro DJ DSP Processor Instances (Adopted from Mixxx 2.5.6 Engine)
    ThreeBandIsolatorEq isolatorEq_;
    PingPongBeatEcho pingPongEcho_;
    StereoDelay stereoDelay_;
    StudioReverb studioReverb_;
    BitcrusherEffect bitcrusher_;
    BeatRollEffect beatRoll_;
    StudioCompressor studioCompressor_;

    // High-performance PCM Cache for Pro Pitch Transposition and Scratching
    std::vector<float> pcmData_;
    std::mutex pcmMutex_;
    std::atomic<int64_t> decodedSamplesCount_{0};
    std::atomic<bool> isFullyLoaded_{false};

    // Beatgrid data
    std::atomic<float> gridBpm_{128.0f};
    std::atomic<int64_t> gridFirstBeatFrame_{0};
    std::atomic<float> downbeatPhase_{0.0f};

    std::atomic<PlayerState> state_{PlayerState::EMPTY};
    std::atomic<int64_t> position_{0};

    int32_t sampleRate_ = 48000;
    int32_t channelCount_ = 0;
    int64_t totalFrames_ = 0;

    std::atomic<float> playbackRate_{1.0f};
    std::atomic<float> volume_{1.0f};
    std::atomic<float> scratchVelocity_{0.0f};
    std::atomic<bool> isScratching_{false};
    int32_t deviceTier_ = 2; // Default to Pro Tier 2 Studio Resampler across all devices

    std::atomic<bool> isSyncActive_{false};

    // Loop Data
    std::atomic<bool> isLooping_{false};
    std::atomic<int64_t> loopStartFrame_{0};
    std::atomic<int64_t> loopEndFrame_{0};

    double phaseAccumulator_ = 0.0;

    // Static buffer for rendering
    std::vector<float> renderBuffer_;
    std::vector<float> interpolationHistory_;

    std::atomic<bool> isThreadRunning_{false};
    std::thread decodingThread_;

    void decodingLoop();
    float interpolateCubic(float y_1, float y0, float y1, float y2, float x);
    float interpolateCubicSample(const float* pcmData, int64_t iPos, int channel, int channels, int64_t maxSampleIndex, float frac);
    float interpolateSinc8(const float* pcmData, int64_t iPos, int channel, int channels, int64_t maxSampleIndex, float frac);
    float interpolateLanczos16(const float* pcmData, int64_t iPos, int channel, int channels, int64_t maxSampleIndex, float frac, float rate);
};

#endif // DECK_AUDIO_PLAYER_H
