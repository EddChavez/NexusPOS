package org.ozafry.nexuspos

class JVMPlatform: Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
}

actual fun getPlatform(): Platform = JVMPlatform()

actual fun currentEpochMillis(): Long = System.currentTimeMillis()