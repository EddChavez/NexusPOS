import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.kotlinSerialization)
}

if (rootProject.findProject(":androidApp") != null) {
    apply(plugin = libs.plugins.androidMultiplatformLibrary.get().pluginId)
}

kotlin {
    if (plugins.hasPlugin(libs.plugins.androidMultiplatformLibrary.get().pluginId)) {
        (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryExtension>("android") {
            namespace = "mx.nexuspos.shared.contracts"
            compileSdk = libs.versions.android.sharedCompileSdk.get().toInt()
            minSdk = libs.versions.android.minSdk.get().toInt()
        }
    }
    jvm()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":shared:core"))
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
