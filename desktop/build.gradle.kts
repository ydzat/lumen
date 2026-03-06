plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.desktop.currentOs)

    implementation(libs.koin.core)
    implementation(libs.coroutines.core)
}

compose.desktop {
    application {
        mainClass = "com.lumen.desktop.MainKt"
    }
}
