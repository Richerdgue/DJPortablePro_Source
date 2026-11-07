#ifndef RING_BUFFER_H
#define RING_BUFFER_H

#include <atomic>
#include <vector>

/**
 * Single-Producer Single-Consumer Lock-free Ring Buffer.
 * Designed for professional audio real-time safety.
 */
class RingBuffer {
public:
    explicit RingBuffer(int32_t capacity)
        : capacity_(capacity), buffer_(capacity) {
        readPos_.store(0);
        writePos_.store(0);
    }

    // Producer side (background thread)
    int32_t write(const float* data, int32_t numSamples) {
        int32_t available = capacity_ - size();
        int32_t toWrite = (numSamples < available) ? numSamples : available;

        int32_t writeIdx = writePos_.load(std::memory_order_relaxed);
        for (int32_t i = 0; i < toWrite; ++i) {
            buffer_[writeIdx] = data[i];
            writeIdx = (writeIdx + 1) % capacity_;
        }
        writePos_.store(writeIdx, std::memory_order_release);
        return toWrite;
    }

    // Consumer side (audio thread - REAL-TIME SAFE)
    int32_t read(float* target, int32_t numSamples) {
        int32_t available = size();
        int32_t toRead = (numSamples < available) ? numSamples : available;

        int32_t readIdx = readPos_.load(std::memory_order_relaxed);
        for (int32_t i = 0; i < toRead; ++i) {
            target[i] = buffer_[readIdx];
            readIdx = (readIdx + 1) % capacity_;
        }
        readPos_.store(readIdx, std::memory_order_release);
        return toRead;
    }

    int32_t size() const {
        int32_t diff = writePos_.load(std::memory_order_acquire) - readPos_.load(std::memory_order_acquire);
        if (diff < 0) diff += capacity_;
        return diff;
    }

private:
    int32_t capacity_;
    std::vector<float> buffer_;
    std::atomic<int32_t> readPos_;
    std::atomic<int32_t> writePos_;
};

#endif // RING_BUFFER_H
