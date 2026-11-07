package mx.nexuspos.desktop.hardware

import mx.nexuspos.contracts.cash.CashSessionSummary
import mx.nexuspos.contracts.cash.DailyFinancialReportDto
import mx.nexuspos.contracts.sales.SaleResponse
import mx.nexuspos.contracts.system.SettingsDto
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import javax.print.DocFlavor
import javax.print.DocPrintJob
import javax.print.PrintException
import javax.print.PrintService
import javax.print.PrintServiceLookup
import javax.print.SimpleDoc
import javax.print.attribute.HashPrintRequestAttributeSet

interface PrinterDispatcher {
    fun getAvailableOsPrinters(): List<String>
    fun printBytes(bytes: ByteArray, config: PrinterConfig): Result<Unit>
    fun printSaleReceipt(sale: SaleResponse, settings: SettingsDto?, config: PrinterConfig, isReprint: Boolean = false): Result<Unit>
    fun printCashCut(session: CashSessionSummary, settings: SettingsDto?, config: PrinterConfig, isFinalZCut: Boolean = false): Result<Unit>
    fun printDailyReport(report: DailyFinancialReportDto, settings: SettingsDto?, config: PrinterConfig): Result<Unit>
    fun openCashDrawer(config: PrinterConfig): Result<Unit>
    fun printTestReceipt(storeName: String, config: PrinterConfig): Result<Unit>
}

class DefaultPrinterDispatcher : PrinterDispatcher {

    override fun getAvailableOsPrinters(): List<String> {
        return try {
            val services: Array<PrintService> = PrintServiceLookup.lookupPrintServices(null, null)
            services.map { it.name }.distinct().sorted()
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun printBytes(bytes: ByteArray, config: PrinterConfig): Result<Unit> {
        return when (config.connectionType) {
            PrinterConnectionType.NONE -> Result.success(Unit)
            PrinterConnectionType.USB_OS -> printToOsPrinter(bytes, config.osPrinterName)
            PrinterConnectionType.ETHERNET_TCP -> printToTcpSocket(
                bytes = bytes,
                host = config.tcpHost,
                port = config.tcpPort,
                timeoutMs = config.connectionTimeoutMs
            )
        }
    }

    override fun printSaleReceipt(
        sale: SaleResponse,
        settings: SettingsDto?,
        config: PrinterConfig,
        isReprint: Boolean
    ): Result<Unit> {
        if (config.connectionType == PrinterConnectionType.NONE) {
            return Result.success(Unit)
        }
        val printer = EscPosReceiptPrinter(config.paperWidth)
        val text = printer.formatSaleReceiptText(sale, settings, isReprint = isReprint)
        val bytes = printer.generateEscPosBytes(text, cutPaper = true, openDrawer = false)
        return printBytes(bytes, config)
    }

    override fun printCashCut(
        session: CashSessionSummary,
        settings: SettingsDto?,
        config: PrinterConfig,
        isFinalZCut: Boolean
    ): Result<Unit> {
        if (config.connectionType == PrinterConnectionType.NONE) {
            return Result.success(Unit)
        }
        val printer = EscPosReceiptPrinter(config.paperWidth)
        val text = printer.formatCashCutText(session, settings, isFinalZCut = isFinalZCut)
        val bytes = printer.generateEscPosBytes(text, cutPaper = true, openDrawer = false)
        return printBytes(bytes, config)
    }

    override fun printDailyReport(
        report: DailyFinancialReportDto,
        settings: SettingsDto?,
        config: PrinterConfig
    ): Result<Unit> {
        if (config.connectionType == PrinterConnectionType.NONE) {
            return Result.success(Unit)
        }
        val printer = EscPosReceiptPrinter(config.paperWidth)
        val text = printer.formatDailyReportText(report, settings)
        val bytes = printer.generateEscPosBytes(text, cutPaper = true, openDrawer = false)
        return printBytes(bytes, config)
    }

    override fun openCashDrawer(config: PrinterConfig): Result<Unit> {
        if (config.connectionType == PrinterConnectionType.NONE) {
            return Result.success(Unit)
        }
        val printer = EscPosReceiptPrinter(config.paperWidth)
        val bytes = printer.generateDrawerKickBytes()
        return printBytes(bytes, config)
    }

    override fun printTestReceipt(storeName: String, config: PrinterConfig): Result<Unit> {
        if (config.connectionType == PrinterConnectionType.NONE) {
            return Result.failure(
                IllegalStateException("La conexión de impresora está desactivada (NONE). Seleccione USB o Red Ethernet antes de probar.")
            )
        }
        val printer = EscPosReceiptPrinter(config.paperWidth)
        val bytes = printer.generateTestReceiptBytes(storeName, config, cutPaper = true)
        return printBytes(bytes, config)
    }

    // --- Implementaciones de bajo nivel ---

    private fun printToOsPrinter(bytes: ByteArray, printerName: String): Result<Unit> {
        return try {
            val services: Array<PrintService> = PrintServiceLookup.lookupPrintServices(null, null)
            val selectedService = if (printerName.isNotBlank()) {
                services.firstOrNull { it.name.equals(printerName, ignoreCase = true) }
                    ?: return Result.failure(
                        IllegalArgumentException("No se encontró la impresora del sistema: '$printerName'. Verifique que esté encendida y conectada.")
                    )
            } else {
                PrintServiceLookup.lookupDefaultPrintService()
                    ?: services.firstOrNull()
                    ?: return Result.failure(
                        IllegalStateException("No hay impresoras disponibles configuradas en el sistema operativo.")
                    )
            }

            val flavor = DocFlavor.INPUT_STREAM.AUTOSENSE
            val doc = SimpleDoc(ByteArrayInputStream(bytes), flavor, null)
            val job: DocPrintJob = selectedService.createPrintJob()
            job.print(doc, HashPrintRequestAttributeSet())
            Result.success(Unit)
        } catch (pe: PrintException) {
            Result.failure(IOException("Error de impresión en '${printerName}': ${pe.message}", pe))
        } catch (e: Exception) {
            Result.failure(IOException("Error inesperado al despachar a la impresora USB: ${e.message}", e))
        }
    }

    private fun printToTcpSocket(
        bytes: ByteArray,
        host: String,
        port: Int,
        timeoutMs: Int
    ): Result<Unit> {
        if (host.isBlank()) {
            return Result.failure(IllegalArgumentException("La dirección IP de la impresora de red no puede estar vacía."))
        }
        var socket: Socket? = null
        return try {
            socket = Socket()
            socket.soTimeout = timeoutMs
            val address = InetSocketAddress(host, port)
            socket.connect(address, timeoutMs)

            socket.getOutputStream().use { out ->
                out.write(bytes)
                out.flush()
            }
            Result.success(Unit)
        } catch (te: SocketTimeoutException) {
            Result.failure(
                IOException("Tiempo de espera agotado al conectar con la impresora de red ($host:$port). Verifique que esté conectada y encendida.", te)
            )
        } catch (ioe: IOException) {
            Result.failure(
                IOException("No se pudo conectar a la impresora de red ($host:$port): ${ioe.message}", ioe)
            )
        } catch (e: Exception) {
            Result.failure(
                IOException("Fallo de comunicación TCP con la impresora ($host:$port): ${e.message}", e)
            )
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {}
        }
    }
}
