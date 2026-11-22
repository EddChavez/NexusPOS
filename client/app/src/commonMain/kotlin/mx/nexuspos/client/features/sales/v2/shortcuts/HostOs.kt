package mx.nexuspos.client.features.sales.v2.shortcuts

enum class HostOs {
    WINDOWS_LINUX,
    MACOS
}

object HostOsDetector {
    val current: HostOs by lazy {
        val os = try {
            System.getProperty("os.name")?.lowercase() ?: ""
        } catch (_: Exception) {
            ""
        }
        if (os.contains("mac") || os.contains("darwin")) HostOs.MACOS else HostOs.WINDOWS_LINUX
    }
}
