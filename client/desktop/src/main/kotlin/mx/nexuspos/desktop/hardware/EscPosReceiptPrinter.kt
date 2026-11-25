package mx.nexuspos.desktop.hardware

import mx.nexuspos.contracts.cash.CashSessionSummary
import mx.nexuspos.contracts.cash.DailyFinancialReportDto
import mx.nexuspos.contracts.inventory.ExpirationsReportDto
import mx.nexuspos.contracts.inventory.LotExpirationStatus
import mx.nexuspos.contracts.inventory.ReorderSuggestionItemDto
import mx.nexuspos.contracts.inventory.ReorderUrgency
import mx.nexuspos.contracts.sales.SaleRefundResponse
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

        val DOUBLE_SIZE_ON = byteArrayOf(GS, 0x21, 0x11) // Double height & width
        val DOUBLE_SIZE_OFF = byteArrayOf(GS, 0x21, 0x00)
        val BARCODE_HEIGHT_60 = byteArrayOf(GS, 0x68, 60)
        val BARCODE_WIDTH_2 = byteArrayOf(GS, 0x77, 2)
        val BARCODE_HRI_BELOW = byteArrayOf(GS, 0x48, 2)

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
        isReprint: Boolean = false,
        isContingency: Boolean = false
    ): String {
        val sb = StringBuilder()
        val sep = "=".repeat(columnWidth)
        val thinSep = "-".repeat(columnWidth)

        fun formatAmount(cents: Long): String =
            Money(cents).format().replaceFirst("$", settings?.currencySymbol ?: "$")


        // Store header
        (settings?.ticketHeader ?: "¡Gracias por su compra!").lineSequence()
            .filter { it.isNotBlank() }.forEach { sb.appendLine(centerText(it)) }
        sb.appendLine(centerText(settings?.storeName ?: "NEXUS POS - ABARROTES"))
        settings?.storeAddress?.takeIf { it.isNotBlank() }?.let { sb.appendLine(centerText(it)) }
        settings?.storePhone?.takeIf { it.isNotBlank() }?.let { sb.appendLine(centerText("Tel: $it")) }
        settings?.storeTaxId?.takeIf { it.isNotBlank() }?.let { sb.appendLine(centerText("RFC: $it")) }
        sb.appendLine(sep)

        val isOffline = isContingency || sale.id.startsWith("offline-")
        if (isOffline) {
            sb.appendLine(centerText("*** MODO CONTINGENCIA (OFFLINE) ***"))
            sb.appendLine(centerText("* VENTA PENDIENTE DE SINCRONIZAR *"))
            sb.appendLine(thinSep)
        } else if (isReprint) {
            sb.appendLine(centerText("*** COPIA DE REIMPRESIÓN ***"))
            sb.appendLine(thinSep)
        }

        // Sale metadata
        val folioStr = if (sale.id.startsWith("offline-")) "#" + sale.id.removePrefix("offline-") else "#${sale.folio}"
        sb.appendLine("Folio: $folioStr")
        sb.appendLine("Fecha: ${formatTimestamp(sale.createdAt)}")
        sb.appendLine("Cajero: ${sale.cashierName}")
        sb.appendLine(thinSep)

        // Column headers
        sb.appendLine(formatRow("CANT. DESCRIPCIÓN", "IMPORTE"))
        sb.appendLine(thinSep)

        // Items
        for (item in sale.lines) {
            val qtyStr = "${Quantity(item.quantityMillis).format()} ${item.description}"
            val totalStr = formatAmount(item.totalCents)
            sb.appendLine(formatRow(qtyStr, totalStr))
            if (item.quantityMillis != 1000L) {
                sb.appendLine("  @ ${formatAmount(item.unitPriceCents)} c/u")
            }
            if (item.discountCents > 0L) {
                val discStr = if (item.discountPercent > 0.0) {
                    "  (Desc. ${item.discountPercent.toInt()}%: -${formatAmount(item.discountCents)})"
                } else {
                    "  (Descuento: -${formatAmount(item.discountCents)})"
                }
                sb.appendLine(discStr)
            }
        }
        sb.appendLine(sep)

        // Totals
        sb.appendLine(formatRow("SUBTOTAL:", formatAmount(sale.subtotalCents)))
        if (sale.discountCents > 0L) {
            sb.appendLine(formatRow("DESCUENTO:", "-${formatAmount(sale.discountCents)}"))
        }
        sb.appendLine(formatRow("IVA:", formatAmount(sale.taxCents)))
        sb.appendLine(formatRow("TOTAL:", formatAmount(sale.totalCents)))
        sb.appendLine(thinSep)

        // Payment Details
        if (sale.payments.size > 1) {
            sb.appendLine(centerText("-- FORMAS DE PAGO --"))
        }
        for (pay in sale.payments) {
            val refStr = if (pay.reference.isNotBlank()) " (Ref: ${pay.reference})" else ""
            when (pay.method) {
                "CASH" -> {
                    sb.appendLine(formatRow("EFECTIVO:", formatAmount(pay.amountCents)))
                    if (pay.receivedCents > 0L) {
                        sb.appendLine(formatRow("RECIBIDO:", formatAmount(pay.receivedCents)))
                        sb.appendLine(formatRow("CAMBIO:", formatAmount(pay.changeCents)))
                    }
                }
                "CARD" -> {
                    sb.appendLine(formatRow("TARJETA$refStr:", formatAmount(pay.amountCents)))
                }
                "TRANSFER" -> {
                    sb.appendLine(formatRow("TRANSFERENCIA$refStr:", formatAmount(pay.amountCents)))
                }
                "LOYALTY" -> {
                    sb.appendLine(formatRow("MONEDERO ELECTRÓNICO:", formatAmount(pay.amountCents)))
                }
                "CREDIT" -> {
                    sb.appendLine(centerText("** VENTA A CRÉDITO / FIADO **"))
                    sale.customerName?.let { sb.appendLine("Cliente: $it") }
                    sb.appendLine(formatRow("Cargo a Cuenta:", formatAmount(pay.amountCents)))
                    sale.customerRemainingCreditCents?.let {
                        sb.appendLine(formatRow("Crédito Disponible:", formatAmount(it)))
                    }
                    sb.appendLine()
                    sb.appendLine(centerText("Firma del Cliente:"))
                    sb.appendLine()
                    sb.appendLine(centerText("_______________________________"))
                }
                else -> {
                    sb.appendLine(formatRow("PAGO (${pay.method})$refStr:", formatAmount(pay.amountCents)))
                }
            }
        }

        if (sale.loyaltyReward != null) {
            val reward = sale.loyaltyReward!!
            sb.appendLine(thinSep)
            sb.appendLine(centerText("** MONEDERO ELECTRÓNICO **"))
            if (reward.redeemedCents > 0L) {
                sb.appendLine(formatRow("Saldo Redimido:", formatAmount(reward.redeemedCents)))
            }
            if (reward.earnedCents > 0L) {
                sb.appendLine(formatRow("Puntos Ganados (${reward.cashbackPercentApplied}%):", "+" + formatAmount(reward.earnedCents)))
            }
            sb.appendLine(formatRow("Nuevo Saldo Disponible:", formatAmount(reward.finalBalanceCents)))
        }

        sb.appendLine(sep)
        (settings?.ticketFooter ?: "Conserve su ticket para aclaraciones.").lineSequence()
            .filter { it.isNotBlank() }.forEach { sb.appendLine(centerText(it)) }
        sb.appendLine()
        sb.appendLine()

        return sb.toString()
    }

    /**
     * Formats official partial refund receipt (Nota de Devolución).
     */
    fun formatSaleRefundText(
        refund: SaleRefundResponse,
        settings: SettingsDto? = null
    ): String {
        val sb = StringBuilder()
        val sep = "=".repeat(columnWidth)
        val thinSep = "-".repeat(columnWidth)

        // Store Header
        sb.appendLine(centerText(settings?.storeName ?: "NEXUS POS - ABARROTES"))
        settings?.storeAddress?.takeIf { it.isNotBlank() }?.let { sb.appendLine(centerText(it)) }
        settings?.storePhone?.takeIf { it.isNotBlank() }?.let { sb.appendLine(centerText("Tel: $it")) }
        sb.appendLine(sep)

        sb.appendLine(centerText("*** NOTA DE DEVOLUCIÓN ***"))
        sb.appendLine(thinSep)

        sb.appendLine("Nota Devolución: #${refund.folio}")
        sb.appendLine("Ticket Original: #${refund.saleFolio}")
        sb.appendLine("Fecha: ${formatTimestamp(refund.createdAt)}")
        sb.appendLine("Cajero: ${refund.cashierName}")
        refund.authorizedByName?.takeIf { it.isNotBlank() }?.let {
            sb.appendLine("Autorizó (Sup): $it")
        }
        sb.appendLine("Motivo: ${refund.reason}")
        if (refund.notes.isNotBlank()) {
            sb.appendLine("Obs: ${refund.notes}")
        }
        sb.appendLine(thinSep)

        sb.appendLine(formatRow("CANT. ARTÍCULO DEVUELTO", "REEMBOLSO"))
        sb.appendLine(thinSep)

        for (item in refund.lines) {
            val qtyStr = "${Quantity(item.quantityMillis).format()} ${item.description}"
            val totalStr = Money(item.totalCents).format()
            sb.appendLine(formatRow(qtyStr, totalStr))
            if (item.quantityMillis != 1000L) {
                sb.appendLine("  @ ${Money(item.unitPriceCents).format()} c/u")
            }
            if (item.reenterStock) {
                sb.appendLine("  [Reingresó a Inventario]")
            } else {
                sb.appendLine("  [Baja/Merma - No reingresó]")
            }
        }
        sb.appendLine(sep)

        val methodLabel = when (refund.refundMethod) {
            "CASH" -> "EFECTIVO (Gaveta)"
            "CREDIT" -> "SALDO A FAVOR (Crédito)"
            "LOYALTY" -> "MONEDERO ELECTRÓNICO"
            else -> refund.refundMethod
        }
        sb.appendLine(formatRow("MÉTODO REEMBOLSO:", methodLabel))
        sb.appendLine(formatRow("TOTAL REEMBOLSADO:", Money(refund.totalCents).format()))
        sb.appendLine(thinSep)

        sb.appendLine()
        sb.appendLine(centerText("_______________________________"))
        sb.appendLine(centerText("Firma del Cliente"))
        sb.appendLine()
        sb.appendLine(centerText("_______________________________"))
        sb.appendLine(centerText("Firma Cajero / Supervisor"))
        sb.appendLine()
        sb.appendLine()

        return sb.toString()
    }

    fun generateSaleRefundBytes(
        refund: SaleRefundResponse,
        settings: SettingsDto? = null,
        cutPaper: Boolean = true
    ): ByteArray {
        val text = formatSaleRefundText(refund, settings)
        val openDrawer = (refund.refundMethod == "CASH")
        return generateEscPosBytes(text, cutPaper = cutPaper, openDrawer = openDrawer)
    }

    /**
     * Formats suggested purchase and reorder list (Lista de Resurtido).
     */
    fun formatReorderListText(
        items: List<ReorderSuggestionItemDto>,
        settings: SettingsDto? = null,
        departmentName: String? = null
    ): String {
        val sb = StringBuilder()
        val sep = "=".repeat(columnWidth)
        val thinSep = "-".repeat(columnWidth)

        sb.appendLine(centerText(settings?.storeName ?: "NEXUS POS"))
        settings?.storeAddress?.takeIf { it.isNotBlank() }?.let { sb.appendLine(centerText(it)) }
        sb.appendLine(sep)
        sb.appendLine(centerText("*** LISTA DE RESURTIDO Y COMPRA ***"))
        departmentName?.takeIf { it.isNotBlank() }?.let { sb.appendLine(centerText("DEPTO: $it")) }
        sb.appendLine(thinSep)

        val dateStr = formatTimestamp(System.currentTimeMillis())
        sb.appendLine("Fecha Emisión: $dateStr")
        sb.appendLine("Total Partidas: ${items.size}")
        val criticalCount = items.count { it.urgency == ReorderUrgency.CRITICAL_OUT_OF_STOCK }
        if (criticalCount > 0) {
            sb.appendLine("Agotados Críticos: $criticalCount")
        }
        sb.appendLine(thinSep)
        sb.appendLine(formatRow("CANT. A PEDIR / ARTÍCULO", "EST. COSTO"))
        sb.appendLine(thinSep)

        items.forEach { item ->
            val qtyStr = if (item.suggestedOrderMillis % 1000L == 0L) {
                "${item.suggestedOrderMillis / 1000L} ${item.baseUnit}"
            } else {
                "${item.suggestedOrderMillis / 1000.0} ${item.baseUnit}"
            }
            val costStr = Money(item.estimatedInvestmentCents).format()
            val criticalTag = if (item.urgency == ReorderUrgency.CRITICAL_OUT_OF_STOCK) " [AGOTADO]" else ""
            sb.appendLine(formatRow(qtyStr, costStr))
            sb.appendLine("  ${item.productName}$criticalTag")
            val stockStr = "  Stock: ${Quantity(item.currentStockMillis).format()} | Mín: ${Quantity(item.minStockMillis).format()}"
            sb.appendLine(stockStr)
            if (item.barcode.isNotBlank() && item.barcode != "S/C") {
                sb.appendLine("  Cod: ${item.barcode}")
            }
        }

        sb.appendLine(sep)
        val totalCost = items.sumOf { it.estimatedInvestmentCents }
        sb.appendLine(formatRow("INVERSIÓN TOTAL EST.:", Money(totalCost).format()))
        sb.appendLine(thinSep)
        sb.appendLine()
        sb.appendLine(centerText("_______________________________"))
        sb.appendLine(centerText("Firma Encargado de Compras"))
        sb.appendLine()
        sb.appendLine()

        return sb.toString()
    }

    fun generateReorderListBytes(
        items: List<ReorderSuggestionItemDto>,
        settings: SettingsDto? = null,
        departmentName: String? = null,
        cutPaper: Boolean = true
    ): ByteArray {
        val text = formatReorderListText(items, settings, departmentName)
        return generateEscPosBytes(text, cutPaper = cutPaper, openDrawer = false)
    }

    /**
     * Formats executive report of lot expirations and perishables.
     */
    fun formatExpirationsReportText(
        report: ExpirationsReportDto,
        settings: SettingsDto? = null
    ): String {
        val sb = StringBuilder()
        val sep = "=".repeat(columnWidth)
        val thinSep = "-".repeat(columnWidth)

        sb.appendLine(centerText(settings?.storeName ?: "NEXUS POS"))
        settings?.storeAddress?.takeIf { it.isNotBlank() }?.let { sb.appendLine(centerText(it)) }
        sb.appendLine(sep)
        sb.appendLine(centerText("*** REPORTE DE CADUCIDADES ***"))
        sb.appendLine(centerText("CONTROL DE LOTES Y PERECEDEROS"))
        sb.appendLine(thinSep)

        val dateStr = formatTimestamp(report.generatedAt)
        sb.appendLine("Fecha Emisión: $dateStr")
        sb.appendLine("Total Lotes Activos: ${report.totalLotsCount}")
        sb.appendLine("Lotes Caducados: ${report.expiredLotsCount}")
        sb.appendLine("Lotes Próximos a Vencer: ${report.nearExpirationLotsCount}")
        sb.appendLine(formatRow("MERMA CONFIRMADA:", Money(report.totalExpiredValuationCents).format()))
        sb.appendLine(formatRow("CAPITAL EN RIESGO:", Money(report.totalAtRiskValuationCents).format()))
        sb.appendLine(thinSep)
        sb.appendLine(formatRow("LOTE / ARTÍCULO", "VENCE / ESTADO"))
        sb.appendLine(thinSep)

        report.lots.forEach { lot ->
            val statusTag = when (lot.status) {
                LotExpirationStatus.EXPIRED -> "[CADUCADO]"
                LotExpirationStatus.NEAR_EXPIRATION -> "[PRÓXIMO ${lot.daysUntilExpiration}d]"
                LotExpirationStatus.OPTIMAL -> "[OK ${lot.daysUntilExpiration}d]"
            }
            sb.appendLine(formatRow("${lot.lotNumber} (${lot.quantityMillis / 1000.0} ${lot.baseUnit})", statusTag))
            sb.appendLine("  ${lot.productName}")
            sb.appendLine("  Caduca: ${lot.expirationDate} | Val: ${Money(lot.valuationCents).format()}")
            if (lot.barcode.isNotBlank() && lot.barcode != "S/C") {
                sb.appendLine("  Cod: ${lot.barcode}")
            }
            sb.appendLine()
        }

        sb.appendLine(sep)
        sb.appendLine(centerText("Fin del Reporte"))
        sb.appendLine()
        sb.appendLine(centerText("_______________________________"))
        sb.appendLine(centerText("Firma Auditor / Encargado"))
        sb.appendLine()
        sb.appendLine()

        return sb.toString()
    }

    fun generateExpirationsReportBytes(
        report: ExpirationsReportDto,
        settings: SettingsDto? = null,
        cutPaper: Boolean = true
    ): ByteArray {
        val text = formatExpirationsReportText(report, settings)
        return generateEscPosBytes(text, cutPaper = cutPaper, openDrawer = false)
    }

    /**
     * Formats cash cut summary text (Corte X = Arqueo Informativo, Corte Z = Cierre Oficial).
     */
    fun formatCashCutText(
        session: CashSessionSummary,
        settings: SettingsDto? = null,
        isFinalZCut: Boolean = false
    ): String {
        val sb = StringBuilder()
        val sep = "=".repeat(columnWidth)
        val thinSep = "-".repeat(columnWidth)

        sb.appendLine(centerText(settings?.storeName ?: "NEXUS POS"))
        if (isFinalZCut) {
            sb.appendLine(centerText("*** CORTE Z (CIERRE OFICIAL) ***"))
        } else {
            sb.appendLine(centerText("*** CORTE X (ARQUEO PARCIAL) ***"))
        }
        sb.appendLine(sep)
        sb.appendLine("Sesión ID: #${session.sessionId}")
        sb.appendLine("Caja: ${session.registerName.ifBlank { "Caja ${session.registerId}" }}")
        sb.appendLine("Cajero: ${session.cashierName}")
        sb.appendLine("Estado: ${if (isFinalZCut) "TURNO CERRADO" else "EN CURSO / ABIERTO"}")
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
            val diffCents = session.differenceCents ?: (counted - session.expectedCashCents)
            val diffStr = when {
                diffCents == 0L -> "Exacto ($0.00)"
                diffCents > 0L -> "+${Money(diffCents).format()} (Sobrante)"
                else -> "-${Money(-diffCents).format()} (Faltante)"
            }
            sb.appendLine(formatRow("DIFERENCIA (Arqueo):", diffStr))
        }

        if (session.denominations.isNotEmpty()) {
            sb.appendLine(thinSep)
            sb.appendLine(centerText("-- DESGLOSE DE EFECTIVO --"))
            val bills = session.denominations.filter { !it.isCoin && it.pieces > 0 }
            if (bills.isNotEmpty()) {
                sb.appendLine("BILLETES:")
                for (b in bills) {
                    val label = "  ${Money(b.denominationCents).format()} x ${b.pieces}"
                    sb.appendLine(formatRow(label, Money(b.totalCents).format()))
                }
            }
            val coins = session.denominations.filter { it.isCoin && it.pieces > 0 }
            if (coins.isNotEmpty()) {
                sb.appendLine("MONEDAS:")
                for (c in coins) {
                    val label = "  ${Money(c.denominationCents).format()} x ${c.pieces}"
                    sb.appendLine(formatRow(label, Money(c.totalCents).format()))
                }
            }
        }

        if (isFinalZCut) {
            sb.appendLine(sep)
            sb.appendLine()
            sb.appendLine(centerText("Firma del Cajero: __________________"))
            sb.appendLine()
            sb.appendLine(centerText("Firma Supervisor: __________________"))
            sb.appendLine()
        } else {
            sb.appendLine(thinSep)
            sb.appendLine(centerText("Nota: Arqueo parcial, no sustituye al Corte Z"))
            sb.appendLine()
        }

        return sb.toString()
    }

    /**
     * Formats consolidated daily financial report into plain text for thermal printing.
     */
    fun formatDailyReportText(
        report: DailyFinancialReportDto,
        settings: SettingsDto? = null
    ): String {
        val sb = StringBuilder()
        val sep = "=".repeat(columnWidth)
        val thinSep = "-".repeat(columnWidth)

        sb.appendLine(centerText(settings?.storeName ?: "NEXUS POS"))
        sb.appendLine(centerText("*** REPORTE DIARIO CONSOLIDADO ***"))
        sb.appendLine(sep)
        sb.appendLine("Fecha: ${formatTimestamp(report.dateEpochMs).take(10)}")
        sb.appendLine("Turnos incluidos: ${report.activeSessionsCount}")
        sb.appendLine(thinSep)

        // Ventas por método
        sb.appendLine(centerText("-- DESGLOSE DE VENTAS --"))
        sb.appendLine(formatRow("Ventas en Efectivo:", Money(report.salesCashCents).format()))
        sb.appendLine(formatRow("Ventas a Crédito (Fiado):", Money(report.salesCreditCents).format()))
        if (report.salesOtherCents > 0L) {
            sb.appendLine(formatRow("Otras Formas de Pago:", Money(report.salesOtherCents).format()))
        }
        sb.appendLine(thinSep)
        sb.appendLine(formatRow("TOTAL VENTAS BRUTAS:", Money(report.totalSalesCents).format()))
        sb.appendLine(thinSep)

        // Movimientos y Cobranza
        sb.appendLine(centerText("-- CAJA Y MOVIMIENTOS --"))
        sb.appendLine(formatRow("(+) Cobranza Fiado:", Money(report.creditPaymentsCashCents).format()))
        sb.appendLine(formatRow("(+) Entradas de Caja:", Money(report.cashInCents).format()))
        sb.appendLine(formatRow("(-) Retiros de Caja:", Money(report.cashOutCents).format()))
        sb.appendLine(thinSep)

        // Tickets
        sb.appendLine(centerText("-- TICKETS EMITIDOS --"))
        sb.appendLine(formatRow("Tickets Completados:", "${report.completedTicketsCount}"))
        sb.appendLine(formatRow("Tickets Cancelados:", "${report.cancelledTicketsCount} (${Money(report.cancelledAmountCents).format()})"))
        sb.appendLine(formatRow("Total Operaciones:", "${report.totalTicketsCount}"))
        sb.appendLine(thinSep)

        // Rentabilidad
        sb.appendLine(centerText("-- GANANCIA BRUTA ESTIMADA --"))
        sb.appendLine(formatRow("Costo de ventas:", Money(report.totalCostCents).format()))
        if (report.estimatedCostLines > 0) {
            sb.appendLine(formatRow("Costos estimados:", report.estimatedCostLines.toString()))
        }
        sb.appendLine(formatRow("GANANCIA BRUTA:", Money(report.estimatedGrossProfitCents).format()))
        sb.appendLine(formatRow("Margen de Utilidad:", "${report.profitMarginPercent}%"))
        sb.appendLine(sep)
        sb.appendLine()
        sb.appendLine()

        return sb.toString()
    }

    /**
     * Generates ESC/POS bytes for daily financial report.
     */
    fun generateDailyReportBytes(
        report: DailyFinancialReportDto,
        settings: SettingsDto? = null,
        cutPaper: Boolean = true
    ): ByteArray {
        val text = formatDailyReportText(report, settings)
        return generateEscPosBytes(text, cutPaper = cutPaper, openDrawer = false)
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

    /**
     * Formats plain text layout for a thermal shelf label.
     */
    fun formatShelfLabelText(
        productName: String,
        barcode: String,
        priceCents: Long,
        presentationName: String = "Pieza",
        departmentName: String? = null,
        storeName: String? = null
    ): String {
        val sb = StringBuilder()
        val sep = "=".repeat(columnWidth)
        val thinSep = "-".repeat(columnWidth)

        sb.appendLine(centerText(storeName ?: "NEXUS POS"))
        sb.appendLine(sep)
        sb.appendLine(centerText(productName.uppercase()))
        val presInfo = "Presentación: $presentationName" + if (!departmentName.isNullOrBlank()) " | $departmentName" else ""
        sb.appendLine(centerText(presInfo))
        sb.appendLine(thinSep)
        sb.appendLine()
        val priceFormatted = Money(priceCents).format()
        sb.appendLine(centerText(">>> $priceFormatted <<<"))
        sb.appendLine()
        sb.appendLine(thinSep)
        sb.appendLine(centerText("|| ||||| ||||||| ||||| ||||"))
        sb.appendLine(centerText(barcode.ifBlank { "SIN-CODIGO" }))
        sb.appendLine(sep)
        sb.appendLine(centerText("PRECIO VIGENTE AL: ${formatTimestamp(System.currentTimeMillis()).take(10)}"))
        sb.appendLine()
        sb.appendLine()
        return sb.toString()
    }

    /**
     * Generates native ESC/POS command bytes for printing a shelf label with Code 128 barcode.
     */
    fun generateShelfLabelBytes(
        productName: String,
        barcode: String,
        priceCents: Long,
        presentationName: String = "Pieza",
        storeName: String? = null,
        cutPaper: Boolean = true
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(INIT)

        out.write(ALIGN_CENTER)
        out.write((storeName ?: "NEXUS POS").toByteArray(charset))
        out.write(0x0A)
        out.write("-".repeat(columnWidth.coerceAtMost(32)).toByteArray(charset))
        out.write(0x0A)

        out.write(BOLD_ON)
        out.write(productName.take(columnWidth).toByteArray(charset))
        out.write(BOLD_OFF)
        out.write(0x0A)

        out.write("($presentationName)".toByteArray(charset))
        out.write(0x0A)
        out.write(0x0A)

        out.write(DOUBLE_SIZE_ON)
        out.write(BOLD_ON)
        out.write(Money(priceCents).format().toByteArray(charset))
        out.write(BOLD_OFF)
        out.write(DOUBLE_SIZE_OFF)
        out.write(0x0A)
        out.write(0x0A)

        val cleanBarcode = barcode.trim()
        if (cleanBarcode.isNotBlank()) {
            out.write(BARCODE_HEIGHT_60)
            out.write(BARCODE_WIDTH_2)
            out.write(BARCODE_HRI_BELOW)

            val codeBytes = cleanBarcode.toByteArray(charset)
            val code128Payload = ByteArrayOutputStream()
            code128Payload.write('{'.code)
            code128Payload.write('B'.code)
            code128Payload.write(codeBytes)
            val payload = code128Payload.toByteArray()

            out.write(GS.toInt())
            out.write('k'.code)
            out.write(73)
            out.write(payload.size)
            out.write(payload)
            out.write(0x0A)
        }

        out.write(0x0A)
        out.write("Vigente: ${formatTimestamp(System.currentTimeMillis()).take(10)}".toByteArray(charset))
        out.write(0x0A)
        out.write(0x0A)
        out.write(0x0A)

        if (cutPaper) {
            out.write(CUT_PAPER)
        }

        return out.toByteArray()
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
