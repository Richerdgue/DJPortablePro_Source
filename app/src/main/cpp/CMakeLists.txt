cmake_minimum_required(VERSION 3.22.1)

project("djportablepro")

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(oboe REQUIRED CONFIG)

include_directories(
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${CMAKE_CURRENT_SOURCE_DIR}/qm-dsp
)

add_library(djportablepro SHARED
        native-lib.cpp
        AudioEngine.cpp
        AndroidMediaDecoder.cpp
        DeckAudioPlayer.cpp
        TrackAnalyzer.cpp
        PhaseSyncEngine.cpp
        bpm_analyzer.cpp
        bpm_engine/BpmAnalyzerEngine.cpp
        bpm_engine/JniBpmBridge.cpp
        qm-dsp/base/Pitch.cpp
        qm-dsp/maths/MathUtilities.cpp
        qm-dsp/dsp/phasevocoder/PhaseVocoder.cpp
        qm-dsp/dsp/transforms/FFT.cpp
        qm-dsp/dsp/onsets/DetectionFunction.cpp
        qm-dsp/dsp/tempotracking/TempoTrackV2.cpp
        qm-dsp/ext/kissfft/kiss_fft.c
        qm-dsp/ext/kissfft/tools/kiss_fftr.c
)

# 16 KB Page Alignment for Android 15+ Compatibility
target_link_options(djportablepro PRIVATE "-Wl,-z,max-page-size=16384")

target_link_libraries(djportablepro
        oboe::oboe
        log
        android
        mediandk
        GLESv3
        EGL
)
