package mx.nexuspos.desktop.hardware

enum class PrinterConnectionType {
    NONE,       // Impresión desactivada
    USB_OS,     // Impresora USB o del Sistema Operativo (Java Print Service)
    ETHERNET_TCP // Impresora de Red Ethernet o WiFi (Socket TCP puerto 9100)
}

enum class PaperWidth(val columns: Int, val label: String) {
    MM_80(42, "80 mm (Estándar - 42 columnas)"),
    MM_58(32, "58 mm (Compacto - 32 columnas)");

    companion object {
        fun fromColumns(cols: Int): PaperWidth = if (cols <= 36) MM_58 else MM_80
        fun fromString(str: String?): PaperWidth = when (str?.trim()?.uppercase()) {
            "58MM", "58", "MM_58" -> MM_58
            else -> MM_80
        }
    }
}

data class PrinterConfig(
    val connectionType: PrinterConnectionType = PrinterConnectionType.NONE,
    val osPrinterName: String = "",
    val tcpHost: String = "192.168.1.200",
    val tcpPort: Int = 9100,
    val paperWidth: PaperWidth = PaperWidth.MM_80,
    val autoPrintOnSale: Boolean = true,
    val openDrawerOnCashSale: Boolean = true,
    val connectionTimeoutMs: Int = 3000
)
