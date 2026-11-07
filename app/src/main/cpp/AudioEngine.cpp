#include "AudioEngine.h"
#include <android/log.h>
#include <algorithm>
#include <cmath>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

#define TAG "AudioEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

AudioEngine::AudioEngine() {
    LOGI("[ENGINE_INIT] AudioEngine instance created");
    mixingBuffer_.assign(4096 * 2, 0.0f);
}

AudioEngine::~AudioEngine() {
    release();
    if (masterLimiter_) {
        delete masterLimiter_;
        masterLimiter_ = nullptr;
    }
    LOGI("[ENGINE_RELEASE] AudioEngine instance destroyed");
}

bool AudioEngine::initialize(int32_t tier) {
    if (isInitialized()) return true;
    deviceTier_ = tier;

    deckA_.setDeviceTier(tier);
    deckB_.setDeviceTier(tier);

    if (!openStream()) {
        LOGE("[ENGINE_ERROR] Failed to initialize audio stream");
        return false;
    }

    updateState(EngineState::INITIALIZED);
    LOGI("[ENGINE_INIT] AudioEngine initialized successfully");
    return true;
}

bool AudioEngine::openStream() {
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
           ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
           ->setSharingMode(oboe::SharingMode::Exclusive)
           ->setFormat(oboe::AudioFormat::Float)
           ->setChannelCount(oboe::ChannelCount::Stereo)
           ->setDataCallback(this)
           ->setErrorCallback(this);

    oboe::Result result = builder.openStream(stream_);

    if (result != oboe::Result::OK) {
        LOGW("[ENGINE_STREAM_OPEN] Failed to open exclusive stream: %s. Retrying in shared mode...", oboe::convertToText(result));
        builder.setSharingMode(oboe::SharingMode::Shared);
        result = builder.openStream(stream_);
    }

    if (result != oboe::Result::OK) {
        LOGE("[ENGINE_ERROR] Failed to open audio stream: %s", oboe::convertToText(result));
        return false;
    }

    logStreamConfig();

    int32_t channelCount = stream_->getChannelCount();
    int32_t capacityFrames = stream_->getBufferCapacityInFrames();
    size_t requiredSize = static_cast<size_t>((capacityFrames > 0 ? capacityFrames : 4096) * (channelCount > 0 ? channelCount : 2) * 2);
    if (requiredSize < 8192) requiredSize = 8192;
    mixingBuffer_.assign(requiredSize, 0.0f);

    if (masterLimiter_) delete masterLimiter_;
    masterLimiter_ = new MasterLimiter((float)stream_->getSampleRate());
    phaseSyncEngine_.initialize((float)stream_->getSampleRate());

    return true;
}

void AudioEngine::logStreamConfig() {
    if (stream_) {
        LOGI("[ENGINE_STREAM_CONFIG] Sample Rate: %d", stream_->getSampleRate());
        LOGI("[ENGINE_STREAM_CONFIG] Channels: %d", stream_->getChannelCount());
        LOGI("[ENGINE_STREAM_CONFIG] Performance Mode: %s", oboe::convertToText(stream_->getPerformanceMode()));
        LOGI("[ENGINE_STREAM_CONFIG] Sharing Mode: %s", oboe::convertToText(stream_->getSharingMode()));
    }
}

bool AudioEngine::start() {
    if (state_ == EngineState::RUNNING) return true;

    if (stream_) {
        oboe::Result result = stream_->requestStart();
        if (result != oboe::Result::OK) {
            LOGE("[ENGINE_ERROR] Failed to start stream: %s", oboe::convertToText(result));
            return false;
        }
    }

    updateState(EngineState::RUNNING);
    LOGI("[ENGINE_START] AudioEngine running");
    return true;
}

bool AudioEngine::stop() {
    if (state_ != EngineState::RUNNING) return true;

    if (stream_) {
        oboe::Result result = stream_->requestStop();
        if (result != oboe::Result::OK) {
            LOGE("[ENGINE_ERROR] Failed to stop stream: %s", oboe::convertToText(result));
        }
    }

    updateState(EngineState::STOPPED);
    LOGI("[ENGINE_STOP] AudioEngine stopped");
    return true;
}

void AudioEngine::release() {
    if (state_ == EngineState::RELEASED) return;

    deckA_.release();
    deckB_.release();

    if (stream_) {
        stream_->close();
        stream_.reset();
    }

    updateState(EngineState::RELEASED);
    frames_processed_ = 0;
    LOGI("[ENGINE_RELEASE] Resources released");
}

bool AudioEngine::isInitialized() const {
    EngineState current = state_.load();
    return current != EngineState::UNINITIALIZED && current != EngineState::RELEASED;
}

void AudioEngine::setCrossfader(float position) {
    crossfaderPosition_.store(std::clamp(position, 0.0f, 1.0f));
}

DeckAudioPlayer* AudioEngine::getDeck(int32_t deckIndex) {
    return (deckIndex == 0) ? &deckA_ : &deckB_;
}

