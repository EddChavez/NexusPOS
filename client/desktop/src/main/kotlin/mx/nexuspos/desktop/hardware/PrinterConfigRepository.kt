package mx.nexuspos.desktop.hardware

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties

class PrinterConfigRepository(
    private val configFile: File = defaultConfigFile()
) {
    companion object {
        fun defaultConfigFile(): File {
            val userHome = System.getProperty("user.home") ?: "."
            val dir = File(userHome, ".nexuspos")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return File(dir, "printer.properties")
        }
    }

    fun load(): PrinterConfig {
        if (!configFile.exists()) {
            return PrinterConfig()
        }

        return try {
            val props = Properties()
            FileInputStream(configFile).use { props.load(it) }

            val connTypeStr = props.getProperty("printer.connectionType", "NONE")
            val connType = try {
                PrinterConnectionType.valueOf(connTypeStr.trim().uppercase())
            } catch (e: Exception) {
                PrinterConnectionType.NONE
            }

            val osName = props.getProperty("printer.osName", "")
            val tcpHost = props.getProperty("printer.tcpHost", "192.168.1.200")
            val tcpPort = props.getProperty("printer.tcpPort", "9100").toIntOrNull() ?: 9100
            val paperWidthStr = props.getProperty("printer.paperWidth", "80MM")
            val paperWidth = PaperWidth.fromString(paperWidthStr)
            val autoPrint = props.getProperty("printer.autoPrintOnSale", "true").toBoolean()
            val openDrawer = props.getProperty("printer.openDrawerOnCashSale", "true").toBoolean()
            val timeout = props.getProperty("printer.connectionTimeoutMs", "3000").toIntOrNull() ?: 3000

            PrinterConfig(
                connectionType = connType,
                osPrinterName = osName,
                tcpHost = tcpHost,
                tcpPort = tcpPort,
                paperWidth = paperWidth,
                autoPrintOnSale = autoPrint,
                openDrawerOnCashSale = openDrawer,
                connectionTimeoutMs = timeout
            )
        } catch (e: Exception) {
            PrinterConfig()
        }
    }

    fun save(config: PrinterConfig) {
        try {
            configFile.parentFile?.let {
                if (!it.exists()) it.mkdirs()
            }
            val props = Properties()
            props.setProperty("printer.connectionType", config.connectionType.name)
            props.setProperty("printer.osName", config.osPrinterName)
            props.setProperty("printer.tcpHost", config.tcpHost)
            props.setProperty("printer.tcpPort", config.tcpPort.toString())
            props.setProperty("printer.paperWidth", if (config.paperWidth == PaperWidth.MM_58) "58MM" else "80MM")
            props.setProperty("printer.autoPrintOnSale", config.autoPrintOnSale.toString())
            props.setProperty("printer.openDrawerOnCashSale", config.openDrawerOnCashSale.toString())
            props.setProperty("printer.connectionTimeoutMs", config.connectionTimeoutMs.toString())

            FileOutputStream(configFile).use { out ->
                props.store(out, "NexusPOS Hardware Printer Configuration")
            }
        } catch (e: Exception) {
            // Log or ignore file system write issues gracefully
        }
    }
}
