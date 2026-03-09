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
    implementation(libs.coroutines.swing)
    implementation(libs.slf4j.simple)
}

compose.desktop {
    application {
        mainClass = "com.lumen.desktop.MainKt"

        nativeDistributions {
            val appVersion = "0.1.0"

            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)

            packageName = "Lumen"
            packageVersion = appVersion
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
                debPackageVersion = appVersion
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

// Configure JavaFX for the run task
afterEvaluate {
    tasks.withType<JavaExec> {
        doFirst {
            val fxJars = classpath.files
                .filter { it.name.contains("javafx") }
                .joinToString(File.pathSeparator) { it.absolutePath }
            if (fxJars.isNotBlank()) {
                jvmArgs = (jvmArgs ?: emptyList()) + listOf(
                    "--module-path", fxJars,
                    "--add-modules", "javafx.web,javafx.swing",
                    "--add-opens", "java.base/java.lang=ALL-UNNAMED",
                    "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED",
                    "--add-opens", "java.base/java.io=ALL-UNNAMED",
                    "--add-opens", "java.base/java.net=ALL-UNNAMED",
                    "--add-opens", "java.desktop/sun.font=ALL-UNNAMED",
                    "--add-opens", "java.desktop/java.awt=ALL-UNNAMED",
                    "--add-opens", "java.desktop/java.awt.font=ALL-UNNAMED",
                    "--add-opens", "java.desktop/sun.awt=ALL-UNNAMED",
                    "--add-opens", "java.desktop/sun.java2d=ALL-UNNAMED",
                )
            }
        }
    }
}
