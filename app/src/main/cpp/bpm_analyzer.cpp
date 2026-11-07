#include "bpm_analyzer.h"
#include <android/log.h>
#include <algorithm>
#include <numeric>

#define LOG_TAG "BpmAnalyzer"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#ifdef USE_ESSENTIA
#include <essentia/algorithmfactory.h>
#include <essentia/essentia.h>
#endif

BpmAnalyzer::BpmAnalyzer() {
#ifdef USE_ESSENTIA
    essentia::init();
#endif
}

BpmAnalyzer::~BpmAnalyzer() {
    release();
#ifdef USE_ESSENTIA
    essentia::shutdown();
#endif
}

void BpmAnalyzer::init(int sampleRate, int channels) {
    std::lock_guard<std::mutex> lock(mBufferMutex);
    mSampleRate = (sampleRate > 0) ? sampleRate : 44100;
    mChannels = (channels > 0) ? channels : 2;
    mWindowSize = std::max(1, mSampleRate / 1000); // 1ms window

    mAudioBufferQueue.clear();
    mOnsetEnvelope.clear();
    mDetectedBpm.store(0.0f);
    mConfidence.store(0.0f);

    if (!mIsRunning.load()) {
        mIsRunning.store(true);
        mWorkerThread = std::thread(&BpmAnalyzer::workerThreadLoop, this);
    }
    LOGI("[BpmAnalyzer] Initialized: SR=%d, Ch=%d, WindowSize=%d", mSampleRate, mChannels, mWindowSize);
}

void BpmAnalyzer::pushPcmSamples(const float* samples, size_t count) {
    if (!samples || count == 0 || !mIsRunning.load()) return;

    {
        std::lock_guard<std::mutex> lock(mBufferMutex);
        // Limit queue size to ~10 seconds of stereo audio to prevent memory explosion
        size_t maxSamples = static_cast<size_t>(mSampleRate * mChannels * 10);
        if (mAudioBufferQueue.size() + count > maxSamples) {
            size_t dropCount = (mAudioBufferQueue.size() + count) - maxSamples;
            for (size_t i = 0; i < dropCount && !mAudioBufferQueue.empty(); ++i) {
                mAudioBufferQueue.pop_front();
            }
        }
        mAudioBufferQueue.insert(mAudioBufferQueue.end(), samples, samples + count);
    }
    mBufferCondVar.notify_one();
}

float BpmAnalyzer::getBpm() const {
    return mDetectedBpm.load();
}

float BpmAnalyzer::getConfidence() const {
    return mConfidence.load();
}

void BpmAnalyzer::reset() {
    std::lock_guard<std::mutex> lock(mBufferMutex);
    mAudioBufferQueue.clear();
    mOnsetEnvelope.clear();
    mDetectedBpm.store(0.0f);
    mConfidence.store(0.0f);
    mPrevLowEnergy = 0.0f;
    mPrevMidEnergy = 0.0f;
    mPrevHighEnergy = 0.0f;
}

void BpmAnalyzer::release() {
    if (mIsRunning.load()) {
        mIsRunning.store(false);
        mBufferCondVar.notify_all();
        if (mWorkerThread.joinable()) {
            mWorkerThread.join();
        }
    }
    std::lock_guard<std::mutex> lock(mBufferMutex);
    mAudioBufferQueue.clear();
    mOnsetEnvelope.clear();
}

void BpmAnalyzer::workerThreadLoop() {
    LOGI("[BpmAnalyzer] Background worker processing thread started");
    std::vector<float> chunkToProcess;

    // Process in chunks of ~2048 mono frames
    const size_t chunkSize = 2048 * mChannels;

    while (mIsRunning.load()) {
        {
            std::unique_lock<std::mutex> lock(mBufferMutex);
            mBufferCondVar.wait(lock, [this, chunkSize] {
                return !mIsRunning.load() || mAudioBufferQueue.size() >= chunkSize;
            });

            if (!mIsRunning.load()) break;

            size_t available = mAudioBufferQueue.size();
            chunkToProcess.resize(available);
            for (size_t i = 0; i < available; ++i) {
                chunkToProcess[i] = mAudioBufferQueue.front();
                mAudioBufferQueue.pop_front();
            }
        }

        if (!chunkToProcess.empty()) {
            processAudioChunk(chunkToProcess);
        }
    }
    LOGI("[BpmAnalyzer] Background worker processing thread stopped");
}

