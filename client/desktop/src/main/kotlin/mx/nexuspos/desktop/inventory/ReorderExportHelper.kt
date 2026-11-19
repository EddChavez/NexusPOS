package mx.nexuspos.desktop.inventory

import mx.nexuspos.contracts.inventory.ReorderSuggestionItemDto
import mx.nexuspos.contracts.inventory.ReorderUrgency
import mx.nexuspos.core.Money
import mx.nexuspos.core.Quantity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReorderExportHelper {

    fun generateWhatsAppText(
        items: List<ReorderSuggestionItemDto>,
        storeName: String = "NEXUS POS",
        departmentName: String? = null
    ): String {
        val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        val sb = StringBuilder()
        sb.appendLine("🛒 *PEDIDO DE COMPRA - ${storeName.uppercase()}*")
        sb.appendLine("📅 $dateStr ${if (!departmentName.isNullOrBlank()) "• Depto: $departmentName" else ""}")
        sb.appendLine("----------------------------------------")

        items.forEach { item ->
            val qtyStr = if (item.suggestedOrderMillis % 1000L == 0L) {
                "${item.suggestedOrderMillis / 1000L}"
            } else {
                "${item.suggestedOrderMillis / 1000.0}"
            }
            val criticalTag = if (item.urgency == ReorderUrgency.CRITICAL_OUT_OF_STOCK) " 🚨(Agotado)" else ""
            sb.appendLine("• *$qtyStr ${item.baseUnit}* - ${item.productName}$criticalTag")
            if (item.barcode.isNotBlank() && item.barcode != "S/C") {
                sb.appendLine("   Cod: ${item.barcode}")
            }
        }

        sb.appendLine("----------------------------------------")
        sb.appendLine("Total de partidas: ${items.size}")
        val totalInvestmentCents = items.sumOf { it.estimatedInvestmentCents }
        sb.appendLine("Inversión estimada: ${Money.fromCents(totalInvestmentCents).format()}")

        return sb.toString()
    }

    fun generateCsvContent(items: List<ReorderSuggestionItemDto>): String {
        val sb = StringBuilder()
        sb.appendLine("SKU,CodigoBarras,Producto,Departamento,Existencia,Minimo,Maximo,CantidadAPedir,Unidad,CostoUnitario,SubtotalEstimado,Urgencia")

        items.forEach { item ->
            val sku = item.sku.replace(",", ";")
            val barcode = item.barcode.replace(",", ";")
            val name = "\"${item.productName.replace("\"", "\"\"")}\""
            val depto = "\"${(item.departmentName ?: "General").replace("\"", "\"\"")}\""
            val stock = item.currentStockMillis / 1000.0
            val min = item.minStockMillis / 1000.0
            val max = item.maxStockMillis / 1000.0
            val order = item.suggestedOrderMillis / 1000.0
            val unit = item.baseUnit
            val cost = item.costCents / 100.0
            val subtotal = item.estimatedInvestmentCents / 100.0
            val urgency = if (item.urgency == ReorderUrgency.CRITICAL_OUT_OF_STOCK) "AGOTADO_CRITICO" else "BAJO_MINIMO"

            sb.appendLine("$sku,$barcode,$name,$depto,$stock,$min,$max,$order,$unit,$cost,$subtotal,$urgency")
        }

        return sb.toString()
    }
}
