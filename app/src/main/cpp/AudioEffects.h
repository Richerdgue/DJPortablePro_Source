#ifndef AUDIO_EFFECTS_H
#define AUDIO_EFFECTS_H

#include <cmath>
#include <vector>
#include <algorithm>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

/**
 * Professional DJ Audio Effects Processor Suite (Isolator EQ, BPM Delay, Freeverb, Bitcrusher, BeatRoll).
 * Inspired by Mixxx 2.5.6 DSP & Linkwitz-Riley Crossover Architecture.
 * Optimized for real-time Oboe audio streaming with zero memory allocation in the processing loop.
 */

// --- LINKWITZ-RILEY 4TH ORDER / BIQUAD CROSSOVER FILTER ---
class LinkwitzBiquadFilter {
public:
    enum Type { LOWPASS, HIGHPASS, BANDPASS };

    LinkwitzBiquadFilter() { reset(); }

    void setSampleRate(float sr) {
        sampleRate_ = sr;
    }

    void setParameters(Type type, float cutoffHz, float qFactor = 0.707107f) {
        type_ = type;
        cutoff_ = std::clamp(cutoffHz, 20.0f, sampleRate_ * 0.49f);
        q_ = std::max(0.1f, qFactor);
        calculateCoefficients();
    }

    void reset() {
        x1_ = x2_ = y1_ = y2_ = 0.0f;
        x1_r_ = x2_r_ = y1_r_ = y2_r_ = 0.0f;
    }

    inline float processSample(float sample, bool isRightChannel = false) {
        float& x1 = isRightChannel ? x1_r_ : x1_;
        float& x2 = isRightChannel ? x2_r_ : x2_;
        float& y1 = isRightChannel ? y1_r_ : y1_;
        float& y2 = isRightChannel ? y2_r_ : y2_;

        float output = b0_ * sample + b1_ * x1 + b2_ * x2 - a1_ * y1 - a2_ * y2;

        x2 = x1;
        x1 = sample;
        y2 = y1;
        y1 = output;

        return output;
    }

private:
    void calculateCoefficients() {
        float w0 = 2.0f * static_cast<float>(M_PI) * cutoff_ / sampleRate_;
        float cosW0 = std::cos(w0);
        float sinW0 = std::sin(w0);
        float alpha = sinW0 / (2.0f * q_);

        float a0 = 1.0f;
        if (type_ == LOWPASS) {
            b0_ = (1.0f - cosW0) * 0.5f;
            b1_ = 1.0f - cosW0;
            b2_ = (1.0f - cosW0) * 0.5f;
            a0 = 1.0f + alpha;
            a1_ = -2.0f * cosW0;
            a2_ = 1.0f - alpha;
        } else if (type_ == HIGHPASS) {
            b0_ = (1.0f + cosW0) * 0.5f;
            b1_ = -(1.0f + cosW0);
            b2_ = (1.0f + cosW0) * 0.5f;
            a0 = 1.0f + alpha;
            a1_ = -2.0f * cosW0;
            a2_ = 1.0f - alpha;
        } else { // BANDPASS
            b0_ = alpha;
            b1_ = 0.0f;
            b2_ = -alpha;
            a0 = 1.0f + alpha;
            a1_ = -2.0f * cosW0;
            a2_ = 1.0f - alpha;
        }

        b0_ /= a0;
        b1_ /= a0;
        b2_ /= a0;
        a1_ /= a0;
        a2_ /= a0;
    }

    Type type_ = LOWPASS;
    float sampleRate_ = 44100.0f;
    float cutoff_ = 20000.0f;
    float q_ = 0.707107f;

    float b0_ = 1.0f, b1_ = 0.0f, b2_ = 0.0f, a1_ = 0.0f, a2_ = 0.0f;
    float x1_ = 0.0f, x2_ = 0.0f, y1_ = 0.0f, y2_ = 0.0f;
    float x1_r_ = 0.0f, x2_r_ = 0.0f, y1_r_ = 0.0f, y2_r_ = 0.0f;
};

