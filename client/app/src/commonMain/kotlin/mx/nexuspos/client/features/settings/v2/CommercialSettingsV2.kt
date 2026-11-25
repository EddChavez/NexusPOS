package mx.nexuspos.client.features.settings.v2

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import mx.nexuspos.client.core.ApiClient
import mx.nexuspos.client.features.settings.SettingsTab
import mx.nexuspos.client.features.setup.SetupToggle
import mx.nexuspos.client.features.sales.v2.dialogs.parseCheckoutCents
import mx.nexuspos.client.features.sales.v2.dialogs.checkoutAmountText
import mx.nexuspos.contracts.catalog.CatalogPriceList
import mx.nexuspos.contracts.catalog.UpdateCatalogPriceList
import mx.nexuspos.contracts.system.CatalogRounding
import mx.nexuspos.contracts.system.CommercialPreferences
import mx.nexuspos.contracts.system.SaleFolioStatus
import mx.nexuspos.core.Money

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CommercialSettingsV2(api: ApiClient, tab: SettingsTab, catalogLinks: @Composable () -> Unit, onSave: suspend () -> Unit) {
    val scope = rememberCoroutineScope()
    var draft by remember(tab) { mutableStateOf<CommercialPreferences?>(null) }
    var original by remember(tab) { mutableStateOf<CommercialPreferences?>(null) }
    var status by remember(tab) { mutableStateOf<String?>(null) }
    var error by remember(tab) { mutableStateOf<String?>(null) }
    var saving by remember(tab) { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var cashLimit by remember(tab) { mutableStateOf("") }
    var folio by remember(tab) { mutableStateOf<SaleFolioStatus?>(null) }
    LaunchedEffect(api, tab, reload) {
        if (tab == SettingsTab.SALES) api.saleFolioStatus().onSuccess { folio = it }
        error = null
        api.getSettings().onSuccess {
            original = it.commerce
            draft = it.commerce
            cashLimit = it.commerce.cashDrawerLimitCents?.let(::checkoutAmountText).orEmpty()
        }.onFailure { error = it.message }
    }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        val current = draft
        if (current == null) {
            if (error == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            else OutlinedButton(onClick = { reload++ }) { Text("Reintentar") }
            return@Column
        }
        when (tab) {
            SettingsTab.SALES -> {
                SetupToggle("Habilitar venta rápida", current.quickSaleEnabled) { draft = current.copy(quickSaleEnabled = it) }
                folio?.let {
                    Text("Último folio emitido · ${it.folio ?: "Sin ventas"}")
                    Text("Prefijo de folio · ${it.prefix ?: "Sin prefijo"}")
                }
            }
            SettingsTab.SHIFTS -> {
                SetupToggle("Solicitar fondo inicial", current.requireOpeningFund) { draft = current.copy(requireOpeningFund = it) }
                SetupToggle("Permitir relevo de operador", current.allowShiftHandoff) { draft = current.copy(allowShiftHandoff = it) }
                val cents = parseCheckoutCents(cashLimit)
                val invalid = cashLimit.isNotBlank() && (cents == null || cents <= 0)
                OutlinedTextField(cashLimit, { value ->
                    cashLimit = value.take(16)
                    draft = current.copy(cashDrawerLimitCents = parseCheckoutCents(cashLimit)?.takeIf { it > 0 })
                }, label = { Text("Límite de efectivo (opcional)") }, singleLine = true, isError = invalid)
            }
            SettingsTab.CATALOG -> {
                catalogLinks()
                Text("IVA predeterminado para productos nuevos")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 8, 16).forEach { rate ->
                        FilterChip(current.defaultVatPercent == rate, { draft = current.copy(defaultVatPercent = rate) }, { Text("$rate %") })
                    }
                }
                Text("Redondeo al proponer un precio")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CatalogRounding.entries.forEach { mode ->
                        FilterChip(current.catalogRounding == mode, { draft = current.copy(catalogRounding = mode) }, { Text(mode.label) })
                    }
                }
                Text("Ejemplo · ${Money(235).format()} → ${Money(current.catalogRounding.apply(235)).format()}")
                SetupToggle("Buscar códigos sin ceros a la izquierda", current.matchBarcodeWithoutLeadingZeros) {
                    draft = current.copy(matchBarcodeWithoutLeadingZeros = it)
                }
            }
            SettingsTab.CREDIT -> {
                SetupToggle("Habilitar nuevas ventas a crédito", current.creditSalesEnabled) { draft = current.copy(creditSalesEnabled = it) }
                Text("Formas de pago para abonos")
                listOf("CASH" to "Efectivo", "TRANSFER" to "Transferencia", "CARD" to "Tarjeta").forEach { (method, label) ->
                    SetupToggle(label, method in current.creditPaymentMethods) { checked ->
                        val methods = if (checked) current.creditPaymentMethods + method else current.creditPaymentMethods - method
                        if (methods.isEmpty()) error = "Conserva al menos una forma de pago."
                        else {
                            error = null
                            draft = current.copy(creditPaymentMethods = methods)
                        }
                    }
                }
            }
            else -> return@Column
        }
        Button(onClick = {
            saving = true
            error = null
            status = null
            scope.launch {
                api.updateCommerce(current).onSuccess {
                    draft = it
                    original = it
                    status = "Preferencias guardadas en la sucursal."
                    onSave()
                }.onFailure { error = it.message }
                saving = false
            }
        }, enabled = !saving && current != original && (tab != SettingsTab.SHIFTS || cashLimit.isBlank() || current.cashDrawerLimitCents != null)) { Text("Guardar cambios") }
        if (tab == SettingsTab.CATALOG) PriceListSettings(api)
    }
}


@Composable
private fun PriceListSettings(api: ApiClient) {
    val scope = rememberCoroutineScope()
    var lists by remember { mutableStateOf<List<CatalogPriceList>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(api) { api.priceLists().onSuccess { lists = it }.onFailure { error = it.message } }
    HorizontalDivider()
    Text("Listas de precio", style = MaterialTheme.typography.titleMedium)
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    lists.forEach { item ->
        key(item.level) {
            var name by remember(item.name) { mutableStateOf(item.name) }
            var enabled by remember(item.enabled) { mutableStateOf(item.enabled) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedTextField(name, { name = it.take(60) }, label = { Text("Precio ${item.level}") },
                    enabled = !busy, singleLine = true, modifier = Modifier.weight(1f))
                Switch(enabled, { enabled = it }, enabled = !busy)
                TextButton(onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        api.updatePriceList(item.level, UpdateCatalogPriceList(name, enabled)).onSuccess { updated ->
                            lists = lists.map { if (it.level == updated.level) updated else it }
                        }.onFailure { error = it.message }
                        busy = false
                    }
                }, enabled = !busy && name.isNotBlank() && (name != item.name || enabled != item.enabled)) { Text("Guardar lista") }
            }
        }
    }
}
