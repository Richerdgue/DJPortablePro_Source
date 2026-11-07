#ifndef AUDIO_ENGINE_H
#define AUDIO_ENGINE_H

#include <oboe/Oboe.h>
#include <atomic>
#include <cstdint>
#include <memory>
#include <vector>
#include "DeckAudioPlayer.h"
#include "MasterLimiter.h"
#include "PhaseSyncEngine.h"

enum class EngineState {
    UNINITIALIZED,
    INITIALIZED,
    RUNNING,
    STOPPED,
    RELEASED
};

class AudioEngine : public oboe::AudioStreamDataCallback, public oboe::AudioStreamErrorCallback {
public:
    AudioEngine();
    virtual ~AudioEngine();

    bool initialize(int32_t deviceTier);
    bool start();
    bool stop();
    void release();
    bool isInitialized() const;

    int32_t getTier() const { return deviceTier_; }

    /**
     * Sets the crossfader position (0.0 = Deck A, 1.0 = Deck B).
     */
    void setCrossfader(float position);

    // Deck Access (0 for Deck A, 1 for Deck B)
    DeckAudioPlayer* getDeck(int32_t deckIndex);

    // Sub-Bass Phase & Tempo Sync Adjustment via PhaseSyncEngine
    SyncAdjustmentResult calculateSyncAdjustment(int32_t slaveDeckIndex, double slaveBaseBpm, int64_t slaveFirstBeatFrame, bool isSyncTriggered);

    // Stream Properties
    int32_t getSampleRate() const;
    int32_t getChannelCount() const;
    int64_t getFramesProcessed() const;

    // oboe::AudioStreamDataCallback
    oboe::DataCallbackResult onAudioReady(
            oboe::AudioStream *audioStream,
            void *audioData,
            int32_t numFrames) override;

    // oboe::AudioStreamErrorCallback
    void onErrorAfterClose(oboe::AudioStream *audioStream, oboe::Result error) override;

private:
    std::shared_ptr<oboe::AudioStream> stream_;
    std::atomic<EngineState> state_{EngineState::UNINITIALIZED};
    std::atomic<int64_t> frames_processed_{0};

    DeckAudioPlayer deckA_;
    DeckAudioPlayer deckB_;
    PhaseSyncEngine phaseSyncEngine_;

    std::atomic<float> crossfaderPosition_{0.5f};
    int32_t deviceTier_ = 1; // Default to balanced

    MasterLimiter* masterLimiter_ = nullptr;

    // Pre-allocated mixing buffer for zero-allocation performance
    std::vector<float> mixingBuffer_;

    void updateState(EngineState newState);
    void logStreamConfig();
    bool openStream();
};

#endif // AUDIO_ENGINE_H
