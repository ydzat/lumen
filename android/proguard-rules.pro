# ============================================================
# Lumen Android — R8 / ProGuard keep rules
# ============================================================
# Broad keep rules are intentional to avoid R8 stripping
# classes loaded via reflection (Koin DI, serialization, JNI).

# ---- ObjectBox entities & generated cursor/properties classes ----
-keep class com.lumen.core.database.entities.** { *; }
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

# ---- Readability4J + Jsoup ----
-keep class net.dankito.readability4j.** { *; }
-dontwarn net.dankito.readability4j.**
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# ---- Apache PDFBox Android ----
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn org.bouncycastle.**

# ---- Compose / AndroidX ----
-dontwarn androidx.**

# ---- General Kotlin rules ----
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings { <fields>; }

# ============================================================
# Koog transitive dependencies — optional JVM-only classes
# not available on Android (Netty native, OpenTelemetry,
# Lettuce/Redis, Jackson, Log4j, JFR, etc.)
# ============================================================
-dontwarn com.fasterxml.jackson.**
-dontwarn com.google.auto.value.**
-dontwarn io.micrometer.context.**
-dontwarn io.netty.channel.epoll.**
-dontwarn io.netty.channel.kqueue.**
-dontwarn io.netty.channel.uring.**
-dontwarn io.netty.internal.tcnative.**
-dontwarn io.opentelemetry.**
-dontwarn javax.enterprise.**
-dontwarn javax.naming.**
-dontwarn jdk.jfr.**
-dontwarn jdk.net.**
-dontwarn org.HdrHistogram.**
-dontwarn org.LatencyUtils.**
-dontwarn org.apache.commons.lang3.**
-dontwarn org.apache.log4j.**
-dontwarn org.apache.logging.log4j.**
-dontwarn reactor.blockhound.**
