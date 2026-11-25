package mx.nexuspos.client.features.products.v2

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import mx.nexuspos.client.features.sales.v2.components.parseSalesQuantity
import mx.nexuspos.client.features.sales.v2.dialogs.ShiftActionPane
import mx.nexuspos.client.features.sales.v2.dialogs.checkoutAmountText
import mx.nexuspos.client.features.sales.v2.dialogs.parseCheckoutCents
import mx.nexuspos.contracts.catalog.DepartmentDto
import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.contracts.system.CommercialPreferences
import mx.nexuspos.core.ProductUnit
import mx.nexuspos.core.Quantity
import kotlin.math.roundToLong

internal data class ProductDraft(
    val sku: String,
    val name: String,
    val barcode: String,
    val departmentId: String?,
    val baseUnit: String,
    val costCents: Long,
    val priceCents: Long,
    val taxRate: Double,
    val usesInventory: Boolean,
    val initialStockMillis: Long,
    val minStockMillis: Long,
    val maxStockMillis: Long,
    val isPerishable: Boolean,
    val shelfLifeAlertDays: Int
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProductFormPaneV2(
    product: ProductDto?,
    loadPreferences: suspend () -> Result<CommercialPreferences>,
    departments: List<DepartmentDto>,
    busy: Boolean,
    serverError: String?,
    onManageDepartments: () -> Unit,
    onBack: () -> Unit,
    onSave: (ProductDraft) -> Unit
) {
    val primary = product?.presentations?.firstOrNull()
    var name by remember { mutableStateOf(product?.name.orEmpty()) }
    var sku by remember { mutableStateOf(product?.sku.orEmpty()) }
    var barcode by remember { mutableStateOf(primary?.barcode.orEmpty()) }
    var dept by remember { mutableStateOf(product?.departmentId) }
    var unit by remember { mutableStateOf(ProductUnit.fromCode(product?.baseUnit ?: "PZA")) }
    var price by remember { mutableStateOf(primary?.priceCents?.let(::checkoutAmountText).orEmpty()) }
    var cost by remember { mutableStateOf(product?.costCents?.let(::checkoutAmountText).orEmpty()) }
    var tax by remember { mutableStateOf(product?.taxRate ?: 0.0) }
    var taxChanged by remember { mutableStateOf(false) }
    var preferences by remember { mutableStateOf(CommercialPreferences()) }
    LaunchedEffect(product?.id) {
        if (product == null) loadPreferences().onSuccess {
            preferences = it
            if (!taxChanged) tax = it.defaultVatPercent / 100.0
        }
    }
    var tracked by remember { mutableStateOf(product?.usesInventory ?: true) }
    var initial by remember { mutableStateOf("") }
    var minimum by remember { mutableStateOf(stockInput(product?.minStockMillis ?: 0)) }
    var maximum by remember { mutableStateOf(stockInput(product?.maxStockMillis ?: 0)) }
    var perishable by remember { mutableStateOf(product?.isPerishable ?: false) }
    var days by remember { mutableStateOf((product?.shelfLifeAlertDays ?: 7).toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    val isKit = !product?.kitComponents.isNullOrEmpty()

    fun save() {
        val priceCents = parseCheckoutCents(price)
        val costCents = if (cost.isBlank()) 0L else parseCheckoutCents(cost)
        val initialStock = if (tracked && product == null) parseStock(initial) else 0L
        val minStock = if (tracked) parseStock(minimum) else 0L
        val maxStock = if (tracked) parseStock(maximum) else 0L
        val alertDays = if (tracked && perishable) days.trim().toIntOrNull() else product?.shelfLifeAlertDays ?: 7
        error = when {
            name.isBlank() || barcode.isBlank() -> "Captura el nombre y el código de barras."
            priceCents == null || costCents == null -> "Ingresa precios válidos de hasta dos decimales."
            initialStock == null || minStock == null || maxStock == null -> "Ingresa existencias válidas de hasta tres decimales."
            maxStock > 0 && maxStock < minStock -> "La existencia máxima debe ser mayor o igual al mínimo."
            alertDays == null || alertDays <= 0 -> "Ingresa un número positivo de días para la alerta de caducidad."
            else -> null
        }
        if (error != null) return
        onSave(ProductDraft(
            sku.trim(), name.trim(), barcode.trim(), dept, unit.code,
            checkNotNull(costCents), checkNotNull(priceCents), tax, tracked,
            checkNotNull(initialStock), checkNotNull(minStock), checkNotNull(maxStock),
            tracked && perishable, checkNotNull(alertDays)
        ))
    }

    ShiftActionPane(error ?: serverError, busy, footer = {
        TextButton(onClick = onBack, enabled = !busy) { Text("Volver") }
        Button(onClick = ::save, enabled = !busy) { Text("Guardar producto") }
    }) {
        Column(Modifier.widthIn(max = 760.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Datos del producto", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                name, { name = it; error = null }, label = { Text("Nombre") }, singleLine = true,
                enabled = !busy, modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    sku, { sku = it }, label = { Text("SKU (opcional)") }, singleLine = true,
                    enabled = !busy, modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    barcode, { barcode = it; error = null }, label = { Text("Código de barras") }, singleLine = true,
                    enabled = !busy, modifier = Modifier.weight(1f)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProductDepartmentFilter(departments, dept, { dept = it }, Modifier.weight(1f), enabled = !busy)
                TextButton(onClick = onManageDepartments, enabled = !busy) { Text("Administrar") }
            }
            Text("Unidad base", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProductUnit.entries.forEach { option ->
                    FilterChip(
                        selected = unit == option, onClick = { unit = option },
                        enabled = !busy && !isKit, label = { Text(option.label) }
                    )
                }
            }
            HorizontalDivider()
            Text("Precio de la presentación principal", style = MaterialTheme.typography.titleMedium)
            if (primary != null && (product.presentations.size > 1 || primary.factorMillis != 1000L)) {
                Text(primary.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumericProductField(price, { price = it; error = null }, "Precio público", busy, Modifier.weight(1f), currency = true)
                NumericProductField(cost, { cost = it; error = null }, "Costo", busy, Modifier.weight(1f), currency = true)
            }
            if (product == null) {
                parseCheckoutCents(price)?.takeIf { it in 0..(Long.MAX_VALUE - 100) }?.let { amount ->
                    val suggested = preferences.catalogRounding.apply(amount)
                    if (suggested != amount) TextButton(
                        onClick = { price = checkoutAmountText(suggested) },
                        enabled = !busy
                    ) { Text("Usar precio redondeado · ${checkoutAmountText(suggested)}") }
                }
            }
            Text("IVA", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf(0.0, 0.08, 0.16) + listOfNotNull(product?.taxRate)).distinct().sorted().forEach { rate ->
                    FilterChip(
                        selected = tax == rate, onClick = { tax = rate; taxChanged = true }, enabled = !busy,
                        label = { Text(productTaxLabel(rate)) }
                    )
                }
            }
            HorizontalDivider()
            Text("Inventario y caducidad", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(checked = tracked, onCheckedChange = { tracked = it; error = null }, enabled = !busy && !isKit)
                Text(if (isKit) "Existencias por componente" else "Controlar existencias")
            }
            if (tracked) {
                if (product == null) {
                    NumericProductField(initial, { initial = it; error = null }, "Existencia inicial", busy, Modifier.fillMaxWidth())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumericProductField(minimum, { minimum = it; error = null }, "Mínimo", busy, Modifier.weight(1f))
                    NumericProductField(maximum, { maximum = it; error = null }, "Máximo", busy, Modifier.weight(1f))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Checkbox(checked = perishable, onCheckedChange = { perishable = it }, enabled = !busy)
                    Text("Producto perecedero")
                }
                if (perishable) {
                    OutlinedTextField(
                        days, { days = it; error = null }, label = { Text("Alertar días antes del vencimiento") },
                        enabled = !busy, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun NumericProductField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    busy: Boolean,
    modifier: Modifier,
    currency: Boolean = false
) {
    OutlinedTextField(
        value, onChange, label = { Text(label) },
        prefix = if (currency) ({ Text("$") }) else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true, enabled = !busy, modifier = modifier
    )
}

private fun stockInput(millis: Long) = Quantity(millis).format().substringBefore(' ')

private val zeroStock = Regex("0+([.,]0{0,3})?|[.,]0{1,3}")

private fun parseStock(input: String): Long? {
    val value = input.trim()
    return if (value.isEmpty() || zeroStock.matches(value)) 0L else parseSalesQuantity(value)?.millis
}

internal fun productTaxLabel(rate: Double): String =
    "${((rate * 10000.0).roundToLong() / 100.0).toString().removeSuffix(".0")} %"
