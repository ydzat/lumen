import org.jetbrains.compose.desktop.application.dsl.TargetFormat

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

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)

            packageName = "Lumen"
            packageVersion = "0.1.0"
            description = "Lumen — Personal AI Assistant"
            vendor = "Lumen Project"

            modules(
                "java.instrument",
                "java.management",
                "java.net.http",
                "java.sql",
                "jdk.unsupported",
            )

            linux {
                iconFile.set(project.file("src/main/resources/icons/lumen.png"))
                debPackageVersion = "0.1.0"
                appCategory = "Utility"
            }

            macOS {
                iconFile.set(project.file("src/main/resources/icons/lumen.icns"))
                bundleID = "com.lumen.desktop"
                dmgPackageVersion = "1.0.0"
                dmgPackageBuildVersion = "1.0.0"
            }

            windows {
                iconFile.set(project.file("src/main/resources/icons/lumen.ico"))
                menuGroup = "Lumen"
                upgradeUuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
            }
        }
    }
}
