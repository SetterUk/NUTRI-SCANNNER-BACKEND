# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Preserve line numbers so Crashlytics/Sentry stack traces are readable.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ── LiteRT-LM / ML Kit GenAI (Gemma 4 E2B on-device inference) ──────────────
-keep class com.google.mlkit.genai.** { *; }
-keep class com.google.android.gms.tflite.** { *; }
-keep class org.tensorflow.lite.** { *; }
-keep class com.google.android.gms.internal.mlkit_genai.** { *; }
-dontwarn com.google.mlkit.genai.**
-dontwarn org.tensorflow.lite.**

# Keep LiteRT native interop symbols
-keepclassmembers class * {
    native <methods>;
}

# PDF parsing (PdfBox Android)
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**

# ── Retrofit + Gson ─────────────────────────────────────────────────────────
# Gson maps JSON by field name via reflection; R8 must not rename or strip these
# models (they are also stored as JSON in Room via Converters).
-keep class com.example.healthheatv2.network.** { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*, AnnotationDefault
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# Retrofit 2.9 predates its bundled R8 rules; without these, suspend functions in
# ApiService fail in release builds with R8 full mode.
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# ── MediaPipe LLM inference (on-device Gemma) ───────────────────────────────
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**