// --- MOOG LADDER 4-POLE ANALOG DJ FILTER (ADOPTED FROM MIXXX 2.5.6 ENGINE) ---
class MoogLadder4Filter {
public:
    MoogLadder4Filter() {
        reset();
    }

    void setSampleRate(float sr) {
        sampleRate_ = (sr > 0.0f) ? sr : 48000.0f;
    }

    void reset() {
        std::fill(azt1_, azt1_ + 2, 0.0f);
        std::fill(azt2_, azt2_ + 2, 0.0f);
        std::fill(azt3_, azt3_ + 2, 0.0f);
        std::fill(azt4_, azt4_ + 2, 0.0f);
        std::fill(az5_, az5_ + 2, 0.0f);
    }

    void setParameters(float cutoffHz, float resonance = 1.35f, bool isHighPass = false) {
        cutoff_ = std::clamp(cutoffHz, 20.0f, sampleRate_ * 0.49f);
        resonance_ = std::clamp(resonance, 0.0f, 3.8f);
        isHighPass_ = isHighPass;

        constexpr float kVt = 1.2f;
        constexpr float v2 = 2.0f + kVt;

        float kfc = cutoff_ / sampleRate_;
        float kfcr = 1.8730f * (kfc * kfc * kfc) + 0.4955f * (kfc * kfc) - 0.6490f * kfc + 0.9988f;

        float x = -2.0f * static_cast<float>(M_PI) * kfcr * kfc;
        float exp_out = std::exp(x);
        k2vg_ = v2 * (1.0f - exp_out);

        kacr_ = resonance_ * (-3.9364f * (kfc * kfc) + 1.8409f * kfc + 0.9968f);
    }

    inline float processSample(float input, bool isRightChannel = false) {
        int ch = isRightChannel ? 1 : 0;
        constexpr float kVt = 1.2f;
        constexpr float k1_Vt = 1.0f / kVt;

        auto tanhApprox = [](float x) {
            float x2 = x * x;
            return x * (27.0f + x2) / (27.0f + 9.0f * x2);
        };

        float inputWithFeedback = input - kacr_ * az5_[ch];
        float stage0 = tanhApprox(inputWithFeedback * k1_Vt);

        float stage1 = azt1_[ch] + k2vg_ * (stage0 - azt1_[ch]);
        azt1_[ch] = stage1;

        float stage2 = azt2_[ch] + k2vg_ * (stage1 - azt2_[ch]);
        azt2_[ch] = stage2;

        float stage3 = azt3_[ch] + k2vg_ * (stage2 - azt3_[ch]);
        azt3_[ch] = stage3;

        float stage4 = azt4_[ch] + k2vg_ * (stage3 - azt4_[ch]);
        azt4_[ch] = stage4;

        az5_[ch] = stage4;

        if (isHighPass_) {
            return input - stage4;
        }

        return stage4;
    }

private:
    float sampleRate_ = 48000.0f;
    float cutoff_ = 10000.0f;
    float resonance_ = 1.35f;
    bool isHighPass_ = false;

    float k2vg_ = 0.0f;
    float kacr_ = 0.0f;

    float azt1_[2] = {0.0f, 0.0f};
    float azt2_[2] = {0.0f, 0.0f};
    float azt3_[2] = {0.0f, 0.0f};
    float azt4_[2] = {0.0f, 0.0f};
    float az5_[2] = {0.0f, 0.0f};
};

// --- ETAPA 1: 3-BAND ISOLATOR EQ CON FULL KILL (LINKWITZ-RILEY CROSSOVER) ---
class ThreeBandIsolatorEq {
public:
    ThreeBandIsolatorEq(float sampleRate = 44100.0f) : sampleRate_(sampleRate) {
        initFilters();
    }

    void setSampleRate(float sr) {
        sampleRate_ = (sr > 0.0f) ? sr : 44100.0f;
        initFilters();
    }

    void setGains(float lowGain, float midGain, float highGain) {
        lowGain_ = std::clamp(lowGain, 0.0f, 2.0f);
        midGain_ = std::clamp(midGain, 0.0f, 2.0f);
        highGain_ = std::clamp(highGain, 0.0f, 2.0f);
    }