int32_t AudioEngine::getSampleRate() const { return stream_ ? stream_->getSampleRate() : 0; }
int32_t AudioEngine::getChannelCount() const { return stream_ ? stream_->getChannelCount() : 0; }
int64_t AudioEngine::getFramesProcessed() const { return frames_processed_.load(); }

oboe::DataCallbackResult AudioEngine::onAudioReady(
        oboe::AudioStream *audioStream,
        void *audioData,
        int32_t numFrames) {

    if (!audioData) return oboe::DataCallbackResult::Stop;

    int32_t numChannels = audioStream->getChannelCount();
    auto *output = static_cast<float*>(audioData);

    // 1. Calculate Crossfader Gains (Constant Power Curve)
    float xFade = crossfaderPosition_.load();
    float gainA = cosf(xFade * M_PI / 2.0f);
    float gainB = sinf(xFade * M_PI / 2.0f);

    // 2. Mix Deck A (into clean buffer)
    int32_t totalSamples = numFrames * numChannels;
    if (static_cast<size_t>(totalSamples) > mixingBuffer_.size()) {
        mixingBuffer_.resize(static_cast<size_t>(totalSamples * 2), 0.0f);
    }

    for (int i = 0; i < totalSamples; ++i) output[i] = 0.0f;
    deckA_.render(output, numFrames, false);

    // 3. Mix Deck B (Additive using pre-allocated buffer)
    float* tempBuffer = mixingBuffer_.data();
    for (int i = 0; i < totalSamples; ++i) tempBuffer[i] = 0.0f;
    deckB_.render(tempBuffer, numFrames, false);

    // 4. Feed Low-Pass (<150 Hz) Filtered Signals into PhaseSyncEngine for Sub-Bass Correlation
    phaseSyncEngine_.pushAudioBuffers(output, tempBuffer, static_cast<size_t>(numFrames), numChannels);

    // Apply Crossfader Gains
    for (int i = 0; i < numFrames * numChannels; ++i) {
        output[i] = output[i] * gainA + tempBuffer[i] * gainB;
    }

    // 4. Apply Master Limiter (Brick-wall protection)
    if (masterLimiter_) {
        masterLimiter_->process(output, numFrames, numChannels);
    }

    static int logTicker = 0;
    if (++logTicker % 200 == 0) {
        float maxAmp = 0.0f;
        for (int i = 0; i < numFrames * numChannels; ++i) {
            maxAmp = std::max(maxAmp, std::abs(output[i]));
        }
        LOGI("[AUDIO_READY] Frames=%d, maxAmp=%.4f, xFade=%.2f, DeckA_Pos=%lld, DeckB_Pos=%lld",
             numFrames, maxAmp, xFade, deckA_.getPosition(), deckB_.getPosition());
    }

    frames_processed_ += numFrames;
    return oboe::DataCallbackResult::Continue;
}

void AudioEngine::onErrorAfterClose(oboe::AudioStream *audioStream, oboe::Result error) {
    LOGW("[ENGINE_DISCONNECT] Audio stream error: %s", oboe::convertToText(error));
    updateState(EngineState::STOPPED);
}

void AudioEngine::updateState(EngineState newState) {
    state_ = newState;
}

SyncAdjustmentResult AudioEngine::calculateSyncAdjustment(int32_t slaveDeckIndex, double slaveBaseBpm, int64_t slaveFirstBeatFrame, bool isSyncTriggered) {
    int32_t masterDeckIndex = (slaveDeckIndex == 0) ? 1 : 0;
    DeckAudioPlayer* masterDeck = getDeck(masterDeckIndex);
    DeckAudioPlayer* slaveDeck = getDeck(slaveDeckIndex);

    MasterClockState masterClock;
    masterClock.masterSamplePosition = masterDeck->getPosition();
    float masterBaseBpmVal = masterDeck->getBpm();
    if (masterBaseBpmVal <= 0.0f) masterBaseBpmVal = 128.0f;
    masterClock.masterBaseBpm = static_cast<double>(masterBaseBpmVal);
    masterClock.masterEffectiveBpm = masterClock.masterBaseBpm * static_cast<double>(masterDeck->getPlaybackRate());
    masterClock.masterFirstBeatFrame = masterDeck->getFirstBeatFrame();
    masterClock.masterBeatPhase = masterDeck->getBeatPhase();

    SyncAdjustmentResult res = phaseSyncEngine_.calculateSyncAdjustment(
        masterClock,
        slaveDeck->getPosition(),
        slaveBaseBpm,
        slaveFirstBeatFrame,
        isSyncTriggered
    );

    // Atomic C++ seek execution on correction trigger (0-latency instant snap)
    if (res.sampleOffsetCorrection != 0) {
        int64_t currentPos = slaveDeck->getPosition();
        int64_t targetPos = std::max(static_cast<int64_t>(0), currentPos - res.sampleOffsetCorrection);
        slaveDeck->seek(targetPos);
    }

    return res;
}