void BpmAnalyzer::processAudioChunk(const std::vector<float>& pcmData) {
    size_t numSamples = pcmData.size();
    size_t numFrames = numSamples / mChannels;

    // 2-pole Biquad Butterworth LowPass Filter @ 130 Hz (Q = 0.707107) for Kick Isolation
    struct BiquadLowPass130Hz {
        float b0 = 0.0f, b1 = 0.0f, b2 = 0.0f, a1 = 0.0f, a2 = 0.0f;
        float x1 = 0.0f, x2 = 0.0f, y1 = 0.0f, y2 = 0.0f;

        void init(float sr) {
            float f0 = 130.0f;
            float Q = 0.707107f;
            float w0 = 2.0f * 3.14159265358979323846f * f0 / sr;
            float alpha = std::sin(w0) / (2.0f * Q);
            float cosW0 = std::cos(w0);

            float b0_r = (1.0f - cosW0) / 2.0f;
            float b1_r = 1.0f - cosW0;
            float b2_r = (1.0f - cosW0) / 2.0f;
            float a0_r = 1.0f + alpha;
            float a1_r = -2.0f * cosW0;
            float a2_r = 1.0f - alpha;

            b0 = b0_r / a0_r; b1 = b1_r / a0_r; b2 = b2_r / a0_r;
            a1 = a1_r / a0_r; a2 = a2_r / a0_r;
            x1 = x2 = y1 = y2 = 0.0f;
        }

        inline float process(float in) {
            float out = b0 * in + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            x2 = x1; x1 = in; y2 = y1; y1 = out;
            return out;
        }
    };

    BiquadLowPass130Hz kickFilter;
    kickFilter.init(static_cast<float>(mSampleRate));

    float lowEnergyAcc = 0.0f;
    float midEnergyAcc = 0.0f;
    float highEnergyAcc = 0.0f;
    int samplesInWindow = 0;

    for (size_t f = 0; f < numFrames; ++f) {
        float mono = 0.0f;
        for (int c = 0; c < mChannels; ++c) {
            mono += std::abs(pcmData[f * mChannels + c]);
        }
        mono /= static_cast<float>(mChannels);

        // Filter bands via 130 Hz Biquad Butterworth
        float bassVal = kickFilter.process(mono);
        float bass = std::abs(bassVal);
        float mid = std::abs(mono - bassVal);
        float treble = std::abs(mono * 0.5f);

        lowEnergyAcc += bass * bass;
        midEnergyAcc += mid * mid;
        highEnergyAcc += treble * treble;
        samplesInWindow++;

        if (samplesInWindow >= mWindowSize) {
            float curLowEnergy = std::sqrt(lowEnergyAcc / static_cast<float>(mWindowSize));
            float curMidEnergy = std::sqrt(midEnergyAcc / static_cast<float>(mWindowSize));
            float curHighEnergy = std::sqrt(highEnergyAcc / static_cast<float>(mWindowSize));

            float fluxLow = std::max(0.0f, curLowEnergy - mPrevLowEnergy);
            float fluxMid = std::max(0.0f, curMidEnergy - mPrevMidEnergy);
            float fluxHigh = std::max(0.0f, curHighEnergy - mPrevHighEnergy);

            // Multi-band weighted spectral flux: Bass (40%) + Mid (40%) + Treble (20%)
            float combinedFlux = 0.40f * fluxLow + 0.40f * fluxMid + 0.20f * fluxHigh;
            float logFlux = std::log1p(10.0f * combinedFlux);

            mOnsetEnvelope.push_back(logFlux);

            mPrevLowEnergy = curLowEnergy;
            mPrevMidEnergy = curMidEnergy;
            mPrevHighEnergy = curHighEnergy;

            lowEnergyAcc = 0.0f;
            midEnergyAcc = 0.0f;
            highEnergyAcc = 0.0f;
            samplesInWindow = 0;
        }
    }

    // Maintain a rolling envelope of ~5 seconds (~5000 window points at 1000 Hz)
    const size_t maxEnvelopePoints = 5000;
    if (mOnsetEnvelope.size() > maxEnvelopePoints) {
        size_t excess = mOnsetEnvelope.size() - maxEnvelopePoints;
        mOnsetEnvelope.erase(mOnsetEnvelope.begin(), mOnsetEnvelope.begin() + excess);
    }

    // Trigger tempo estimation every ~2 seconds of audio onset data
    if (mOnsetEnvelope.size() >= 2000) {
        float bpm = computeBpmFromOnsetEnvelope(mOnsetEnvelope);
        if (bpm > 50.0f && bpm < 220.0f) {
            mDetectedBpm.store(bpm);
            mConfidence.store(0.85f);
        }
    }
}

