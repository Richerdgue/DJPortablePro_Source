# 🎧 DJPortablePro - Professional Open-Source Android DJ Application

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Android](https://img.shields.io/badge/Platform-Android_8.0+-green.svg)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin_1.9+-purple.svg)](https://kotlinlang.org/)
[![C++](https://img.shields.io/badge/Native-C%2B%2B20_NDK-blue.svg)](https://developer.android.com/ndk)

**DJPortablePro** is a high-performance, low-latency mobile DJ console for Android. Built with a native **C++20 NDK audio engine** powered by **Google Oboe**, **Mixxx 2.5.6 DSP filters**, and **Jetpack Compose 120 FPS hardware-accelerated waveforms**.

---

## 🌟 Key Features

* **High-Precision C++ NDK Audio Engine**:
  * Powered by Google Oboe low-latency audio callbacks.
  * **Mixxx 2.5.6 Moog Ladder 4-Pole Analog Resonant Filter** ($Q = 1.35$).
  * **Linkwitz-Riley 4th-Order 24dB/octave Crossover Isolator EQ** with zero phase distortion & $100\%$ full kill.
  * **Ping-Pong Stereo Beat Echo** with BPM Quantization & Ping-Pong Panning.
  * **Pro Studio Compressor** with Soft-Knee and Auto Makeup Gain.
* **120 FPS Catmull-Rom Cubic Spline Waveforms**:
  * Smooth, organic wave contours without sharp 45° sawtooth angles.
  * 3 Reversible Waveform Modes: `OVERVIEW`, `M-COLOR THREAD` (VirtualDJ Full Spectrum RGB), and `SINGLE COLOR`.
  * **Full-Screen Parallel Waveforms & Grid Editor (`GRID EDIT`)** with `< GRID >`, `SET 1`, `TAP TEMPO`, and `RESET`.
* **Professional Ergonomic Deck Layout**:
  * Tactile 3D Metallic Pitch Faders ($52\text{dp} \times 34\text{dp}$) with **Double-Tap Pitch Reset** to $0.0\%$.
  * Balanced Jog Wheels with center BPM indicator.
  * 6-Fader DJ Mixer Channel Strip (`GAIN`, `HIGH`, `MID`, `LOW`, `FILTER`, `VOL`).
  * 64-Bit Double-Precision Master Sync & Downbeat Phase Lock (`Bombo con Bombo y Caja con Caja`).

---

## 🏗️ Project Architecture

```
DJPortablePro/
├── app/
│   ├── src/main/cpp/               # Native C++20 NDK Audio Core
│   │   ├── AudioEffects.h          # Moog Ladder 4-Pole, Linkwitz-Riley EQ, Ping-Pong Echo
│   │   ├── DspFilters.h            # Biquad Filters & High-Precision Gain Curve
│   │   ├── DeckAudioPlayer.cpp     # Resampling, Oboe Callbacks & 64-bit Beat Sync
│   │   ├── TrackAnalyzer.cpp       # 10-Band Spectral Kick Onset & BPM Analyzer
│   │   └── NativeAudioPlayer.cpp   # JNI Bridge
│   ├── src/main/java/com/djportablepro/app/
│   │   ├── audio/                  # DjPlayerController & State Management
│   │   ├── ui/
│   │   │   ├── main/               # MainActivity, Overview & Zoom Waveforms, Mixer
│   │   │   ├── grid/               # FullScreenGridEditOverlay (Parallel Waveforms)
│   │   │   ├── deck/               # JogWheelView, PitchControlView, ProMobileDeckPanel
│   │   │   └── library/            # LibraryBrowserComponent (File Picker)
├── LICENSE                         # MIT License
└── README.md
```

---

## 🚀 Building & Running

### Requirements
1. **Android Studio** 2024.1.1 or newer.
2. **Android NDK** r26+ & **CMake** 3.22+.
3. **JDK** 17 or JDK 21.

### Quick Build
```bash
# Clone the repository
git clone https://github.com/Richerdgue/DJ-Portable-Pro-para-Android.git
cd DJPortablePro

# Build Debug APK
./gradlew assembleDebug
```

---

## 🤝 Contributing

Contributions are welcome! Feel free to open an issue or submit a pull request for new audio DSP effects, UI improvements, or MIDI controller mappings.

## 📄 License

Distributed under the **MIT License**. See `LICENSE` for more information.
