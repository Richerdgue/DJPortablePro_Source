#include "BpmAnalyzerEngine.h"
#include "../TrackAnalyzer.h"
#include <android/log.h>

#define TAG "BpmAnalyzerEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

BpmAnalyzerEngine::BpmAnalyzerEngine() {}
BpmAnalyzerEngine::~BpmAnalyzerEngine() {}

KymatikNativeOutput BpmAnalyzerEngine::analyzeTrack(const std::string& filePath, double referenceBpmHint) {
    LOGI("[KYMATIK_CPP] Hybrid Aubio/Superpowered BPM Engine Analyzing: %s (Hint: %.1f)", filePath.c_str(), referenceBpmHint);

    TrackAnalyzer analyzer;
    AnalysisOutput rawOut = analyzer.analyze(filePath, static_cast<float>(referenceBpmHint));

    KymatikNativeOutput output;
    output.bpm = static_cast<double>(rawOut.bpm);
    output.firstBeatFrame = rawOut.firstBeatFrame;
    output.confidence = (rawOut.bpm > 50.0f) ? 0.96 : 0.0;
    output.key = rawOut.key;
    output.waveform = rawOut.waveform;
    output.version = 3;

    return output;
}