float BpmAnalyzer::computeBpmFromOnsetEnvelope(const std::vector<float>& onsetEnv) {
#ifdef USE_ESSENTIA
    // Essentia RhythmExtractor2013 Integration
    using namespace essentia;
    using namespace essentia::standard;

    AlgorithmFactory& factory = AlgorithmFactory::instance();
    Algorithm* rhythmExtractor = factory.create("RhythmExtractor2013", "method", "multifeature");

    rhythmExtractor->input("signal").set(onsetEnv);

    Real bpmResult = 0.0;
    std::vector<Real> ticks, estimates, bpmIntervals;
    Real confidenceResult = 0.0;

    rhythmExtractor->output("bpm").set(bpmResult);
    rhythmExtractor->output("ticks").set(ticks);
    rhythmExtractor->output("estimates").set(estimates);
    rhythmExtractor->output("bpmIntervals").set(bpmIntervals);
    rhythmExtractor->output("confidence").set(confidenceResult);

    rhythmExtractor->compute();
    delete rhythmExtractor;

    mConfidence.store(static_cast<float>(confidenceResult));
    return static_cast<float>(bpmResult);
#else
    // Pure High-Precision C++ Sub-Decimal Comb Filter BPM Estimator
    double windowRate = static_cast<double>(mSampleRate) / static_cast<double>(mWindowSize); // ~1000 Hz
    size_t N = onsetEnv.size();
    if (N < 500) return 0.0f;

    double maxEnergy = 0.0;
    double bestBpm = 0.0;

    // Scan standard DJ tempo range 60.0 to 180.0 BPM in 0.5 BPM steps
    for (double bpmTest = 60.0; bpmTest <= 180.0; bpmTest += 0.5) {
        double periodSamples = (60.0 / bpmTest) * windowRate;
        double energy = 0.0;

        for (int pulse = 1; pulse <= 8; ++pulse) {
            double lag = pulse * periodSamples;
            size_t lagIdx = static_cast<size_t>(std::round(lag));
            if (lagIdx >= N) break;

            double corr = 0.0;
            for (size_t i = 0; i < N - lagIdx; ++i) {
                corr += onsetEnv[i] * onsetEnv[i + lagIdx];
            }
            energy += corr / static_cast<double>(N - lagIdx);
        }

            double diff = (bpmTest - 122.0) / 50.0;
            double gaussianPrior = std::exp(-0.5 * diff * diff);
            double weightedEnergy = energy * (0.65 + 0.35 * gaussianPrior);

            if (weightedEnergy > maxEnergy) {
                maxEnergy = weightedEnergy;
                bestBpm = bpmTest;
            }
        }

    // Refine around best BPM with 0.1 BPM step precision
    if (bestBpm > 0.0) {
        double refinedBestBpm = bestBpm;
        double refinedMaxEnergy = maxEnergy;

        for (double bpmTest = bestBpm - 1.0; bpmTest <= bestBpm + 1.0; bpmTest += 0.1) {
            double periodSamples = (60.0 / bpmTest) * windowRate;
            double energy = 0.0;

            for (int pulse = 1; pulse <= 8; ++pulse) {
                double lag = pulse * periodSamples;
                size_t lagIdx = static_cast<size_t>(std::round(lag));
                if (lagIdx >= N) break;

                double corr = 0.0;
                for (size_t i = 0; i < N - lagIdx; ++i) {
                    corr += onsetEnv[i] * onsetEnv[i + lagIdx];
                }
                energy += corr / static_cast<double>(N - lagIdx);
            }

            if (energy > refinedMaxEnergy) {
                refinedMaxEnergy = energy;
                refinedBestBpm = bpmTest;
            }
        }
        bestBpm = refinedBestBpm;
    }

    return static_cast<float>(bestBpm);
#endif
}