    void setKills(bool killLow, bool killMid, bool killHigh) {
        killLow_ = killLow;
        killMid_ = killMid;
        killHigh_ = killHigh;
    }

    void reset() {
        lowLp1_.reset(); lowLp2_.reset();
        highHp1_.reset(); highHp2_.reset();
        midLp1_.reset(); midLp2_.reset();
        midHp1_.reset(); midHp2_.reset();
    }

    void process(float* buffer, int32_t numFrames, int32_t channels) {
        // Map 0.0 to 2.0 fader level to Linkwitz-Riley gain multipliers:
        // 0.0 -> 0.0x (-24dB/octave Total Kill)
        // 1.0 -> 1.0x (0dB Flat Crossover Pass-Through)
        // 2.0 -> 3.981x (+12dB Clean Boost)
        auto faderToGain = [](float val) {
            if (val <= 0.005f) return 0.0f;
            if (val <= 1.0f) return val;
            return 1.0f + (val - 1.0f) * 2.981f; // +12dB boost @ 2.0
        };

        float gLow = killLow_ ? 0.0f : faderToGain(lowGain_);
        float gMid = killMid_ ? 0.0f : faderToGain(midGain_);
        float gHigh = killHigh_ ? 0.0f : faderToGain(highGain_);

        // Fast-path: If neutral EQ (1.0) and no kills, pass through transparently
        if (gLow == 1.0f && gMid == 1.0f && gHigh == 1.0f && !killLow_ && !killMid_ && !killHigh_) {
            return;
        }

        for (int i = 0; i < numFrames; ++i) {
            for (int c = 0; c < channels; ++c) {
                int idx = i * channels + c;
                bool isRight = (c == 1);
                float sample = buffer[idx];

                // Low Band (Cascaded 2-stage Lowpass @ 250 Hz for 4th Order Linkwitz-Riley)
                float lowSample = lowLp2_.processSample(lowLp1_.processSample(sample, isRight), isRight);

                // High Band (Cascaded 2-stage Highpass @ 2500 Hz for 4th Order Linkwitz-Riley)
                float highSample = highHp2_.processSample(highHp1_.processSample(sample, isRight), isRight);

                // Mid Band (Bandpass complement: Highpass @ 250 Hz -> Lowpass @ 2500 Hz)
                float midSample = midLp2_.processSample(midLp1_.processSample(
                                    midHp2_.processSample(midHp1_.processSample(sample, isRight), isRight),
                                    isRight), isRight);

                buffer[idx] = lowSample * gLow + midSample * gMid + highSample * gHigh;
            }
        }
    }

private:
    void initFilters() {
        // Crossover Low/Mid @ 250 Hz
        lowLp1_.setSampleRate(sampleRate_); lowLp1_.setParameters(LinkwitzBiquadFilter::LOWPASS, 250.0f, 0.707107f);
        lowLp2_.setSampleRate(sampleRate_); lowLp2_.setParameters(LinkwitzBiquadFilter::LOWPASS, 250.0f, 0.707107f);

        midHp1_.setSampleRate(sampleRate_); midHp1_.setParameters(LinkwitzBiquadFilter::HIGHPASS, 250.0f, 0.707107f);
        midHp2_.setSampleRate(sampleRate_); midHp2_.setParameters(LinkwitzBiquadFilter::HIGHPASS, 250.0f, 0.707107f);

        // Crossover Mid/High @ 2500 Hz
        midLp1_.setSampleRate(sampleRate_); midLp1_.setParameters(LinkwitzBiquadFilter::LOWPASS, 2500.0f, 0.707107f);
        midLp2_.setSampleRate(sampleRate_); midLp2_.setParameters(LinkwitzBiquadFilter::LOWPASS, 2500.0f, 0.707107f);

        highHp1_.setSampleRate(sampleRate_); highHp1_.setParameters(LinkwitzBiquadFilter::HIGHPASS, 2500.0f, 0.707107f);
        highHp2_.setSampleRate(sampleRate_); highHp2_.setParameters(LinkwitzBiquadFilter::HIGHPASS, 2500.0f, 0.707107f);
    }

