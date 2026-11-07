#ifndef TRACK_ANALYZER_H
#define TRACK_ANALYZER_H

#include <string>
#include <vector>
#include <cstdint>

enum RhythmSignature {
    RHYTHM_UNKNOWN = 0,
    RHYTHM_FOUR_ON_THE_FLOOR = 1, // Electro, House, Dance, EDM
    RHYTHM_DEMBOW = 2,            // Reggaetón, Trap, Dancehall
    RHYTHM_LATIN_CLAVE = 3        // Salsa, Cumbia, Merengue, Vallenato
};

struct TempoMarker {
    int64_t framePosition;
    float bpm;
};

struct AnalysisOutput {
    float bpm;
    int64_t firstBeatFrame; // The exact frame of the first "1" beat
    std::vector<uint8_t> waveform;
    std::string key = "";
    RhythmSignature rhythmType = RHYTHM_UNKNOWN;
    std::vector<TempoMarker> tempoMap;
    int version;
};

class TrackAnalyzer {
public:
    TrackAnalyzer();
    ~TrackAnalyzer();

    AnalysisOutput analyze(const std::string& path, float referenceBpm = 0.0f);

private:
    const int ANALYZER_VERSION = 4; // Version 4: 24000 Ultra-HD Kymatik Waveform Resolution
    const int WAVEFORM_RESOLUTION = 24000;

    std::string estimateCamelotKey(const std::vector<float>& pcmMono, int32_t sampleRate);
    float verifyBpmWithMetronomeGrid(const std::vector<float>& novelty, double windowRate, float initialBpm);
    RhythmSignature classifyRhythmSignature(const std::vector<float>& novelty, double windowRate, float initialBpm);
    std::vector<TempoMarker> generateDynamicTempoMap(const std::vector<float>& novelty, double windowRate, int32_t sampleRate, float globalBpm);
};

#endif // TRACK_ANALYZER_H
