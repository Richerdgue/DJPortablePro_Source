#include "AndroidMediaDecoder.h"
#include <android/log.h>
#include <unistd.h>
#include <cstring>
#include <fcntl.h>
#include <sys/stat.h>
#include <cerrno>

#define TAG "AndroidMediaDecoder"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

AndroidMediaDecoder::AndroidMediaDecoder() {}

AndroidMediaDecoder::~AndroidMediaDecoder() {
    close();
}

bool AndroidMediaDecoder::open(const std::string& path) {
    close();

    eosInputSent_ = false;
    eosOutputReached_ = false;

    if (path.rfind("fd:", 0) == 0) {
        // Path is a detached File Descriptor from Android ContentResolver
        try {
            fd_ = std::stoi(path.substr(3));
        } catch (...) {
            fd_ = -1;
        }
        if (fd_ < 0) {
            LOGE("Invalid File Descriptor string: %s", path.c_str());
            return false;
        }
        struct stat st;
        if (fstat(fd_, &st) < 0) {
            LOGE("Failed to fstat FD: %d", fd_);
            return false;
        }
        extractor_ = AMediaExtractor_new();
        media_status_t status = AMediaExtractor_setDataSourceFd(extractor_, fd_, 0, st.st_size);
        if (status != AMEDIA_OK) {
            LOGE("Failed to set data source FD: %d for path: %s", status, path.c_str());
            return false;
        }
    } else {
        // POSIX open for regular file paths
        fd_ = ::open(path.c_str(), O_RDONLY);
        if (fd_ < 0) {
            LOGE("Failed to open file path via POSIX open(): %s, errno=%d (%s)", path.c_str(), errno, strerror(errno));
            return false;
        }

        struct stat st;
        if (fstat(fd_, &st) < 0) {
            LOGE("Failed to stat file: %s", path.c_str());
            ::close(fd_);
            fd_ = -1;
            return false;
        }

        extractor_ = AMediaExtractor_new();
        media_status_t status = AMediaExtractor_setDataSourceFd(extractor_, fd_, 0, st.st_size);

        if (status != AMEDIA_OK) {
            LOGE("Failed to set data source FD: %d for path: %s", status, path.c_str());
            ::close(fd_);
            fd_ = -1;
            return false;
        }
    }

    int numTracks = AMediaExtractor_getTrackCount(extractor_);
    for (int i = 0; i < numTracks; ++i) {
        AMediaFormat* format = AMediaExtractor_getTrackFormat(extractor_, i);
        const char* mime;
        if (!AMediaFormat_getString(format, AMEDIAFORMAT_KEY_MIME, &mime)) {
            AMediaFormat_delete(format);
            continue;
        }

        if (strncmp(mime, "audio/", 6) == 0) {
            AMediaExtractor_selectTrack(extractor_, i);

            AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_SAMPLE_RATE, &sampleRate_);
            AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_CHANNEL_COUNT, &channelCount_);
            AMediaFormat_getInt64(format, AMEDIAFORMAT_KEY_DURATION, &durationUs_);
            if (!AMediaFormat_getInt32(format, "pcm-encoding", &pcmEncoding_)) {
                pcmEncoding_ = 2; // Default to 16-bit PCM
            }

            if (sampleRate_ <= 0) sampleRate_ = 44100;
            if (channelCount_ <= 0) channelCount_ = 2;

            totalFrames_ = (durationUs_ * sampleRate_) / 1000000;

            LOGI("[DECODER] Audio Track Selected: MIME=%s, SampleRate=%d, Channels=%d, Encoding=%d, TotalFrames=%lld",
                 mime, sampleRate_, channelCount_, pcmEncoding_, totalFrames_);

            codec_ = AMediaCodec_createDecoderByType(mime);
            AMediaCodec_configure(codec_, format, nullptr, nullptr, 0);
            AMediaCodec_start(codec_);

            AMediaFormat* initialOutFormat = AMediaCodec_getOutputFormat(codec_);
            if (initialOutFormat) {
                int32_t enc = 2;
                if (AMediaFormat_getInt32(initialOutFormat, "pcm-encoding", &enc)) {
                    pcmEncoding_ = enc;
                    LOGI("[DECODER] Initial MediaCodec Output Format: pcmEncoding=%d", pcmEncoding_);
                }
                AMediaFormat_delete(initialOutFormat);
            }

            AMediaFormat_delete(format);
            return true;
        }
        AMediaFormat_delete(format);
    }

    LOGE("No audio track found in file: %s", path.c_str());
    return false;
}

