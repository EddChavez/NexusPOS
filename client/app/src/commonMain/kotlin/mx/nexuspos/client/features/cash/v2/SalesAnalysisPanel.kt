package mx.nexuspos.client.features.cash.v2

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import mx.nexuspos.client.core.ApiClient
import mx.nexuspos.client.core.CashCutHistoryAccessDenied
import mx.nexuspos.client.features.settings.v2.SettingsChoice
import mx.nexuspos.client.ui.components.FeedbackMessageHost
import mx.nexuspos.contracts.reports.*
import mx.nexuspos.core.Money
import mx.nexuspos.core.ProductUnit
import mx.nexuspos.core.Quantity

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SalesAnalysisPanel(
    api: ApiClient,
    zoneId: String,
    initialFrom: String,
    initialTo: String,
    onExport: (suspend (SalesAnalysisReport) -> Result<String?>)?
) {
    val scope = rememberCoroutineScope()
    var from by remember { mutableStateOf(initialFrom) }
    var to by remember { mutableStateOf(initialTo) }
    var group by remember { mutableStateOf(SalesAnalysisGroup.DAY) }
    var pin by remember { mutableStateOf("") }
    var report by remember { mutableStateOf<SalesAnalysisReport?>(null) }
    var loading by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }
    val start = parseReportDate(from)
    val end = parseReportDate(to)
    val dateError = when {
        start == null || end == null -> "Usa el formato AAAA-MM-DD."
        start > end -> "La fecha inicial debe ser igual o anterior a la final."
        else -> null
    }
    val ready = report
    val canEdit = !loading && !exporting
    val filtersApplied = ready != null && ready.fromDate == start?.toString() &&
        ready.toDate == end?.toString() && ready.groupBy == group

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Reporte de ventas y margen", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (ready != null) TextButton(onClick = {
                pin = ""
                report = null
                success = null
                error = null
            }, enabled = canEdit) {
                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Bloquear")
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                from, { from = it }, label = { Text("Desde") }, modifier = Modifier.weight(1f), enabled = canEdit, singleLine = true
            )
            OutlinedTextField(
                to, { to = it }, label = { Text("Hasta") }, modifier = Modifier.weight(1f), enabled = canEdit, singleLine = true
            )
            SettingsChoice(
                "Agrupar por", group.name, SalesAnalysisGroup.entries.map { it.name to it.displayLabel() },
                { group = SalesAnalysisGroup.valueOf(it) }, modifier = Modifier.weight(1f), enabled = canEdit
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically
        ) {
            if (ready == null) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { value -> if (value.length <= 6 && value.all { it in '0'..'9' }) pin = value },
                    label = { Text("PIN de encargado") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true, enabled = canEdit, modifier = Modifier.width(220.dp)
                )
            }
            Button(onClick = {
                if (loading || exporting) return@Button
                loading = true
                success = null
                error = null
                val request = SalesAnalysisRequest(pin, checkNotNull(start).toString(), checkNotNull(end).toString(), zoneId, group)
                scope.launch {
                    api.getSalesAnalysis(request).onSuccess { report = it }.onFailure {
                        if (it is CashCutHistoryAccessDenied) {
                            pin = ""
                            report = null
                        }
                        error = it.message ?: "No se pudo generar el reporte."
                    }
                    loading = false
                }
            }, enabled = canEdit && pin.length in setOf(4, 6) && dateError == null) { Text("Consultar") }

            if (ready != null && onExport != null) {
                OutlinedButton(onClick = {
                    if (loading || exporting || !filtersApplied) return@OutlinedButton
                    exporting = true
                    success = null
                    error = null
                    scope.launch {
                        onExport(ready).onSuccess { name ->
                            if (name != null) success = "CSV guardado · $name"
                        }.onFailure { error = it.message ?: "No se pudo guardar el CSV." }
                        exporting = false
                    }
                }, enabled = canEdit && filtersApplied) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Exportar CSV")
                }
            }
        }
        if (dateError != null) Text(dateError, color = MaterialTheme.colorScheme.error)
        FeedbackMessageHost(error, success, onDismissError = { error = null }, onDismissSuccess = { success = null })
        if (loading || exporting) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (ready != null) {
            Text(
                "${ready.fromDate} a ${ready.toDate} · ${ready.groupBy.displayLabel()} · Importes sin IVA",
                style = MaterialTheme.typography.bodySmall
            )
            if (!filtersApplied) {
                Text("Hay filtros sin aplicar.", style = MaterialTheme.typography.bodySmall)
            }
            when (ready.groupBy) {
                SalesAnalysisGroup.PARETO -> Text("Porcentaje acumulado sobre productos con venta neta positiva.", style = MaterialTheme.typography.bodySmall)
                SalesAnalysisGroup.ROTATION -> Text("Salidas netas por ventas ÷ existencia promedio al inicio y al cierre.", style = MaterialTheme.typography.bodySmall)
                else -> Unit
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AnalysisMetric("Ventas brutas", ready.totals.salesCents)
                AnalysisMetric("Ventas netas", ready.totals.netSalesCents)
                if (ready.costsVisible) {
                    AnalysisMetric("Costo", ready.totals.costCents)
                    AnalysisMetric("Utilidad bruta", ready.totals.profitCents)
                    ready.totals.marginBasisPoints?.let { MetricValue("Margen bruto", Money(it).format().replace("$", "") + "%") }
                }
            }
            val estimates = ready.totals.estimatedCostEvents
            if (estimates > 0) {
                Text(
                    if (estimates == 1L) "1 partida usa un costo estimado." else "$estimates partidas usan un costo estimado.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
                )
            }
            if (ready.rows.isEmpty()) Text("No hay ventas, devoluciones ni cancelaciones para este periodo.")
            key(ready) {
                LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(ready.rows, key = { it.key }) { AnalysisRow(it, ready.costsVisible) }
                }
            }
        }
    }
}

