plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.android.library)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
                }
            }
        }
    }

    jvm()

    sourceSets {
        val jvmCommonMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                compileOnly(libs.onnxruntime)
                implementation(libs.djl.tokenizers)
            }
        }

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(libs.coroutines.core)
            implementation(libs.serialization.json)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.koog.agents)
            implementation(libs.rssparser)
            api(project(":shared-db"))
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        androidMain {
            dependsOn(jvmCommonMain)
            dependencies {
                implementation(libs.coroutines.android)
                implementation(libs.ktor.client.cio)
                implementation(libs.koin.android)
                implementation(libs.onnxruntime.android)
                implementation(libs.androidx.work.runtime)
                implementation(libs.pdfbox.android)
            }
        }

        jvmMain {
            dependsOn(jvmCommonMain)
            dependencies {
                implementation(libs.onnxruntime)
                implementation(libs.ktor.client.cio)
                implementation(libs.pdfbox)
            }
        }

        jvmTest.dependencies {
            implementation(libs.ktor.client.mock)
            implementation(libs.objectbox.linux)
        }
    }
}

android {
    namespace = "com.lumen.shared"
    compileSdk = properties["android.compileSdk"].toString().toInt()

    defaultConfig {
        minSdk = properties["android.minSdk"].toString().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
