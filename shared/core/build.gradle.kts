import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary) apply false
}

if (rootProject.findProject(":androidApp") != null) {
    apply(plugin = libs.plugins.androidMultiplatformLibrary.get().pluginId)
}

kotlin {
    if (plugins.hasPlugin(libs.plugins.androidMultiplatformLibrary.get().pluginId)) {
        (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryExtension>("android") {
            namespace = "mx.nexuspos.shared.core"
            compileSdk = libs.versions.android.sharedCompileSdk.get().toInt()
            minSdk = libs.versions.android.minSdk.get().toInt()
        }
    }
    jvm()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // Pure Kotlin, no external dependencies
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
