#ifndef AUDIO_DECODER_H
#define AUDIO_DECODER_H

#include <string>
#include <cstdint>

/**
 * Interface for audio decoders.
 */
class AudioDecoder {
public:
    virtual ~AudioDecoder() = default;

    virtual bool open(const std::string& path) = 0;
    virtual int32_t readFrames(int32_t numFrames, float* targetBuffer) = 0;
    virtual bool seek(int64_t frameIndex) = 0;

    virtual int32_t getSampleRate() const = 0;
    virtual int32_t getChannelCount() const = 0;
    virtual int64_t getTotalFrames() const = 0;

    virtual void close() = 0;
};

#endif // AUDIO_DECODER_H
