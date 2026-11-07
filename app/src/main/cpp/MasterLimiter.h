#ifndef MASTER_LIMITER_H
#define MASTER_LIMITER_H

#include <vector>
#include <cmath>
#include <algorithm>

/**
 * Professional Look-ahead Brick-wall Limiter.
 * Prevents digital clipping and provides a loud, punchy master output.
 */
class MasterLimiter {
public:
    MasterLimiter(float sampleRate, float lookAheadMs = 5.0f) {
        sampleRate_ = sampleRate;
        lookAheadSamples_ = static_cast<int>(sampleRate * (lookAheadMs / 1000.0f));
        delayBuffer_.assign(lookAheadSamples_ * 2, 0.0f); // Stereo
        delayIdx_ = 0;

        // Professional Attack/Release constants
        attack_ = 0.999f;  // Fast
        release_ = 0.9999f; // Smooth
        gain_ = 1.0f;
    }

    void process(float* buffer, int32_t numFrames, int32_t channels) {
        const float threshold = 0.95f; // -0.5 dB head-room

        for (int i = 0; i < numFrames; ++i) {
            // 1. Calculate Peak across channels in this frame
            float maxFramePeak = 0.0f;
            for (int c = 0; c < channels; ++c) {
                maxFramePeak = std::max(maxFramePeak, std::abs(buffer[i * channels + c]));
            }

            if (maxFramePeak > currentPeak_) currentPeak_ = maxFramePeak;
            else currentPeak_ *= release_;

            // 2. Calculate target gain
            float targetGain = 1.0f;
            if (currentPeak_ > threshold) {
                targetGain = threshold / currentPeak_;
            }

            // 3. Smooth gain change
            gain_ = gain_ * attack_ + targetGain * (1.0f - attack_);

            // 4. Apply gain to DELAYED frame signals & update delay buffer
            int frameDelayOffset = delayIdx_ * channels;
            for (int c = 0; c < channels; ++c) {
                int idx = i * channels + c;
                float input = buffer[idx];

                float delayedSample = delayBuffer_[frameDelayOffset + c];
                buffer[idx] = delayedSample * gain_;

                delayBuffer_[frameDelayOffset + c] = input;
            }

            // 5. Advance delay index ONCE per frame
            delayIdx_ = (delayIdx_ + 1) % lookAheadSamples_;
        }
    }

private:
    float sampleRate_;
    int lookAheadSamples_;
    std::vector<float> delayBuffer_;
    int delayIdx_;

    float currentPeak_ = 0.0f;
    float gain_ = 1.0f;
    float attack_;
    float release_;
};

#endif // MASTER_LIMITER_H