    float sampleRate_ = 44100.0f;
    float lowGain_ = 1.0f;
    float midGain_ = 1.0f;
    float highGain_ = 1.0f;
    bool killLow_ = false;
    bool killMid_ = false;
    bool killHigh_ = false;

    LinkwitzBiquadFilter lowLp1_, lowLp2_;
    LinkwitzBiquadFilter midHp1_, midHp2_;
    LinkwitzBiquadFilter midLp1_, midLp2_;
    LinkwitzBiquadFilter highHp1_, highHp2_;
};

// --- ADOPTED FROM MIXXX 2.5.6: PING-PONG STEREO BEAT ECHO WITH QUANTIZED BPM SYNC ---
class PingPongBeatEcho {
public:
    PingPongBeatEcho(float sampleRate = 48000.0f, float maxSeconds = 3.0f) {
        sampleRate_ = (sampleRate > 0.0f) ? sampleRate : 48000.0f;
        maxSamples_ = static_cast<int>(sampleRate_ * maxSeconds);
        leftBuffer_.assign(maxSamples_, 0.0f);
        rightBuffer_.assign(maxSamples_, 0.0f);
        writeIndex_ = 0;
        delaySamples_ = static_cast<int>(sampleRate_ * 0.5f);
        feedback_ = 0.45f;
        pingpong_ = 0.70f;
        wet_ = 0.0f;
        dampL_.setSampleRate(sampleRate_);
        dampR_.setSampleRate(sampleRate_);
        dampL_.setParameters(LinkwitzBiquadFilter::LOWPASS, 4500.0f, 0.707f);
        dampR_.setParameters(LinkwitzBiquadFilter::LOWPASS, 4500.0f, 0.707f);
    }

    void setSampleRate(float sr) {
        sampleRate_ = (sr > 0.0f) ? sr : 48000.0f;
        dampL_.setSampleRate(sampleRate_);
        dampR_.setSampleRate(sampleRate_);
    }

    void setBpmSync(float bpm, float beatFraction, float feedback = 0.45f, float pingpong = 0.70f, float wet = 0.35f) {
        if (bpm > 40.0f && bpm < 250.0f) {
            float beatSec = (60.0f / bpm) * beatFraction;
            delaySamples_ = std::clamp(static_cast<int>(sampleRate_ * beatSec), 10, maxSamples_ - 1);
        } else {
            delaySamples_ = std::clamp(static_cast<int>(sampleRate_ * 0.375f), 10, maxSamples_ - 1);
        }
        feedback_ = std::clamp(feedback, 0.0f, 0.92f);
        pingpong_ = std::clamp(pingpong, 0.0f, 1.0f);
        wet_ = std::clamp(wet, 0.0f, 1.0f);
    }

    void process(float* buffer, int32_t numFrames, int32_t channels) {
        if (wet_ <= 0.001f) {
            for (int i = 0; i < numFrames; ++i) {
                leftBuffer_[writeIndex_] = buffer[i * channels];
                rightBuffer_[writeIndex_] = (channels > 1) ? buffer[i * channels + 1] : buffer[i * channels];
                writeIndex_ = (writeIndex_ + 1) % maxSamples_;
            }
            return;
        }

        for (int i = 0; i < numFrames; ++i) {
            int readIndex = (writeIndex_ - delaySamples_ + maxSamples_) % maxSamples_;
            float delL = leftBuffer_[readIndex];
            float delR = rightBuffer_[readIndex];

            float fedL = delL * (1.0f - pingpong_) + delR * pingpong_;
            float fedR = delR * (1.0f - pingpong_) + delL * pingpong_;

            float dampedL = dampL_.processSample(fedL, false);
            float dampedR = dampR_.processSample(fedR, true);

            float inL = buffer[i * channels];
            float inR = (channels > 1) ? buffer[i * channels + 1] : inL;

            leftBuffer_[writeIndex_] = inL + dampedL * feedback_;
            rightBuffer_[writeIndex_] = inR + dampedR * feedback_;

            buffer[i * channels] = inL * (1.0f - wet_) + delL * wet_;
            if (channels > 1) {
                buffer[i * channels + 1] = inR * (1.0f - wet_) + delR * wet_;
            }

            writeIndex_ = (writeIndex_ + 1) % maxSamples_;
        }
    }

private:
    float sampleRate_;
    int maxSamples_;
    std::vector<float> leftBuffer_;
    std::vector<float> rightBuffer_;
    int writeIndex_;
    int delaySamples_;
    float feedback_;
    float pingpong_;
    float wet_;
    LinkwitzBiquadFilter dampL_, dampR_;
};