void AndroidMediaDecoder::close() {
    if (codec_) {
        AMediaCodec_stop(codec_);
        AMediaCodec_delete(codec_);
        codec_ = nullptr;
    }
    if (extractor_) {
        AMediaExtractor_delete(extractor_);
        extractor_ = nullptr;
    }
    if (fd_ >= 0) {
        ::close(fd_);
        fd_ = -1;
    }
    eosInputSent_ = false;
    eosOutputReached_ = false;
}

int32_t AndroidMediaDecoder::readFrames(int32_t numFrames, float* targetBuffer) {
    if (!codec_ || eosOutputReached_) return 0;

    int32_t samplesRead = 0;
    int32_t targetSamples = numFrames * channelCount_;
    int retryCount = 0;

    while (samplesRead < targetSamples && !eosOutputReached_ && retryCount < 100) {
        // 1. Queue input buffers until EOS
        if (!eosInputSent_) {
            ssize_t inputBufIdx = AMediaCodec_dequeueInputBuffer(codec_, 1000); // 1ms
            if (inputBufIdx >= 0) {
                size_t inputBufSize = 0;
                uint8_t* inputBuf = AMediaCodec_getInputBuffer(codec_, inputBufIdx, &inputBufSize);
                ssize_t sampleSize = AMediaExtractor_readSampleData(extractor_, inputBuf, inputBufSize);

                if (sampleSize < 0) {
                    AMediaCodec_queueInputBuffer(codec_, inputBufIdx, 0, 0, 0, AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM);
                    eosInputSent_ = true;
                } else {
                    int64_t presentationTimeUs = AMediaExtractor_getSampleTime(extractor_);
                    AMediaCodec_queueInputBuffer(codec_, inputBufIdx, 0, sampleSize, presentationTimeUs, 0);
                    AMediaExtractor_advance(extractor_);
                }
            }
        }

        // 2. Dequeue output PCM frames
        AMediaCodecBufferInfo info;
        ssize_t outputBufIdx = AMediaCodec_dequeueOutputBuffer(codec_, &info, 1000); // 1ms
        if (outputBufIdx >= 0) {
            size_t outputBufSize = 0;
            uint8_t* outputBuf = AMediaCodec_getOutputBuffer(codec_, outputBufIdx, &outputBufSize);

            if (pcmEncoding_ == 4) { // 32-bit Float PCM
                float* floatData = reinterpret_cast<float*>(outputBuf + info.offset);
                int32_t samplesInPacket = info.size / sizeof(float);
                for (int i = 0; i < samplesInPacket && samplesRead < targetSamples; ++i) {
                    targetBuffer[samplesRead++] = floatData[i];
                }
            } else { // 16-bit PCM (standard)
                int16_t* pcmData = reinterpret_cast<int16_t*>(outputBuf + info.offset);
                int32_t samplesInPacket = info.size / sizeof(int16_t);
                for (int i = 0; i < samplesInPacket && samplesRead < targetSamples; ++i) {
                    targetBuffer[samplesRead++] = pcmData[i] / 32768.0f;
                }
            }

            AMediaCodec_releaseOutputBuffer(codec_, outputBufIdx, false);

            if (info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) {
                eosOutputReached_ = true;
                break;
            }
            retryCount = 0;
        } else if (outputBufIdx == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
            AMediaFormat* newFormat = AMediaCodec_getOutputFormat(codec_);
            if (newFormat) {
                int32_t newEncoding = 2;
                if (AMediaFormat_getInt32(newFormat, "pcm-encoding", &newEncoding)) {
                    pcmEncoding_ = newEncoding;
                    LOGI("[DECODER] MediaCodec output format changed: pcmEncoding=%d", pcmEncoding_);
                }
                AMediaFormat_delete(newFormat);
            }
            retryCount = 0;
        } else {
            retryCount++;
            usleep(100);
        }
    }

    return samplesRead / channelCount_;
}

bool AndroidMediaDecoder::seek(int64_t frameIndex) {
    if (!extractor_) return false;
    int64_t seekTimeUs = (frameIndex * 1000000) / sampleRate_;
    AMediaExtractor_seekTo(extractor_, seekTimeUs, AMEDIAEXTRACTOR_SEEK_CLOSEST_SYNC);
    AMediaCodec_flush(codec_);
    eosInputSent_ = false;
    eosOutputReached_ = false;
    return true;
}
