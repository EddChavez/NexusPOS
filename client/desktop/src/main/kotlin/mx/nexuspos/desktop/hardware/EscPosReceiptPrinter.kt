package mx.nexuspos.desktop.hardware

import mx.nexuspos.contracts.cash.CashSessionSummary
import mx.nexuspos.contracts.sales.SaleResponse
import mx.nexuspos.contracts.system.SettingsDto
import mx.nexuspos.core.Money
import mx.nexuspos.core.Quantity
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * ESC/POS thermal receipt printer adapter (58mm / 80mm).
 * Formats sale receipts, reprints, and cash cut summaries.
 */
class EscPosReceiptPrinter(
    val columnWidth: Int = 42, // Standard 80mm = 42-48 cols, 58mm = 32 cols
    private val charset: Charset = Charset.forName("CP437")
) {

    constructor(paperWidth: PaperWidth, charset: Charset = Charset.forName("CP437")) : this(
        columnWidth = paperWidth.columns,
        charset = charset
    )

    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    companion object {
        val ESC: Byte = 0x1B
        val GS: Byte = 0x1D
        val INIT = byteArrayOf(ESC, 0x40)
        val ALIGN_LEFT = byteArrayOf(ESC, 0x61, 0x00)
        val ALIGN_CENTER = byteArrayOf(ESC, 0x61, 0x01)
        val ALIGN_RIGHT = byteArrayOf(ESC, 0x61, 0x02)
        val BOLD_ON = byteArrayOf(ESC, 0x45, 0x01)
        val BOLD_OFF = byteArrayOf(ESC, 0x45, 0x00)
        val CUT_PAPER = byteArrayOf(GS, 0x56, 0x41, 0x10) // GS V A 16 (Feed and cut)

        // ESC/POS Cash Drawer pulse commands (Pin 2 and Pin 5)
        val DRAWER_KICK_PIN2 = byteArrayOf(ESC, 0x70, 0x00, 0x19, 0xFA.toByte())
        val DRAWER_KICK_PIN5 = byteArrayOf(ESC, 0x70, 0x01, 0x19, 0xFA.toByte())
    }

    private fun formatTimestamp(epochMs: Long): String {
        return try {
            dateFormatter.format(Instant.ofEpochMilli(epochMs))
        } catch (e: Exception) {
            epochMs.toString()
        }
    }

    /**
     * Formats a complete sale receipt into plain text.
     */
    fun formatSaleReceiptText(
        sale: SaleResponse,
        settings: SettingsDto? = null,
        isReprint: Boolean = false
    ): String {
        val sb = StringBuilder()
        val sep = "=".repeat(columnWidth)
        val thinSep = "-".repeat(columnWidth)

        // Store Header
        sb.appendLine(centerText(settings?.storeName ?: "NEXUS POS - ABARROTES"))
        settings?.storeAddress?.takeIf { it.isNotBlank() }?.let { sb.appendLine(centerText(it)) }
        settings?.storePhone?.takeIf { it.isNotBlank() }?.let { sb.appendLine(centerText("Tel: $it")) }
        sb.appendLine(sep)

        if (isReprint) {
            sb.appendLine(centerText("*** COPIA DE REIMPRESIÓN ***"))
            sb.appendLine(thinSep)
        }

        // Sale metadata
        sb.appendLine("Folio: #${sale.folio}")
        sb.appendLine("Fecha: ${formatTimestamp(sale.createdAt)}")
        sb.appendLine("Cajero: ${sale.cashierName}")
        sb.appendLine(thinSep)

        // Column headers
        sb.appendLine(formatRow("CANT. DESCRIPCIÓN", "IMPORTE"))
        sb.appendLine(thinSep)

        // Items
        for (item in sale.lines) {
            val qtyStr = "${Quantity(item.quantityMillis).format()} ${item.description}"
            val totalStr = Money(item.totalCents).format()
            sb.appendLine(formatRow(qtyStr, totalStr))
            if (item.quantityMillis != 1000L) {
                sb.appendLine("  @ ${Money(item.unitPriceCents).format()} c/u")
            }
        }
        sb.appendLine(sep)

        // Totals
        sb.appendLine(formatRow("SUBTOTAL:", Money(sale.subtotalCents).format()))
        sb.appendLine(formatRow("IVA:", Money(sale.taxCents).format()))
        sb.appendLine(formatRow("TOTAL:", Money(sale.totalCents).format()))
        sb.appendLine(thinSep)

        // Payment Details
        for (pay in sale.payments) {
            when (pay.method) {
                "CASH" -> {
                    sb.appendLine(formatRow("EFECTIVO:", Money(pay.amountCents).format()))
                    if (pay.receivedCents > 0L) {
                        sb.appendLine(formatRow("RECIBIDO:", Money(pay.receivedCents).format()))
                        sb.appendLine(formatRow("CAMBIO:", Money(pay.changeCents).format()))
                    }
                }
                "CREDIT" -> {
                    sb.appendLine(centerText("** VENTA A CRÉDITO / FIADO **"))
                    sale.customerName?.let { sb.appendLine("Cliente: $it") }
                    sb.appendLine(formatRow("Cargo a Cuenta:", Money(pay.amountCents).format()))
                    sale.customerRemainingCreditCents?.let {
                        sb.appendLine(formatRow("Crédito Disponible:", Money(it).format()))
                    }
                    sb.appendLine()
                    sb.appendLine(centerText("Firma del Cliente:"))
                    sb.appendLine()
                    sb.appendLine(centerText("_______________________________"))
                }
                else -> {
                    sb.appendLine(formatRow("PAGO (${pay.method}):", Money(pay.amountCents).format()))
                }
            }
        }

        sb.appendLine(sep)
        sb.appendLine(centerText(settings?.ticketHeader ?: "¡Gracias por su compra!"))
        sb.appendLine(centerText(settings?.ticketFooter ?: "Conserve su ticket para aclaraciones."))
        sb.appendLine()
        sb.appendLine()

        return sb.toString()
    }

    /**
     * Formats cash cut summary text.
     */
    fun formatCashCutText(
        session: CashSessionSummary,
        settings: SettingsDto? = null
    ): String {
        val sb = StringBuilder()
        val sep = "=".repeat(columnWidth)
        val thinSep = "-".repeat(columnWidth)

        sb.appendLine(centerText(settings?.storeName ?: "NEXUS POS"))
        sb.appendLine(centerText("*** CORTE DE CAJA (TURNO) ***"))
        sb.appendLine(sep)
        sb.appendLine("Sesión ID: #${session.sessionId}")
        sb.appendLine("Cajero: ${session.cashierName}")
        sb.appendLine("Apertura: ${formatTimestamp(session.openedAt)}")
        session.closedAt?.let { sb.appendLine("Cierre: ${formatTimestamp(it)}") }
        sb.appendLine(thinSep)

        sb.appendLine(formatRow("Fondo Inicial:", Money(session.initialFundCents).format()))
        sb.appendLine(formatRow("(+) Ventas en Efectivo:", Money(session.salesCashCents).format()))
        sb.appendLine(formatRow("(+) Abonos de Crédito:", Money(session.creditPaymentsCashCents).format()))
        sb.appendLine(formatRow("(+) Entradas Manuales:", Money(session.cashInCents).format()))
        sb.appendLine(formatRow("(-) Retiros / Salidas:", Money(session.cashOutCents).format()))
        sb.appendLine(thinSep)
        sb.appendLine(formatRow("EFECTIVO ESPERADO:", Money(session.expectedCashCents).format()))

        session.countedCashCents?.let { counted ->
            sb.appendLine(formatRow("Efectivo Contado:", Money(counted).format()))
            val diffCents = session.differenceCents ?: 0L
            val diffStr = if (diffCents >= 0L) "+${Money(diffCents).format()}" else "-${Money(-diffCents).format()}"
            sb.appendLine(formatRow("DIFERENCIA (Arqueo):", diffStr))
        }

        sb.appendLine(sep)
        sb.appendLine()
        sb.appendLine(centerText("Firma del Cajero: __________________"))
        sb.appendLine()
        sb.appendLine(centerText("Firma Supervisor: __________________"))
        sb.appendLine()

        return sb.toString()
    }

    /**
     * Converts formatted text to ESC/POS byte sequence.
     */
    fun generateEscPosBytes(
        text: String,
        cutPaper: Boolean = true,
        openDrawer: Boolean = false
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(INIT)
        if (openDrawer) {
            out.write(generateDrawerKickBytes())
        }
        out.write(text.toByteArray(charset))
        out.write(byteArrayOf(ESC, 0x64, 0x03)) // feed 3 lines
        if (cutPaper) {
            out.write(CUT_PAPER)
        }
        return out.toByteArray()
    }

    /**
     * Generates cash drawer opening pulse bytes (compatible with RJ11 Pin 2 and Pin 5).
     */
    fun generateDrawerKickBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(INIT)
        out.write(DRAWER_KICK_PIN2)
        out.write(DRAWER_KICK_PIN5)
        return out.toByteArray()
    }

    /**
     * Formats a test receipt to verify thermal printer communication and paper width.
     */
    fun generateTestReceiptText(
        storeName: String = "NEXUS POS",
        config: PrinterConfig? = null
    ): String {
        val sb = StringBuilder()
        val sep = "=".repeat(columnWidth)
        val thinSep = "-".repeat(columnWidth)

        sb.appendLine(centerText(storeName))
        sb.appendLine(centerText("*** PRUEBA DE IMPRESIÓN ***"))
        sb.appendLine(sep)
        sb.appendLine("Fecha: ${formatTimestamp(System.currentTimeMillis())}")
        if (config != null) {
            sb.appendLine("Tipo: ${config.connectionType.name}")
            if (config.connectionType == PrinterConnectionType.USB_OS) {
                sb.appendLine("Impresora: ${config.osPrinterName.ifBlank { "Predeterminada SO" }}")
            } else if (config.connectionType == PrinterConnectionType.ETHERNET_TCP) {
                sb.appendLine("IP / Red: ${config.tcpHost}:${config.tcpPort}")
            }
            sb.appendLine("Ancho: ${config.paperWidth.label}")
        }
        sb.appendLine(thinSep)
        sb.appendLine(centerText("COMUNICACIÓN ESC/POS EXITOSA"))
        sb.appendLine("Prueba de caracteres:")
        sb.appendLine("1234567890 - ABCDEFGHIJKLMNOPQRSTUVWXYZ")
        sb.appendLine("Simbolos: $ € £ ¥ # & @ % * + - /")
        sb.appendLine(thinSep)
        sb.appendLine(centerText("NexusPOS LAN-First Engine"))
        sb.appendLine()
        sb.appendLine()

        return sb.toString()
    }

    /**
     * Generates complete ESC/POS bytes for a test receipt.
     */
    fun generateTestReceiptBytes(
        storeName: String = "NEXUS POS",
        config: PrinterConfig? = null,
        cutPaper: Boolean = true
    ): ByteArray {
        val text = generateTestReceiptText(storeName, config)
        return generateEscPosBytes(text, cutPaper = cutPaper, openDrawer = false)
    }

    private fun centerText(text: String): String {
        if (text.length >= columnWidth) return text.take(columnWidth)
        val leftPad = (columnWidth - text.length) / 2
        return " ".repeat(leftPad) + text
    }

    private fun formatRow(left: String, right: String): String {
        val totalLen = columnWidth
        if (left.length + right.length + 1 > totalLen) {
            val maxLeft = totalLen - right.length - 1
            val truncatedLeft = if (maxLeft > 0) left.take(maxLeft) else left
            return truncatedLeft + " " + right
        }
        val spaces = totalLen - left.length - right.length
        return left + " ".repeat(spaces) + right
    }
}