// --- ADOPTED FROM MIXXX 2.5.6: PRO DYNAMIC STUDIO COMPRESSOR WITH AUTO MAKEUP GAIN ---
class StudioCompressor {
public:
    StudioCompressor(float sampleRate = 48000.0f) : sampleRate_(sampleRate) {
        initBallistics(1.0f, 200.0f);
    }

    void setSampleRate(float sr) {
        sampleRate_ = (sr > 0.0f) ? sr : 48000.0f;
        initBallistics(attackMs_, releaseMs_);
    }

    void setParameters(float thresholdDb = -18.0f, float ratio = 4.0f, float attackMs = 1.0f, float releaseMs = 200.0f, bool autoMakeup = true) {
        thresholdDb_ = std::clamp(thresholdDb, -50.0f, 0.0f);
        ratio_ = std::clamp(ratio, 1.0f, 20.0f);
        attackMs_ = std::clamp(attackMs, 0.1f, 250.0f);
        releaseMs_ = std::clamp(releaseMs, 10.0f, 1000.0f);
        autoMakeup_ = autoMakeup;
        initBallistics(attackMs_, releaseMs_);
    }

    void process(float* buffer, int32_t numFrames, int32_t channels) {
        float threshLinear = std::pow(10.0f, thresholdDb_ / 20.0f);
        float makeupGain = autoMakeup_ ? std::pow(10.0f, (-thresholdDb_ * (1.0f - 1.0f / ratio_)) / 40.0f) : 1.0f;

        for (int i = 0; i < numFrames; ++i) {
            float maxSample = 0.0f;
            for (int c = 0; c < channels; ++c) {
                maxSample = std::max(maxSample, std::abs(buffer[i * channels + c]));
            }

            if (maxSample > envelope_) {
                envelope_ = attackCoeff_ * envelope_ + (1.0f - attackCoeff_) * maxSample;
            } else {
                envelope_ = releaseCoeff_ * envelope_ + (1.0f - releaseCoeff_) * maxSample;
            }

            float gainReduction = 1.0f;
            if (envelope_ > threshLinear) {
                float envDb = 20.0f * std::log10(std::max(envelope_, 0.0001f));
                float overDb = envDb - thresholdDb_;
                float compressedDb = overDb / ratio_;
                float targetDb = thresholdDb_ + compressedDb;
                gainReduction = std::pow(10.0f, (targetDb - envDb) / 20.0f);
            }

            float finalGain = gainReduction * makeupGain;
            for (int c = 0; c < channels; ++c) {
                buffer[i * channels + c] *= finalGain;
            }
        }
    }

private:
    void initBallistics(float attackMs, float releaseMs) {
        attackCoeff_ = std::exp(-1000.0f / (attackMs * sampleRate_));
        releaseCoeff_ = std::exp(-1000.0f / (releaseMs * sampleRate_));
    }

    float sampleRate_ = 48000.0f;
    float thresholdDb_ = -18.0f;
    float ratio_ = 4.0f;
    float attackMs_ = 1.0f;
    float releaseMs_ = 200.0f;
    bool autoMakeup_ = true;

    float attackCoeff_ = 0.0f;
    float releaseCoeff_ = 0.0f;
    float envelope_ = 0.0f;
};

