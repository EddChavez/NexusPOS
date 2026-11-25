package mx.nexuspos.desktop.config

data class AppConfig(
    val serverHost: String = "localhost",
    val serverPort: Int = 8080,
    val serverScheme: String = "http",
    val registerId: String = "caja-1",
    val registerName: String = "Caja Principal",
    val connectTimeoutMs: Int = 3000,
    val salesUiVersion: String = "V2",
    val theme: TerminalTheme = TerminalTheme.SYSTEM,
    val startFullScreen: Boolean = false,
    val terminalId: String = ""
) {
    val baseUrl: String
        get() = "$serverScheme://$serverHost:$serverPort"
}

enum class TerminalTheme(val label: String) { SYSTEM("Sistema"), LIGHT("Claro"), DARK("Oscuro") }
