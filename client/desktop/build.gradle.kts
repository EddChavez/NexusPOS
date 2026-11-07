import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared:core"))
    implementation(project(":shared:contracts"))
    implementation(project(":client:app"))

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.material3)
    implementation(libs.kotlinx.coroutinesSwing)
    implementation(libs.kotlinx.datetime)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.datetime)
}

compose.desktop {
    application {
        mainClass = "mx.nexuspos.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "NexusPOS"
            packageVersion = "1.0.0"
            description = "NexusPOS - Terminal de Punto de Venta"
            copyright = "© 2026 NexusPOS"
            vendor = "OzafryDev"

            windows {
                menuGroup = "NexusPOS"
                shortcut = true
                dirChooser = true
            }

            macOS {
                bundleID = "mx.nexuspos.desktop"
                dockName = "NexusPOS"
            }

            linux {
                shortcut = true
            }
        }
    }
}