// --- ETAPA 2: DELAY RÍTMICO BPM SYNC CON DAMPING Y FREEZE ---
class StereoDelay {
public:
    StereoDelay(float sampleRate = 44100.0f, float maxSeconds = 3.0f) {
        sampleRate_ = sampleRate;
        maxSamples_ = static_cast<int>(sampleRate * maxSeconds);
        buffer_.assign(maxSamples_ * 2, 0.0f);
        writeIndex_ = 0;
        delaySamples_ = static_cast<int>(sampleRate * 0.5f);
        feedback_ = 0.4f;
        wet_ = 0.0f;
        dampFilter_.setSampleRate(sampleRate);
        dampFilter_.setParameters(LinkwitzBiquadFilter::LOWPASS, 4000.0f, 0.707f);
    }

    void setSampleRate(float sr) {
        sampleRate_ = (sr > 0.0f) ? sr : 44100.0f;
        dampFilter_.setSampleRate(sampleRate_);
    }

    void setParameters(float delaySeconds, float feedback, float wet, bool freeze = false) {
        delaySamples_ = std::clamp(static_cast<int>(sampleRate_ * delaySeconds), 1, maxSamples_ - 1);
        feedback_ = std::clamp(feedback, 0.0f, 0.95f);
        wet_ = std::clamp(wet, 0.0f, 1.0f);
        freeze_ = freeze;
    }

    void setBpmSync(float bpm, float beatFraction, float feedback, float wet, bool freeze = false) {
        if (bpm > 40.0f && bpm < 250.0f) {
            float beatDurationSec = 60.0f / bpm;
            float delaySeconds = beatDurationSec * beatFraction;
            setParameters(delaySeconds, feedback, wet, freeze);
        } else {
            setParameters(0.5f, feedback, wet, freeze);
        }
    }

    void process(float* buffer, int32_t numFrames, int32_t channels) {
        if (wet_ <= 0.0f && !freeze_) {
            writeIndex_ = (writeIndex_ + numFrames * channels) % (maxSamples_ * channels);
            return;
        }

        for (int i = 0; i < numFrames; ++i) {
            for (int c = 0; c < channels; ++c) {
                int idx = i * channels + c;
                float input = freeze_ ? 0.0f : buffer[idx];

                int readIndex = (writeIndex_ - delaySamples_ * channels + maxSamples_ * channels) % (maxSamples_ * channels);
                float delayed = buffer_[readIndex];

                float damped = dampFilter_.processSample(delayed, (c == 1));
                float feedbackVal = freeze_ ? 1.0f : feedback_;
                float outputDelayed = damped * feedbackVal + input;

                buffer_[writeIndex_ + c] = outputDelayed;
                buffer[idx] = input * (1.0f - wet_) + delayed * wet_;
            }
            writeIndex_ = (writeIndex_ + channels) % (maxSamples_ * channels);
        }
    }

private:
    float sampleRate_;
    int maxSamples_;
    std::vector<float> buffer_;
    int writeIndex_;
    int delaySamples_;
    float feedback_;
    float wet_;
    bool freeze_ = false;
    LinkwitzBiquadFilter dampFilter_;
};

// --- ETAPA 2: STUDIO REVERB SCHROEDER-MOORER (FREEVERB ARCHITECTURE) ---
class StudioReverb {
public:
    StudioReverb(float sampleRate = 44100.0f) : sampleRate_(sampleRate), wet_(0.0f), roomSize_(0.5f) {
        initCombBuffers();
    }

    void setSampleRate(float sr) {
        sampleRate_ = (sr > 0.0f) ? sr : 44100.0f;
        initCombBuffers();
    }

    void setParameters(float roomSize, float wet) {
        roomSize_ = std::clamp(roomSize, 0.1f, 0.95f);
        wet_ = std::clamp(wet, 0.0f, 1.0f);
    }