@Composable
private fun AnalysisMetric(label: String, cents: Long) = MetricValue(label, Money(cents).format())

@Composable
private fun MetricValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnalysisRow(row: SalesAnalysisRow, costsVisible: Boolean) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(row.label, style = MaterialTheme.typography.titleMedium)
            val quantity = row.netQuantityMillis?.let { Quantity(it).format(ProductUnit.fromCode(row.unit.orEmpty())) }
            if (row.sku != null || quantity != null) {
                Text(listOfNotNull(row.sku, quantity).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AnalysisMetric("Ventas netas", row.totals.netSalesCents)
                row.cumulativeShareBasisPoints?.let {
                    MetricValue("Participación acumulada", Money(it).format().removePrefix("$") + "%")
                }
                row.averageStockMillis?.let {
                    MetricValue("Existencia promedio", Quantity(it).format(ProductUnit.fromCode(row.unit.orEmpty())))
                    row.stockConsumedMillis?.let { consumed ->
                        MetricValue("Salidas netas", Quantity(consumed).format(ProductUnit.fromCode(row.unit.orEmpty())))
                    }
                    MetricValue("Rotación", row.turnoverBasisPoints?.let { rate -> Money(rate / 100).format().removePrefix("$") + "×" } ?: "Sin base")
                }
                if (costsVisible) {
                    AnalysisMetric("Costo", row.totals.costCents)
                    AnalysisMetric("Utilidad bruta", row.totals.profitCents)
                }
                if (row.totals.cancellationsCents != 0L) AnalysisMetric("Cancelaciones", row.totals.cancellationsCents)
                if (row.totals.refundsCents != 0L) AnalysisMetric("Devoluciones", row.totals.refundsCents)
            }
            if (row.totals.estimatedCostEvents > 0) Text("Incluye costo estimado", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun SalesAnalysisGroup.displayLabel(): String = when (this) {
    SalesAnalysisGroup.DAY -> "Día"
    SalesAnalysisGroup.PRODUCT -> "Producto"
    SalesAnalysisGroup.DEPARTMENT -> "Departamento"
    SalesAnalysisGroup.CUSTOMER -> "Cliente"
    SalesAnalysisGroup.HOUR -> "Hora"
    SalesAnalysisGroup.PARETO -> "Pareto por producto"
    SalesAnalysisGroup.ROTATION -> "Rotación de inventario"
}

private fun parseReportDate(value: String): LocalDate? =
    value.trim().takeIf { it.length == 10 }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
