package mx.nexuspos.client.features.scale

enum class ScaleProtocol(val code: String, val displayName: String) {
    TORREY("TORREY", "Torrey (L-EQ / MFQ / PCR / W-Label)"),
    RHINO("RHINO", "Rhino (BAR-8 / BAB-30 / I-QY)"),
    GENERIC("GENERIC", "Genérico Continuo (RS-232 / USB)"),
    SIMULATOR("SIMULATOR", "Simulador Virtual (Pruebas / Demo)"),
    NONE("NONE", "Báscula Desactivada");

    companion object {
        fun fromString(value: String?): ScaleProtocol {
            return when (value?.trim()?.uppercase()) {
                "TORREY" -> TORREY
                "RHINO" -> RHINO
                "GENERIC", "GENERIC_STREAM" -> GENERIC
                "SIMULATOR", "MOCK" -> SIMULATOR
                else -> NONE
            }
        }
    }
}

data class ScaleConfig(
    val enabled: Boolean = true,
    val portName: String = "AUTO", // AUTO, COM1, COM3, /dev/ttyUSB0, SIMULATOR, etc.
    val baudRate: Int = 9600,
    val dataBits: Int = 8,
    val stopBits: Int = 1,
    val parity: String = "NONE", // NONE, EVEN, ODD
    val protocol: ScaleProtocol = ScaleProtocol.TORREY,
    val autoAcceptStableWeight: Boolean = false,
    val pollIntervalMs: Long = 250L
)

data class ScaleUiConfig(
    val enabled: Boolean = true,
    val portName: String = "AUTO",
    val baudRate: Int = 9600,
    val protocol: String = "TORREY",
    val autoAcceptStableWeight: Boolean = false,
    val pollIntervalMs: Long = 250L
)
