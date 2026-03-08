# ============================================================
# Lumen Android — R8 / ProGuard keep rules
# ============================================================

# ---- ObjectBox entities & generated cursor/properties classes ----
-keep class com.lumen.core.database.entities.** { *; }
-keep class com.lumen.core.database.entities.**_ { *; }
-keep class io.objectbox.** { *; }
-dontwarn io.objectbox.**

# ---- kotlinx-serialization ----
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.lumen.**$$serializer { *; }
-keepclassmembers class com.lumen.** {
    *** Companion;
}

# ---- Koin DI ----
-keep class org.koin.** { *; }
-dontwarn org.koin.**
-keepclassmembers class * {
    @org.koin.core.annotation.* <methods>;
}

# ---- Ktor client (CIO engine + content negotiation) ----
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ---- Koog AI Agent ----
-keep class ai.koog.** { *; }
-dontwarn ai.koog.**

# ---- ONNX Runtime (native JNI) ----
-keep class com.microsoft.onnxruntime.** { *; }
-dontwarn com.microsoft.onnxruntime.**

# ---- DJL HuggingFace Tokenizers (native JNI) ----
-keep class ai.djl.** { *; }
-dontwarn ai.djl.**

# ---- RSS Parser ----
-keep class com.prof18.rssparser.** { *; }
-dontwarn com.prof18.rssparser.**

# ---- Apache PDFBox Android ----
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn org.bouncycastle.**

# ---- Compose / AndroidX (usually handled by default rules) ----
-dontwarn androidx.**

# ---- General Kotlin rules ----
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings { <fields>; }
