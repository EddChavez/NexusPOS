package mx.nexuspos.desktop.reports

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mx.nexuspos.contracts.reports.SalesAnalysisGroup
import mx.nexuspos.contracts.reports.SalesAnalysisReport
import mx.nexuspos.contracts.reports.SalesAnalysisTotals
import java.awt.FileDialog
import java.awt.Frame
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path

internal suspend fun exportSalesAnalysisCsv(owner: Frame, report: SalesAnalysisReport): Result<String?> =
    try {
        val target = FileDialog(owner, "Guardar reporte de ventas", FileDialog.SAVE).let { dialog ->
            try {
                dialog.file = "ventas_${report.fromDate}_${report.toDate}.csv"
                dialog.isVisible = true
                dialog.file?.let { Path.of(dialog.directory, it) }
            } finally {
                dialog.dispose()
            }
        }
        val name = if (target == null) null else withContext(Dispatchers.IO) {
            // Keep the exact path chosen in the save dialog, including any overwrite confirmation.
            Files.writeString(target, salesAnalysisCsv(report))
            target.fileName.toString()
        }
        Result.success(name)
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }

internal fun salesAnalysisCsv(report: SalesAnalysisReport): String = buildString {
    append('\uFEFF')
    appendLine(
        "Desde,Hasta,Zona horaria,Agrupación,Nombre,SKU,Unidad,Cantidad neta," +
            "Ventas sin IVA,Cancelaciones sin IVA,Devoluciones sin IVA,Ventas netas sin IVA,Costo,Utilidad bruta,Margen bruto (%),Partidas con costo estimado"
    )
    val group = when (report.groupBy) {
        SalesAnalysisGroup.DAY -> "Día"
        SalesAnalysisGroup.PRODUCT -> "Producto"
        SalesAnalysisGroup.DEPARTMENT -> "Departamento"
    }
    fun appendRow(label: String, sku: String?, unit: String?, quantity: Long?, totals: SalesAnalysisTotals) {
        val fields = listOf(
            csvText(report.fromDate), csvText(report.toDate), csvText(report.timeZone), csvText(group), csvText(label),
            csvText(sku.orEmpty()), csvText(unit.orEmpty()), quantity?.let { BigDecimal.valueOf(it, 3).toPlainString() }.orEmpty(),
            decimal(totals.salesCents), decimal(totals.cancellationsCents), decimal(totals.refundsCents),
            decimal(totals.netSalesCents), decimal(totals.costCents), decimal(totals.profitCents), totals.marginBasisPoints?.let(::decimal).orEmpty(), totals.estimatedCostEvents.toString()
        )
        appendLine(fields.joinToString(","))
    }
    report.rows.forEach { appendRow(it.label, it.sku, it.unit, it.netQuantityMillis, it.totals) }
    appendRow("Total", null, null, null, report.totals)
}

private fun decimal(cents: Long): String = BigDecimal.valueOf(cents, 2).toPlainString()

private fun csvText(value: String): String {
    val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@') || value.firstOrNull() in listOf('\t', '\r', '\n')) "'$value" else value
    return "\"${safe.replace("\"", "\"\"")}\""
}
