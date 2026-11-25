import java.util.Properties

rootProject.name = "NexusPOS"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

val androidSdkConfigured = providers.environmentVariable("ANDROID_HOME").isPresent ||
    providers.environmentVariable("ANDROID_SDK_ROOT").isPresent ||
    file("local.properties").takeIf { it.isFile }?.let { local ->
        Properties().apply { local.inputStream().use(::load) }.containsKey("sdk.dir")
    } == true
if (providers.gradleProperty("nexus.android").map(String::toBooleanStrict).getOrElse(androidSdkConfigured)) {
    include(":androidApp")
}
include(":shared:core")
include(":shared:contracts")
include(":server")
include(":client:app")
include(":client:desktop")
