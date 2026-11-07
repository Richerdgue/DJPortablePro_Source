# DJ Portable Pro - Proguard Rules for JNI and Professional Audio

# 1. Conservar firmas nativas (JNI) para evitar NoSuchMethodError
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# 2. Conservar clases de estado y datos (Immutable/Stable) para Compose y JNI
-keep class com.djportablepro.app.audio.** { *; }
-keep class com.djportablepro.app.midi.** { *; }

# 3. Soporte para anotación @Keep
-keep @androidx.annotation.Keep class *
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# 4. Conservar JvmStatic y parámetros de métodos para vinculación JNI
-keepattributes *Annotation*,Signature,EnclosingMethod,InnerClasses
-keepclassmembers class ** {
    @kotlin.jvm.JvmStatic <methods>;
}

# 5. Evitar que R8 elimine el Bridge si solo se usa desde C++
-keep class com.djportablepro.app.audio.native.NativeSyncBridge { *; }

# 6. Supresión de logs en producción para máximo rendimiento y seguridad
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
