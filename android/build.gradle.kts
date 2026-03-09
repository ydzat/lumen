import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.lumen.android"
    compileSdk = properties["android.compileSdk"].toString().toInt()

    defaultConfig {
        applicationId = "com.lumen.android"
        minSdk = properties["android.minSdk"].toString().toInt()
        targetSdk = properties["android.targetSdk"].toString().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        create("release") {
            storeFile = file(
                localProps.getProperty("RELEASE_STORE_FILE")
                    ?: System.getenv("RELEASE_STORE_FILE")
                    ?: "release.jks",
            )
            storePassword = localProps.getProperty("RELEASE_STORE_PASSWORD")
                ?: System.getenv("RELEASE_STORE_PASSWORD")
                ?: ""
            keyAlias = localProps.getProperty("RELEASE_KEY_ALIAS")
                ?: System.getenv("RELEASE_KEY_ALIAS")
                ?: ""
            keyPassword = localProps.getProperty("RELEASE_KEY_PASSWORD")
                ?: System.getenv("RELEASE_KEY_PASSWORD")
                ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties",
                "META-INF/DEPENDENCIES",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
            )
        }
    }
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.ui)

    implementation(libs.androidx.activity.compose)
    implementation(libs.koin.android)
    implementation(libs.coroutines.android)
}
