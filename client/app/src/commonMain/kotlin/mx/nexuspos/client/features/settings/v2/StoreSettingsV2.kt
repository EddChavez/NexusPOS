package mx.nexuspos.client.features.settings.v2

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mx.nexuspos.client.features.settings.SettingsUiState
import mx.nexuspos.client.features.settings.SettingsViewModel
import mx.nexuspos.client.features.settings.ThermalTicketPreview
import mx.nexuspos.contracts.system.OperatingPolicies

@Composable
internal fun StoreSettingsV2(state: SettingsUiState, viewModel: SettingsViewModel) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            if (maxWidth >= 650.dp) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StoreFields(state, viewModel)
                    }
                    Column(
                        Modifier.width(272.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TicketPreview(state)
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StoreFields(state, viewModel)
                    TicketPreview(state)
                }
            }
        }
        SettingsSave("Guardar tienda y ticket", viewModel::saveStorePreferences, saving = state.isSaving)
    }
}

@Composable
private fun TicketPreview(state: SettingsUiState) {
    Text("Vista previa del ticket", style = MaterialTheme.typography.titleMedium)
    ThermalTicketPreview(state)
}

@Composable
private fun StoreFields(state: SettingsUiState, viewModel: SettingsViewModel) {
    val enabled = !state.isSaving
    SettingsSection("Datos del negocio") {
        SettingsField("Nombre de la tienda", state.storeName, viewModel::onStoreNameChanged, enabled = enabled)
        SettingsField("Dirección", state.storeAddress, viewModel::onStoreAddressChanged, enabled = enabled)
        SettingsField("Teléfono", state.storePhone, viewModel::onStorePhoneChanged, enabled = enabled)
        SettingsField("RFC", state.storeTaxId, viewModel::onStoreTaxIdChanged, enabled = enabled)
        SettingsField("Símbolo de moneda", state.currencySymbol, viewModel::onCurrencySymbolChanged, enabled = enabled)
    }
    SettingsSection("Leyendas del ticket") {
        SettingsField("Encabezado", state.ticketHeader, viewModel::onTicketHeaderChanged, enabled = enabled, multiline = true)
        SettingsField("Pie del ticket", state.ticketFooter, viewModel::onTicketFooterChanged, enabled = enabled, multiline = true)
    }
    SettingsSection("WhatsApp") {
        SettingsField("Código de país", state.whatsappCountryCode, viewModel::onWhatsappCountryCodeChanged, enabled = enabled)
        SettingsField("Mensaje de despedida", state.whatsappCustomFooter, viewModel::onWhatsappCustomFooterChanged, enabled = enabled)
        SettingsField("Datos para transferencias de fiado", state.whatsappBankDetails, viewModel::onWhatsappBankDetailsChanged, enabled = enabled, multiline = true)
    }
}

@Composable
internal fun PolicySettingsV2(state: SettingsUiState, viewModel: SettingsViewModel) {
    var negativeStock by rememberSaveable(state.allowNegativeStock) { mutableStateOf(state.allowNegativeStock) }
    var blindCount by rememberSaveable(state.blindCashCountEnabled) { mutableStateOf(state.blindCashCountEnabled) }
    var discount by rememberSaveable(state.maxCashierDiscountPercent) { mutableStateOf(state.maxCashierDiscountPercent.toString()) }
    var loyalty by rememberSaveable(state.loyaltyEnabled) { mutableStateOf(state.loyaltyEnabled) }
    var cashback by rememberSaveable(state.loyaltyCashbackPercent) { mutableStateOf(state.loyaltyCashbackPercent.toString()) }
    var minRedeem by rememberSaveable(state.loyaltyMinRedeemCents) {
        mutableStateOf(formatSettingsAmount(state.loyaltyMinRedeemCents))
    }

    val enabled = !state.isSaving
    val discountPercent = discount.toPercentage()
    val cashbackPercent = cashback.toPercentage()
    val minimumCents = minRedeem.toSettingsCents()

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsSection("Inventario") {
                SettingsToggle(
                    "Permitir ventas sin existencias",
                    "Al desactivar esta opción, se bloquean las ventas que dejarían el saldo en negativo.",
                    negativeStock, { negativeStock = it }, enabled
                )
            }
            SettingsSection("Operación de caja") {
                SettingsToggle(
                    "Arqueo ciego",
                    "Oculta al cajero el importe esperado durante el conteo de efectivo.",
                    blindCount, { blindCount = it }, enabled
                )
                SettingsField(
                    "Descuento máximo para cajeros (%)", discount, { discount = it }, enabled = enabled,
                    error = if (discountPercent == null) "Ingresa un porcentaje entre 0 y 100." else null
                )
            }
            SettingsSection("Monedero electrónico") {
                SettingsToggle("Activar monedero", "Acumula saldo para próximas compras.", loyalty, { loyalty = it }, enabled)
                SettingsField(
                    "Saldo que se acumula por compra (%)", cashback, { cashback = it }, enabled = enabled,
                    error = if (cashbackPercent == null) "Ingresa un porcentaje entre 0 y 100." else null
                )
                SettingsField(
                    "Saldo mínimo para canjear (${state.currencySymbol})", minRedeem, { minRedeem = it }, enabled = enabled,
                    error = if (minimumCents == null) "Ingresa un importe con hasta dos decimales." else null
                )
            }
        }
        SettingsSave(
            label = "Guardar políticas",
            enabled = discountPercent != null && cashbackPercent != null && minimumCents != null,
            saving = state.isSaving,
            onSave = {
                if (discountPercent != null && cashbackPercent != null && minimumCents != null) {
                    viewModel.saveOperatingPolicies(
                        OperatingPolicies(negativeStock, blindCount, discountPercent, loyalty, cashbackPercent, minimumCents)
                    )
                }
            }
        )
    }
}

private fun String.toPercentage(): Double? =
    trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.0..100.0 }

private fun String.toSettingsCents(): Long? {
    val amount = trim().replace(',', '.')
    if (!amount.matches(Regex("[0-9]+(\\.[0-9]{1,2})?"))) return null
    val whole = amount.substringBefore('.').toLongOrNull() ?: return null
    val fraction = amount.substringAfter('.', "").padEnd(2, '0').toLong()
    if (whole > (Long.MAX_VALUE - fraction) / 100) return null
    return whole * 100 + fraction
}

private fun formatSettingsAmount(cents: Long): String =
    "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
