#include <jni.h>
#include <string>
#include "BpmAnalyzerEngine.h"
#include <android/log.h>

#define TAG "JniBpmBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

extern "C" JNIEXPORT jobject JNICALL
Java_com_djportablepro_app_kymatik_data_AubioSuperpoweredImpl_nAnalyzeTrack(
    JNIEnv *env,
    jobject thiz,
    jstring filePath,
    jdouble referenceBpmHint
) {
    const char *path = env->GetStringUTFChars(filePath, nullptr);
    std::string pathStr = path ? path : "";
    if (path) env->ReleaseStringUTFChars(filePath, path);

    BpmAnalyzerEngine engine;
    KymatikNativeOutput output = engine.analyzeTrack(pathStr, referenceBpmHint);

    jclass resultClass = env->FindClass("com/djportablepro/app/kymatik/domain/KymatikResult");
    if (resultClass == nullptr) return nullptr;

    jmethodID ctor = env->GetMethodID(resultClass, "<init>", "(DJDLjava/lang/String;[BI)V");
    if (ctor == nullptr) return nullptr;

    jstring keyStr = env->NewStringUTF(output.key.c_str());
    jbyteArray waveformArray = env->NewByteArray(static_cast<jsize>(output.waveform.size()));
    if (output.waveform.size() > 0) {
        env->SetByteArrayRegion(waveformArray, 0, static_cast<jsize>(output.waveform.size()), reinterpret_cast<const jbyte*>(output.waveform.data()));
    }

    jobject resultObj = env->NewObject(
        resultClass,
        ctor,
        output.bpm,
        output.firstBeatFrame,
        output.confidence,
        keyStr,
        waveformArray,
        output.version
    );

    return resultObj;
}
