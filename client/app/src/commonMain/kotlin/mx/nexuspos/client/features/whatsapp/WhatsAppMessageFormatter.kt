package mx.nexuspos.client.features.whatsapp

import mx.nexuspos.contracts.credit.CreditMovementDto
import mx.nexuspos.contracts.credit.CustomerDto
import mx.nexuspos.contracts.credit.CustomerStatementDto
import mx.nexuspos.contracts.sales.SaleRefundResponse
import mx.nexuspos.contracts.sales.SaleResponse
import mx.nexuspos.contracts.system.SettingsDto
import mx.nexuspos.core.Money
import mx.nexuspos.core.Quantity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object WhatsAppMessageFormatter {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    private fun formatTimestamp(timestamp: Long): String {
        return dateFormat.format(Date(timestamp))
    }

    /**
     * Cleans phone number removing spaces, dashes, parentheses and adds default country code if 10 digits.
     */
    fun cleanAndFormatPhoneNumber(phone: String, defaultCountryCode: String = "52"): String {
        val clean = phone.filter { it.isDigit() }
        if (clean.isBlank()) return ""

        val effectiveCode = defaultCountryCode.filter { it.isDigit() }.ifBlank { "52" }

        // Si ya tiene 12 dígitos y empieza con el código de país (ej. 525512345678)
        if (clean.length == 12 && clean.startsWith(effectiveCode)) {
            return clean
        }
        // Si tiene 13 dígitos y es móvil México con 1 (ej. 5215512345678)
        if (clean.length == 13 && clean.startsWith("521")) {
            return clean
        }
        // Si tiene 10 dígitos (estándar México local), añadir lada país
        if (clean.length == 10) {
            return "$effectiveCode$clean"
        }
        // Cualquier otro formato (internacional ya formateado)
        return clean
    }

    /**
     * Encodes URL component safely without third party dependencies.
     */
    fun urlEncode(value: String): String {
        val sb = StringBuilder()
        for (b in value.encodeToByteArray()) {
            val c = b.toInt().toChar()
            if ((c in 'a'..'z') || (c in 'A'..'Z') || (c in '0'..'9') || c == '-' || c == '_' || c == '.' || c == '~') {
                sb.append(c)
            } else {
                sb.append('%').append((b.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0'))
            }
        }
        return sb.toString()
    }

    /**
     * Builds official WhatsApp web/app link.
     */
    fun buildWhatsAppUrl(phone: String, message: String, defaultCountryCode: String = "52"): String {
        val cleanPhone = cleanAndFormatPhoneNumber(phone, defaultCountryCode)
        val encodedText = urlEncode(message)
        return if (cleanPhone.isNotBlank()) {
            "https://api.whatsapp.com/send?phone=$cleanPhone&text=$encodedText"
        } else {
            "https://api.whatsapp.com/send?text=$encodedText"
        }
    }

    /**
     * Formats digital sale receipt for WhatsApp.
     */
    fun formatSaleTicket(sale: SaleResponse, settings: SettingsDto? = null): String {
        val sb = StringBuilder()
        val storeName = settings?.storeName?.ifBlank { "NEXUS POS" } ?: "NEXUS POS"

        sb.appendLine("🧾 *$storeName*")
        if (!settings?.storeAddress.isNullOrBlank()) {
            sb.appendLine("📍 ${settings!!.storeAddress}")
        }
        if (!settings?.storePhone.isNullOrBlank()) {
            sb.appendLine("📞 Tel: ${settings!!.storePhone}")
        }
        sb.appendLine("Folio: *#${sale.folio}* • Fecha: ${formatTimestamp(sale.createdAt)}")
        sb.appendLine("Cajero: ${sale.cashierName}")
        if (!sale.customerName.isNullOrBlank()) {
            sb.appendLine("Cliente: *${sale.customerName}*")
        }
        sb.appendLine()

        // Monospace table for items
        sb.appendLine("```")
        sb.appendLine(padRow("Cant.  Producto", "Total", 32))
        sb.appendLine("-".repeat(32))

        for (line in sale.lines) {
            val qtyStr = Quantity(line.quantityMillis).format()
            val totalStr = Money(line.totalCents).format()
            val desc = line.description.take(18)
            val left = "${qtyStr.padEnd(5)} $desc"
            sb.appendLine(padRow(left, totalStr, 32))
            if (line.discountCents > 0L) {
                sb.appendLine(padRow("  (Desc: -${Money(line.discountCents).format()})", "", 32))
            }
        }

        sb.appendLine("-".repeat(32))
        sb.appendLine(padRow("Subtotal:", Money(sale.subtotalCents).format(), 32))
        val totalDiscount = sale.lines.sumOf { it.discountCents }
        if (totalDiscount > 0L) {
            sb.appendLine(padRow("Descuentos:", "-${Money(totalDiscount).format()}", 32))
        }
        if (sale.taxCents > 0L) {
            sb.appendLine(padRow("Impuestos:", Money(sale.taxCents).format(), 32))
        }
        sb.appendLine(padRow("TOTAL:", Money(sale.totalCents).format(), 32))
        sb.appendLine("```")

        // Payments summary
        sb.appendLine("*Forma(s) de Pago:*")
        for (p in sale.payments) {
            val refStr = if (p.reference.isNotBlank()) " (${p.reference})" else ""
            when (p.method) {
                "CASH" -> {
                    sb.append("💵 Efectivo: *${Money(p.amountCents).format()}*")
                    if (p.receivedCents > p.amountCents) {
                        sb.append(" (Recibido: ${Money(p.receivedCents).format()} • Cambio: ${Money(p.changeCents).format()})")
                    }
                    sb.appendLine()
                }
                "CARD" -> sb.appendLine("💳 Tarjeta$refStr: *${Money(p.amountCents).format()}*")
                "TRANSFER" -> sb.appendLine("🏦 Transferencia$refStr: *${Money(p.amountCents).format()}*")
                "LOYALTY" -> sb.appendLine("🪙 Monedero Electrónico: *${Money(p.amountCents).format()}*")
                "CREDIT" -> {
                    sb.append("📝 Crédito Fiado: *${Money(p.amountCents).format()}*")
                    sale.customerRemainingCreditCents?.let {
                        sb.append(" (Crédito disp: ${Money(it).format()})")
                    }
                    sb.appendLine()
                }
                else -> sb.appendLine("• ${p.method}$refStr: *${Money(p.amountCents).format()}*")
            }
        }

        // Loyalty reward block if earned/redeemed
        if (sale.loyaltyReward != null) {
            val reward = sale.loyaltyReward!!
            sb.appendLine()
            sb.appendLine("🪙 *Programa de Lealtad:*")
            if (reward.earnedCents > 0L) {
                sb.appendLine("• ¡Puntos ganados (${reward.cashbackPercentApplied}%): *+${Money(reward.earnedCents).format()}*!")
            }
            if (reward.redeemedCents > 0L) {
                sb.appendLine("• Saldo redimido: *-${Money(reward.redeemedCents).format()}*")
            }
            sb.appendLine("• Saldo actual disponible: *${Money(reward.finalBalanceCents).format()}*")
        }

        sb.appendLine()
        val customFooter = settings?.whatsappCustomFooter?.ifBlank { settings.ticketFooter }
            ?: "¡Gracias por su compra!"
        sb.appendLine(customFooter)

        return sb.toString()
    }

    /**
     * Formats customer statement & credit collection reminder for WhatsApp.
     */
    fun formatCustomerStatement(
        customer: CustomerDto,
        statement: CustomerStatementDto? = null,
        settings: SettingsDto? = null
    ): String {
        val sb = StringBuilder()
        val storeName = settings?.storeName?.ifBlank { "NEXUS POS" } ?: "NEXUS POS"

        sb.appendLine("Hola *${customer.name}*, le compartimos su estado de cuenta en *$storeName*:")
        sb.appendLine()
        val debt = Money(customer.currentBalanceCents).format()
        sb.appendLine("📌 *Saldo Pendiente (Deuda):* *$debt*")

        if (customer.creditEnabled) {
            val disp = if (!customer.hasCreditLimit) "Ilimitado" else Money(customer.availableCreditCents).format()
            val limit = if (!customer.hasCreditLimit) "Sin Límite" else Money(customer.creditLimitCents).format()
            sb.appendLine("🟢 *Crédito Disponible:* $disp (Límite: $limit)")
        }

        if (customer.loyaltyBalanceCents > 0L) {
            sb.appendLine("🪙 *Saldo en Monedero Electrónico:* *${Money(customer.loyaltyBalanceCents).format()}*")
        }

        // Recent movements
        val movs = statement?.movements?.take(5) ?: emptyList()
        if (movs.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("*Últimos Movimientos:*")
            for (m in movs) {
                val isCharge = m.type == "PURCHASE" || m.type == "CHARGE"
                val sign = if (isCharge) "(+)" else "(-)"
                val dateStr = formatTimestamp(m.createdAt).take(10)
                sb.appendLine("• $dateStr: ${m.description} *$sign${Money(m.amountCents).format()}*")
            }
        }

        // Bank details for payment
        if (!settings?.whatsappBankDetails.isNullOrBlank()) {
            sb.appendLine()
            sb.appendLine("💳 *Datos para Pago por Transferencia:*")
            sb.appendLine(settings!!.whatsappBankDetails)
        }

        sb.appendLine()
        sb.appendLine("Agradecemos su preferencia y puntualidad. ¡Que tenga un excelente día!")

        return sb.toString()
    }

    /**
     * Formats payment receipt when a customer makes a credit payment.
     */
    fun formatCreditPayment(
        customer: CustomerDto,
        payment: CreditMovementDto,
        settings: SettingsDto? = null
    ): String {
        val sb = StringBuilder()
        val storeName = settings?.storeName?.ifBlank { "NEXUS POS" } ?: "NEXUS POS"

        sb.appendLine("Hola *${customer.name}*, confirmamos la recepción de su abono en *$storeName*:")
        sb.appendLine()
        sb.appendLine("Recibo: *#${payment.receiptFolio ?: payment.id}*")
        sb.appendLine("Fecha: ${formatTimestamp(payment.createdAt)}")
        sb.appendLine("💰 *Importe Recibido:* *${Money(payment.amountCents).format()}*")
        sb.appendLine("📌 *Saldo Deudor Restante:* *${Money(payment.balanceAfterCents).format()}*")
        sb.appendLine()
        sb.appendLine("¡Muchas gracias por su pago!")

        return sb.toString()
    }

    /**
     * Formats sale refund receipt for WhatsApp.
     */
    fun formatRefund(refund: SaleRefundResponse, settings: SettingsDto? = null): String {
        val sb = StringBuilder()
        val storeName = settings?.storeName?.ifBlank { "NEXUS POS" } ?: "NEXUS POS"

        sb.appendLine("🔄 *NOTA DE DEVOLUCIÓN - $storeName*")
        sb.appendLine("Nota: *#${refund.folio}* • Ticket Original: *#${refund.saleFolio}*")
        sb.appendLine("Fecha: ${formatTimestamp(refund.createdAt)}")
        sb.appendLine("Motivo: ${refund.reason}")
        if (refund.notes.isNotBlank()) {
            sb.appendLine("Notas: ${refund.notes}")
        }
        sb.appendLine()

        sb.appendLine("```")
        sb.appendLine(padRow("Cant.  Artículo Devuelto", "Reembolso", 32))
        sb.appendLine("-".repeat(32))
        for (l in refund.lines) {
            val qtyStr = Quantity(l.quantityMillis).format()
            val totalStr = Money(l.totalCents).format()
            val desc = l.description.take(18)
            sb.appendLine(padRow("${qtyStr.padEnd(5)} $desc", totalStr, 32))
        }
        sb.appendLine("-".repeat(32))
        sb.appendLine(padRow("TOTAL DEVUELTO:", Money(refund.totalCents).format(), 32))
        sb.appendLine("```")

        val methodLabel = when (refund.refundMethod) {
            "CASH" -> "Efectivo"
            "CREDIT" -> "Abono a Cuenta Crédito"
            "LOYALTY" -> "Abono a Monedero Electrónico"
            else -> refund.refundMethod
        }
        sb.appendLine("Método de reembolso: *$methodLabel*")
        sb.appendLine()
        sb.appendLine("Conserve este comprobante digital para cualquier aclaración.")

        return sb.toString()
    }

    private fun padRow(left: String, right: String, totalWidth: Int): String {
        val available = totalWidth - right.length
        return if (available <= 0) {
            left.take(totalWidth - 1) + " "
        } else {
            left.take(available).padEnd(available) + right
        }
    }
}
