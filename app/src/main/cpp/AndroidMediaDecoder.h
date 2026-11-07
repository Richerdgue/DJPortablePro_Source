#ifndef ANDROID_MEDIA_DECODER_H
#define ANDROID_MEDIA_DECODER_H

#include "AudioDecoder.h"
#include <media/NdkMediaExtractor.h>
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaFormat.h>

class AndroidMediaDecoder : public AudioDecoder {
public:
    AndroidMediaDecoder();
    ~AndroidMediaDecoder() override;

    bool open(const std::string& path) override;
    int32_t readFrames(int32_t numFrames, float* targetBuffer) override;
    bool seek(int64_t frameIndex) override;

    int32_t getSampleRate() const override { return sampleRate_; }
    int32_t getChannelCount() const override { return channelCount_; }
    int64_t getTotalFrames() const override { return totalFrames_; }

    void close() override;

private:
    AMediaExtractor* extractor_ = nullptr;
    AMediaCodec* codec_ = nullptr;
    AMediaFormat* format_ = nullptr;
    int fd_ = -1;

    int32_t sampleRate_ = 0;
    int32_t channelCount_ = 0;
    int32_t pcmEncoding_ = 2; // 2 = 16-bit PCM, 4 = 32-bit float PCM
    int64_t totalFrames_ = 0;
    int64_t durationUs_ = 0;

    bool eosInputSent_ = false;
    bool eosOutputReached_ = false;

    bool setupCodec();
};

#endif // ANDROID_MEDIA_DECODER_H
