#include <jni.h>
#include <mutex>
#include "AudioEngine.h"
#include "TrackAnalyzer.h"
#include "bpm_analyzer.h"

static AudioEngine *engine = nullptr;
static std::mutex engineMutex;

static jclass gAnalysisResultClass = nullptr;
static jmethodID gAnalysisResultConstructor = nullptr;

extern "C"
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    jclass localClass = env->FindClass("com/djportablepro/app/library/AnalysisResult");
    if (localClass != nullptr) {
        gAnalysisResultClass = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
        gAnalysisResultConstructor = env->GetMethodID(gAnalysisResultClass, "<init>", "(FJ[BI)V");
        env->DeleteLocalRef(localClass);
    }

    return JNI_VERSION_1_6;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_djportablepro_app_audio_engine_NativeAudioPlayer_nInitialize(JNIEnv *env, jobject thiz, jint tier) {
    std::lock_guard<std::mutex> lock(engineMutex);
    if (engine == nullptr) {
        engine = new AudioEngine();
        engine->initialize(tier);
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_djportablepro_app_audio_engine_NativeAudioPlayer_nRelease(JNIEnv *env, jobject thiz) {
    std::lock_guard<std::mutex> lock(engineMutex);
    if (engine != nullptr) {
        engine->stop();
        delete engine;
        engine = nullptr;
    }
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_djportablepro_app_audio_engine_NativeAudioPlayer_nIsInitialized(JNIEnv *env, jobject thiz) {
    std::lock_guard<std::mutex> lock(engineMutex);
    return (engine != nullptr);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_djportablepro_app_audio_engine_NativeAudioPlayer_nStart(JNIEnv *env, jobject thiz) {
    std::lock_guard<std::mutex> lock(engineMutex);
    return (engine != nullptr) ? engine->start() : false;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_djportablepro_app_audio_engine_NativeAudioPlayer_nStop(JNIEnv *env, jobject thiz) {
    std::lock_guard<std::mutex> lock(engineMutex);
    return (engine != nullptr) ? engine->stop() : false;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_djportablepro_app_audio_engine_NativeAudioPlayer_nLoad(JNIEnv *env, jobject thiz, jint deckIndex, jstring path) {
    if (path == nullptr) return false;
    const char *nativePath = env->GetStringUTFChars(path, nullptr);
    bool result = false;
    {
        std::lock_guard<std::mutex> lock(engineMutex);
        if (engine != nullptr) {
            result = engine->getDeck(deckIndex)->load(nativePath);
        }
    }
    env->ReleaseStringUTFChars(path, nativePath);
    return result;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_djportablepro_app_audio_engine_NativeAudioPlayer_nPlay(JNIEnv *env, jobject thiz, jint deckIndex) {
    std::lock_guard<std::mutex> lock(engineMutex);
    if (engine != nullptr) engine->getDeck(deckIndex)->play();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_djportablepro_app_audio_engine_NativeAudioPlayer_nPause(JNIEnv *env, jobject thiz, jint deckIndex) {
    std::lock_guard<std::mutex> lock(engineMutex);
    if (engine != nullptr) engine->getDeck(deckIndex)->pause();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_djportablepro_app_audio_engine_NativeAudioPlayer_nSeek(JNIEnv *env, jobject thiz, jint deckIndex, jlong frameIndex) {
    std::lock_guard<std::mutex> lock(engineMutex);
    if (engine != nullptr) engine->getDeck(deckIndex)->seek(frameIndex);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_djportablepro_app_audio_engine_NativeAudioPlayer_nSetPlaybackRate(JNIEnv *env, jobject thiz, jint deckIndex, jfloat rate) {
    std::lock_guard<std::mutex> lock(engineMutex);
    if (engine != nullptr) engine->getDeck(deckIndex)->setPlaybackRate(rate);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_djportablepro_app_audio_engine_NativeAudioPlayer_nSetVolume(JNIEnv *env, jobject thiz, jint deckIndex, jfloat volume) {
    std::lock_guard<std::mutex> lock(engineMutex);
    if (engine != nullptr) engine->getDeck(deckIndex)->setVolume(volume);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_djportablepro_app_audio_engine_NativeAudioPlayer_nSetDspParams(JNIEnv *env, jobject thiz, jint deckIndex, jfloat low, jfloat mid, jfloat high, jfloat filter, jfloat gain) {
    std::lock_guard<std::mutex> lock(engineMutex);
    if (engine != nullptr) engine->getDeck(deckIndex)->setDspParams(low, mid, high, filter, gain);
}

// --- PRO DJ EFFECTS & ISOLATOR EQ JNI BRIDGE ---

extern "C"
JNIEXPORT void JNICALL
Java_com_djportablepro_app_audio_engine_NativeAudioPlayer_nSetEqGains(JNIEnv *env, jobject thiz, jint deckIndex, jfloat low, jfloat mid, jfloat high) {
    std::lock_guard<std::mutex> lock(engineMutex);
    if (engine != nullptr) engine->getDeck(deckIndex)->setEqGains(low, mid, high);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_djportablepro_app_audio_engine_NativeAudioPlayer_nSetEqKills(JNIEnv *env, jobject thiz, jint deckIndex, jboolean killLow, jboolean killMid, jboolean killHigh) {
    std::lock_guard<std::mutex> lock(engineMutex);
    if (engine != nullptr) engine->getDeck(deckIndex)->setEqKills(killLow, killMid, killHigh);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_djportablepro_app_audio_engine_NativeAudioPlayer_nSetDelayParams(JNIEnv *env, jobject thiz, jint deckIndex, jfloat beatFraction, jfloat feedback, jfloat wet, jboolean freeze) {
    std::lock_guard<std::mutex> lock(engineMutex);
    if (engine != nullptr) engine->getDeck(deckIndex)->setDelayParams(beatFraction, feedback, wet, freeze);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_djportablepro_app_audio_engine_NativeAudioPlayer_nSetReverbParams(JNIEnv *env, jobject thiz, jint deckIndex, jfloat roomSize, jfloat wet) {
    std::lock_guard<std::mutex> lock(engineMutex);
    if (engine != nullptr) engine->getDeck(deckIndex)->setReverbParams(roomSize, wet);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_djportablepro_app_audio_engine_NativeAudioPlayer_nSetBitcrusherParams(JNIEnv *env, jobject thiz, jint deckIndex, jfloat factor, jfloat wet) {
    std::lock_guard<std::mutex> lock(engineMutex);
    if (engine != nullptr) engine->getDeck(deckIndex)->setBitcrusherParams(factor, wet);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_djportablepro_app_audio_engine_NativeAudioPlayer_nSetBeatRollParams(JNIEnv *env, jobject thiz, jint deckIndex, jfloat fraction, jboolean active) {
    std::lock_guard<std::mutex> lock(engineMutex);
    if (engine != nullptr) engine->getDeck(deckIndex)->setBeatRollParams(fraction, active);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_djportablepro_app_audio_engine_NativeAudioPlayer_nSetScratchMode(JNIEnv *env, jobject thiz, jint deckIndex, jboolean enabled) {
    std::lock_guard<std::mutex> lock(engineMutex);
    if (engine != nullptr) engine->getDeck(deckIndex)->setScratchMode(enabled);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_djportablepro_app_audio_engine_NativeAudioPlayer_nSetScratchVelocity(JNIEnv *env, jobject thiz, jint deckIndex, jfloat velocity) {
    std::lock_guard<std::mutex> lock(engineMutex);
    if (engine != nullptr) engine->getDeck(deckIndex)->setScratchVelocity(velocity);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_djportablepro_app_audio_engine_NativeAudioPlayer_nSetCrossfader(JNIEnv *env, jobject thiz, jfloat position) {
    std::lock_guard<std::mutex> lock(engineMutex);
    if (engine != nullptr) engine->setCrossfader(position);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_djportablepro_app_audio_engine_NativeAudioPlayer_nSetBeatGrid(JNIEnv *env, jobject thiz, jint deckIndex, jfloat bpm, jlong firstBeatFrame) {
    std::lock_guard<std::mutex> lock(engineMutex);
    if (engine != nullptr) engine->getDeck(deckIndex)->setBeatGrid(bpm, firstBeatFrame);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_djportablepro_app_audio_engine_NativeAudioPlayer_nSetDownbeatPhase(JNIEnv *env, jobject thiz, jint deckIndex, jdouble phase) {
    std::lock_guard<std::mutex> lock(engineMutex);
    if (engine != nullptr) engine->getDeck(deckIndex)->setDownbeatPhase(phase);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_djportablepro_app_audio_engine_NativeAudioPlayer_nSetLoop(JNIEnv *env, jobject thiz, jint deckIndex, jlong startFrame, jlong endFrame, jboolean enabled) {
    std::lock_guard<std::mutex> lock(engineMutex);
    if (engine != nullptr) engine->getDeck(deckIndex)->setLoop(startFrame, endFrame, enabled);
}

extern "C"
JNIEXPORT jdouble JNICALL
Java_com_djportablepro_app_audio_engine_NativeAudioPlayer_nGetBeatPhase(JNIEnv *env, jobject thiz, jint deckIndex) {
    std::lock_guard<std::mutex> lock(engineMutex);
    return (engine != nullptr) ? engine->getDeck(deckIndex)->getBeatPhase() : 0.0;
}

extern "C"
JNIEXPORT jdouble JNICALL
Java_com_djportablepro_app_audio_engine_NativeAudioPlayer_nGetDownbeatPhase(JNIEnv *env, jobject thiz, jint deckIndex) {
    std::lock_guard<std::mutex> lock(engineMutex);
    return (engine != nullptr) ? engine->getDeck(deckIndex)->getDownbeatPhase() : 0.0;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_djportablepro_app_audio_engine_NativeAudioPlayer_nAlignPhase(JNIEnv *env, jobject thiz, jint deckIndex, jdouble targetPhase) {
    std::lock_guard<std::mutex> lock(engineMutex);
    if (engine != nullptr) engine->getDeck(deckIndex)->alignPhase(targetPhase);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_djportablepro_app_audio_engine_NativeAudioPlayer_nSetSyncActive(JNIEnv *env, jobject thiz, jint deckIndex, jboolean active) {
    std::lock_guard<std::mutex> lock(engineMutex);
    if (engine != nullptr) engine->getDeck(deckIndex)->setSyncActive(active);
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_com_djportablepro_app_audio_engine_NativeAudioPlayer_nCalculateSyncAdjustment(
        JNIEnv *env, jobject thiz, jint slaveDeckIndex, jdouble slaveBaseBpm, jlong slaveFirstBeatFrame, jboolean isSyncTriggered) {
    std::lock_guard<std::mutex> lock(engineMutex);
    if (engine == nullptr) return nullptr;

    SyncAdjustmentResult res = engine->calculateSyncAdjustment(
        slaveDeckIndex, slaveBaseBpm, slaveFirstBeatFrame, isSyncTriggered);

    jfloatArray result = env->NewFloatArray(6);
    jfloat values[6];
    values[0] = res.pitchRatio;
    values[1] = res.microNudgeFactor;
    values[2] = res.finalPlaybackRate;
    values[3] = static_cast<jfloat>(res.sampleOffsetCorrection);
    values[4] = res.phaseErrorMs;
    values[5] = res.isLowEndInPhase ? 1.0f : 0.0f;

    env->SetFloatArrayRegion(result, 0, 6, values);
    return result;
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_djportablepro_app_audio_engine_NativeAudioPlayer_nGetPosition(JNIEnv *env, jobject thiz, jint deckIndex) {
    std::lock_guard<std::mutex> lock(engineMutex);
    return (engine != nullptr) ? engine->getDeck(deckIndex)->getPosition() : 0;
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_djportablepro_app_audio_engine_NativeAudioPlayer_nGetDuration(JNIEnv *env, jobject thiz, jint deckIndex) {
    std::lock_guard<std::mutex> lock(engineMutex);
    return (engine != nullptr) ? engine->getDeck(deckIndex)->getDuration() : 0;
}

extern "C"
JNIEXPORT jobject JNICALL
Java_com_djportablepro_app_audio_engine_NativeAudioPlayer_nAnalyzeTrack(JNIEnv *env, jobject thiz, jstring path, jfloat referenceBpm) {
    if (path == nullptr) return nullptr;

    const char *nativePath = env->GetStringUTFChars(path, nullptr);

    TrackAnalyzer analyzer;
    AnalysisOutput output = analyzer.analyze(nativePath, referenceBpm);

    env->ReleaseStringUTFChars(path, nativePath);

    if (gAnalysisResultClass == nullptr || gAnalysisResultConstructor == nullptr) {
        jclass localClass = env->FindClass("com/djportablepro/app/library/AnalysisResult");
        if (localClass != nullptr) {
            gAnalysisResultClass = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
            gAnalysisResultConstructor = env->GetMethodID(gAnalysisResultClass, "<init>", "(FJ[BI)V");
        } else {
            return nullptr;
        }
    }

    jbyteArray waveformArray = env->NewByteArray(output.waveform.size());
    env->SetByteArrayRegion(waveformArray, 0, output.waveform.size(), reinterpret_cast<const jbyte*>(output.waveform.data()));

    return env->NewObject(gAnalysisResultClass, gAnalysisResultConstructor, output.bpm, output.firstBeatFrame, waveformArray, output.version);
}

// --- NativeBpmAnalyzer JNI Bindings ---

extern "C"
JNIEXPORT jlong JNICALL
Java_com_djportablepro_app_audio_engine_NativeBpmAnalyzer_nInit(JNIEnv *env, jobject thiz, jint sampleRate, jint channels) {
    auto *analyzer = new BpmAnalyzer();
    analyzer->init(sampleRate, channels);
    return reinterpret_cast<jlong>(analyzer);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_djportablepro_app_audio_engine_NativeBpmAnalyzer_nPushPcm(JNIEnv *env, jobject thiz, jlong handle, jfloatArray samples, jint count) {
    if (handle == 0 || samples == nullptr || count <= 0) return;
    auto *analyzer = reinterpret_cast<BpmAnalyzer *>(handle);

    jfloat *pcm = env->GetFloatArrayElements(samples, nullptr);
    if (pcm != nullptr) {
        analyzer->pushPcmSamples(pcm, static_cast<size_t>(count));
        env->ReleaseFloatArrayElements(samples, pcm, JNI_ABORT);
    }
}

extern "C"
JNIEXPORT jfloat JNICALL
Java_com_djportablepro_app_audio_engine_NativeBpmAnalyzer_nGetBpm(JNIEnv *env, jobject thiz, jlong handle) {
    if (handle == 0) return 0.0f;
    auto *analyzer = reinterpret_cast<BpmAnalyzer *>(handle);
    return analyzer->getBpm();
}

extern "C"
JNIEXPORT jfloat JNICALL
Java_com_djportablepro_app_audio_engine_NativeBpmAnalyzer_nGetConfidence(JNIEnv *env, jobject thiz, jlong handle) {
    if (handle == 0) return 0.0f;
    auto *analyzer = reinterpret_cast<BpmAnalyzer *>(handle);
    return analyzer->getConfidence();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_djportablepro_app_audio_engine_NativeBpmAnalyzer_nReset(JNIEnv *env, jobject thiz, jlong handle) {
    if (handle == 0) return;
    auto *analyzer = reinterpret_cast<BpmAnalyzer *>(handle);
    analyzer->reset();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_djportablepro_app_audio_engine_NativeBpmAnalyzer_nRelease(JNIEnv *env, jobject thiz, jlong handle) {
    if (handle == 0) return;
    auto *analyzer = reinterpret_cast<BpmAnalyzer *>(handle);
    analyzer->release();
    delete analyzer;
}

extern "C"
JNIEXPORT jobject JNICALL
Java_com_djportablepro_app_audio_engine_NativeBpmAnalyzer_nAnalyzeFile(JNIEnv *env, jobject thiz, jstring path, jfloat referenceHint) {
    if (path == nullptr) return nullptr;

    const char *nativePath = env->GetStringUTFChars(path, nullptr);

    TrackAnalyzer analyzer;
    AnalysisOutput output = analyzer.analyze(nativePath, referenceHint);

    env->ReleaseStringUTFChars(path, nativePath);

    if (gAnalysisResultClass == nullptr || gAnalysisResultConstructor == nullptr) {
        jclass localClass = env->FindClass("com/djportablepro/app/library/AnalysisResult");
        if (localClass != nullptr) {
            gAnalysisResultClass = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
            gAnalysisResultConstructor = env->GetMethodID(gAnalysisResultClass, "<init>", "(FJ[BI)V");
        } else {
            return nullptr;
        }
    }

    jbyteArray waveformArray = env->NewByteArray(output.waveform.size());
    if (output.waveform.size() > 0) {
        env->SetByteArrayRegion(waveformArray, 0, output.waveform.size(), reinterpret_cast<const jbyte*>(output.waveform.data()));
    }

    return env->NewObject(gAnalysisResultClass, gAnalysisResultConstructor, output.bpm, output.firstBeatFrame, waveformArray, output.version);
}

extern "C"
JNIEXPORT jobject JNICALL
Java_com_djportablepro_app_audio_engine_NativeBpmAnalyzer_nAnalyzeBuffer(JNIEnv *env, jobject thiz, jfloatArray pcmArray, jint totalSamples, jint sampleRate, jint channels, jfloat referenceHint) {
    if (gAnalysisResultClass == nullptr || gAnalysisResultConstructor == nullptr) {
        jclass localClass = env->FindClass("com/djportablepro/app/library/AnalysisResult");
        if (localClass != nullptr) {
            gAnalysisResultClass = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
            gAnalysisResultConstructor = env->GetMethodID(gAnalysisResultClass, "<init>", "(FJ[BI)V");
        } else {
            return nullptr;
        }
    }

    jbyteArray emptyWaveform = env->NewByteArray(300);
    return env->NewObject(gAnalysisResultClass, gAnalysisResultConstructor, 128.0f, 0LL, emptyWaveform, 12);
}
