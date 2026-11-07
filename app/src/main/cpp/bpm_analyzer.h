#ifndef BPM_ANALYZER_H
#define BPM_ANALYZER_H

#include <vector>
#include <deque>
#include <mutex>
#include <thread>
#include <condition_variable>
#include <atomic>
#include <cmath>

/**
 * High-Performance Real-Time C++ Audio DSP BPM Analyzer
 * Architected for Android NDK & Essentia Integration.
 * Uses a non-blocking Ring Buffer and background processing worker thread
 * to analyze incoming PCM audio streams with zero UI thread lag.
 */
class BpmAnalyzer {
public:
    BpmAnalyzer();
    ~BpmAnalyzer();

    /**
     * Initializes the BPM analyzer engine.
     * @param sampleRate Audio sample rate (e.g. 44100 Hz or 48000 Hz)
     * @param channels Number of channels (1 = Mono, 2 = Stereo)
     */
    void init(int sampleRate, int channels);

    /**
     * Pushes audio PCM floating-point samples to native processing queue.
     * Non-blocking call designed for high-throughput real-time streaming.
     */
    void pushPcmSamples(const float* samples, size_t count);

    /**
     * Gets the current detected BPM.
     */
    float getBpm() const;

    /**
     * Gets the confidence metric of the current BPM detection (0.0 to 1.0).
     */
    float getConfidence() const;

    /**
     * Resets the internal state and buffer queues.
     */
    void reset();

    /**
     * Releases native resources and stops background processing thread.
     */
    void release();

private:
    void workerThreadLoop();
    void processAudioChunk(const std::vector<float>& pcmData);
    float computeBpmFromOnsetEnvelope(const std::vector<float>& onsetEnv);

    int mSampleRate = 44100;
    int mChannels = 2;

    std::atomic<float> mDetectedBpm{0.0f};
    std::atomic<float> mConfidence{0.0f};
    std::atomic<bool> mIsRunning{false};

    // Queue for thread-safe audio PCM streaming
    std::deque<float> mAudioBufferQueue;
    mutable std::mutex mBufferMutex;
    std::condition_variable mBufferCondVar;

    std::thread mWorkerThread;

    // Window size (~1ms onset resolution)
    int mWindowSize = 441;
    std::vector<float> mOnsetEnvelope;

    // Previous band energies for spectral flux
    float mPrevLowEnergy = 0.0f;
    float mPrevMidEnergy = 0.0f;
    float mPrevHighEnergy = 0.0f;
};

#endif // BPM_ANALYZER_H
