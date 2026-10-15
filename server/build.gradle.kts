plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    application
}

application {
    mainClass.set("mx.nexuspos.server.ApplicationKt")
}

dependencies {
    implementation(project(":shared:core"))
    implementation(project(":shared:contracts"))

    // Ktor Server
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.contentNegotiation)
    implementation(libs.ktor.server.serialization.json)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.statusPages)
    implementation(libs.ktor.server.callLogging)
    implementation(libs.logback)

    // Persistence & Security
    implementation(libs.postgresql)
    implementation(libs.hikaricp)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.jbcrypt)

    // Serialization & Datetime
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    // Testing
    testImplementation(libs.h2)
    testImplementation(libs.ktor.server.testHost)
    testImplementation(libs.ktor.client.contentNegotiation)
    testImplementation(libs.kotlin.testJunit)
}
