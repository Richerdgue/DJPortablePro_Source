#ifndef BPM_ANALYZER_ENGINE_H
#define BPM_ANALYZER_ENGINE_H

#include <string>
#include <vector>
#include <cstdint>

struct KymatikNativeOutput {
    double bpm;
    int64_t firstBeatFrame;
    double confidence;
    std::string key;
    std::vector<uint8_t> waveform;
    int version = 3;
};

class BpmAnalyzerEngine {
public:
    BpmAnalyzerEngine();
    ~BpmAnalyzerEngine();

    KymatikNativeOutput analyzeTrack(const std::string& filePath, double referenceBpmHint = 0.0);

private:
    const int WAVEFORM_RESOLUTION = 2000;
};

#endif // BPM_ANALYZER_ENGINE_H