    void process(float* buffer, int32_t numFrames, int32_t channels) {
        if (wet_ <= 0.0f) return;

        for (int i = 0; i < numFrames; ++i) {
            for (int c = 0; c < channels; ++c) {
                int idx = i * channels + c;
                float input = buffer[idx];

                float combSum = 0.0f;
                for (size_t k = 0; k < combBufs_.size(); ++k) {
                    float out = combBufs_[k][combIndices_[k]];
                    combBufs_[k][combIndices_[k]] = input + out * roomSize_ * 0.85f;
                    combIndices_[k] = (combIndices_[k] + 1) % combBufs_[k].size();
                    combSum += out;
                }
                combSum /= static_cast<float>(combBufs_.size());

                buffer[idx] = input * (1.0f - wet_) + combSum * wet_;
            }
        }
    }

private:
    void initCombBuffers() {
        const float combTimes[4] = {0.0297f, 0.0371f, 0.0411f, 0.0437f};
        combBufs_.resize(4);
        combIndices_.assign(4, 0);

        for (int k = 0; k < 4; ++k) {
            int size = static_cast<int>(sampleRate_ * combTimes[k]);
            if (size < 10) size = 10;
            combBufs_[k].assign(size, 0.0f);
        }
    }

    float sampleRate_;
    float wet_;
    float roomSize_;
    std::vector<std::vector<float>> combBufs_;
    std::vector<int> combIndices_;
};

// --- ETAPA 3: BITCRUSHER / DECIMATOR EFFECT ---
class BitcrusherEffect {
public:
    BitcrusherEffect(float sampleRate = 44100.0f) : sampleRate_(sampleRate) {}

    void setParameters(float factor, float wet) {
        factor_ = std::clamp(factor, 1.0f, 16.0f);
        wet_ = std::clamp(wet, 0.0f, 1.0f);
    }

    void process(float* buffer, int32_t numFrames, int32_t channels) {
        if (wet_ <= 0.0f || factor_ <= 1.0f) return;

        int step = static_cast<int>(factor_);
        for (int i = 0; i < numFrames; ++i) {
            if (counter_ % step == 0) {
                for (int c = 0; c < channels; ++c) {
                    heldSamples_[c] = buffer[i * channels + c];
                }
            }
            for (int c = 0; c < channels; ++c) {
                int idx = i * channels + c;
                float input = buffer[idx];
                float crushed = heldSamples_[c];
                buffer[idx] = input * (1.0f - wet_) + crushed * wet_;
            }
            counter_++;
        }
    }

private:
    float sampleRate_;
    float factor_ = 1.0f;
    float wet_ = 0.0f;
    float heldSamples_[2] = {0.0f, 0.0f};
    int counter_ = 0;
};

// --- ETAPA 3: BEAT ROLL / STUTTER LOOP EFFECT ---
class BeatRollEffect {
public:
    BeatRollEffect(float sampleRate = 44100.0f, float maxSeconds = 2.0f) : sampleRate_(sampleRate) {
        maxSamples_ = static_cast<int>(sampleRate * maxSeconds);
        ringBuf_.assign(maxSamples_ * 2, 0.0f);
    }

    void setSampleRate(float sr) {
        sampleRate_ = (sr > 0.0f) ? sr : 44100.0f;
    }

    void setRoll(float bpm, float fraction, bool active) {
        active_ = active;
        if (active_) {
            if (bpm > 40.0f && bpm < 250.0f) {
                float beatDurationSec = 60.0f / bpm;
                rollSamples_ = std::clamp(static_cast<int>(sampleRate_ * beatDurationSec * fraction), 10, maxSamples_ - 1);
            } else {
                rollSamples_ = static_cast<int>(sampleRate_ * 0.25f);
            }
        } else {
            rollIndex_ = 0;
        }
    }

    void process(float* buffer, int32_t numFrames, int32_t channels) {
        for (int i = 0; i < numFrames; ++i) {
            if (!active_) {
                for (int c = 0; c < channels; ++c) {
                    ringBuf_[writeIdx_ + c] = buffer[i * channels + c];
                }
                writeIdx_ = (writeIdx_ + channels) % (maxSamples_ * channels);
            } else {
                for (int c = 0; c < channels; ++c) {
                    int readIdx = (rollIndex_ % rollSamples_) * channels + c;
                    buffer[i * channels + c] = ringBuf_[readIdx];
                }
                rollIndex_++;
            }
        }
    }

private:
    float sampleRate_;
    int maxSamples_;
    std::vector<float> ringBuf_;
    int writeIdx_ = 0;
    int rollIndex_ = 0;
    int rollSamples_ = 1000;
    bool active_ = false;
};

#endif // AUDIO_EFFECTS_H
