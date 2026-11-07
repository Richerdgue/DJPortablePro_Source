#ifndef DSP_FILTERS_H
#define DSP_FILTERS_H

#include <cmath>
#include <algorithm>
#include "AudioEffects.h"

/**
 * 2nd Order IIR Biquad Filter for High-Precision DJ EQ.
 */
class BiquadFilter {
public:
    enum class Type {
        LowPass,
        HighPass,
        PeakingEQ,
        LowShelf,
        HighShelf
    };

    void setParams(Type type, float sampleRate, float frequency, float Q, float gainDb = 0.0f) {
        float A = std::pow(10.0f, gainDb / 40.0f);
        float omega = 2.0f * M_PI * frequency / sampleRate;
        float sn = std::sin(omega);
        float cs = std::cos(omega);
        float alpha = sn / (2.0f * Q);

        float b0 = 1.0f, b1 = 0.0f, b2 = 0.0f, a0 = 1.0f, a1 = 0.0f, a2 = 0.0f;

        switch (type) {
            case Type::LowPass:
                b0 = (1.0f - cs) / 2.0f;
                b1 = 1.0f - cs;
                b2 = (1.0f - cs) / 2.0f;
                a0 = 1.0f + alpha;
                a1 = -2.0f * cs;
                a2 = 1.0f - alpha;
                break;
            case Type::HighPass:
                b0 = (1.0f + cs) / 2.0f;
                b1 = -(1.0f + cs);
                b2 = (1.0f + cs) / 2.0f;
                a0 = 1.0f + alpha;
                a1 = -2.0f * cs;
                a2 = 1.0f - alpha;
                break;
            case Type::PeakingEQ:
                b0 = 1.0f + alpha * A;
                b1 = -2.0f * cs;
                b2 = 1.0f - alpha * A;
                a0 = 1.0f + alpha / A;
                a1 = -2.0f * cs;
                a2 = 1.0f - alpha / A;
                break;
            case Type::LowShelf: {
                float sqrtA = std::sqrt(A);
                b0 = A * ((A + 1.0f) - (A - 1.0f) * cs + 2.0f * sqrtA * alpha);
                b1 = 2.0f * A * ((A - 1.0f) - (A + 1.0f) * cs);
                b2 = A * ((A + 1.0f) - (A - 1.0f) * cs - 2.0f * sqrtA * alpha);
                a0 = (A + 1.0f) + (A - 1.0f) * cs + 2.0f * sqrtA * alpha;
                a1 = -2.0f * ((A - 1.0f) + (A + 1.0f) * cs);
                a2 = (A + 1.0f) + (A - 1.0f) * cs - 2.0f * sqrtA * alpha;
                break;
            }
            case Type::HighShelf: {
                float sqrtA = std::sqrt(A);
                b0 = A * ((A + 1.0f) + (A - 1.0f) * cs + 2.0f * sqrtA * alpha);
                b1 = -2.0f * A * ((A - 1.0f) + (A + 1.0f) * cs);
                b2 = A * ((A + 1.0f) + (A - 1.0f) * cs - 2.0f * sqrtA * alpha);
                a0 = (A + 1.0f) - (A - 1.0f) * cs + 2.0f * sqrtA * alpha;
                a1 = 2.0f * ((A - 1.0f) - (A + 1.0f) * cs);
                a2 = (A + 1.0f) - (A - 1.0f) * cs - 2.0f * sqrtA * alpha;
                break;
            }
            default: return;
        }

        b0_ = b0 / a0; b1_ = b1 / a0; b2_ = b2 / a0;
        a1_ = a1 / a0; a2_ = a2 / a0;
    }

    float process(float x) {
        float y = b0_ * x + b1_ * x1_ + b2_ * x2_ - a1_ * y1_ - a2_ * y2_;
        x2_ = x1_; x1_ = x;
        y2_ = y1_; y1_ = y;
        return y;
    }

private:
    float b0_ = 1.0f, b1_ = 0.0f, b2_ = 0.0f, a1_ = 0.0f, a2_ = 0.0f;
    float x1_ = 0.0f, x2_ = 0.0f, y1_ = 0.0f, y2_ = 0.0f;
};

/**
 * Pro Studio 3-Band EQ + Mixxx 2.5.6 Moog Ladder 4-Pole Analog Filter + Gain DSP Engine.
 */
class DeckDsp {
public:
    void setup(float sampleRate) {
        sampleRate_ = (sampleRate > 0.0f) ? sampleRate : 48000.0f;
        moogFilter_.setSampleRate(sampleRate_);
        updateFilters();
    }

    void setLevels(float low, float mid, float high, float filter, float gain) {
        lowLevel_ = std::clamp(low, 0.0f, 2.0f);
        midLevel_ = std::clamp(mid, 0.0f, 2.0f);
        highLevel_ = std::clamp(high, 0.0f, 2.0f);
        filterLevel_ = std::clamp(filter, 0.0f, 1.0f);
        gain_ = std::clamp(gain, 0.0f, 2.0f);
        updateFilters();
    }

    float process(float x) {
        float out = x * gain_;
        if (filterLevel_ < 0.48f || filterLevel_ > 0.52f) {
            out = moogFilter_.processSample(out, false);
        }
        return out;
    }

private:
    float sampleRate_ = 48000.0f;
    float lowLevel_ = 1.0f, midLevel_ = 1.0f, highLevel_ = 1.0f, filterLevel_ = 0.5f, gain_ = 1.0f;

    BiquadFilter lowEq_, midEq_, highEq_;
    MoogLadder4Filter moogFilter_;

    void updateFilters() {
        // Map 0.0 - 2.0 range to dB (-30dB to +12dB boost)
        auto toDb = [](float val) {
            if (val <= 0.01f) return -60.0f; // Kill
            if (val <= 1.0f) return (val - 1.0f) * 30.0f; // 0.0 -> -30dB, 1.0 -> 0dB
            return (val - 1.0f) * 12.0f; // 1.0 -> 0dB, 2.0 -> +12dB boost!
        };

        lowEq_.setParams(BiquadFilter::Type::LowShelf, sampleRate_, 250.0f, 0.707f, toDb(lowLevel_));
        midEq_.setParams(BiquadFilter::Type::PeakingEQ, sampleRate_, 1000.0f, 0.707f, toDb(midLevel_));
        highEq_.setParams(BiquadFilter::Type::HighShelf, sampleRate_, 4000.0f, 0.707f, toDb(highLevel_));

        // DJ Sound Color Filter: Adopted from Mixxx 2.5.6 EngineFilterMoogLadder4
        if (filterLevel_ < 0.48f) {
            float norm = std::clamp(filterLevel_ / 0.5f, 0.0f, 1.0f);
            float freq = 120.0f + norm * 19880.0f;
            moogFilter_.setParameters(freq, 1.35f, false /* LowPass */);
        } else if (filterLevel_ > 0.52f) {
            float norm = std::clamp((filterLevel_ - 0.5f) / 0.5f, 0.0f, 1.0f);
            float freq = 20.0f + norm * 7980.0f;
            moogFilter_.setParameters(freq, 1.35f, true /* HighPass */);
        }
    }
};

#endif // DSP_FILTERS_H
