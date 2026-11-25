package mx.nexuspos.client.features.settings.v2

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mx.nexuspos.client.features.settings.SettingsUiState
import mx.nexuspos.client.features.settings.SettingsViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PrinterSettingsV2(state: SettingsUiState, viewModel: SettingsViewModel) {
    val ethernet = state.printerConnectionType == "ETHERNET_TCP"
    val invalidPort = ethernet && !state.tcpPort.isTcpPort()
    val connectionValid = !ethernet || (state.tcpHost.isNotBlank() && !invalidPort)
    val enabled = !state.isTestingPrinter
    TerminalSettingsForm("Guardar impresora", viewModel::savePrinterConfig, connectionValid && enabled) {
        SettingsSection("Conexión") {
            SettingsChoice(
                "Tipo de conexión", state.printerConnectionType,
                listOf("NONE" to "Desactivada", "USB_OS" to "USB o sistema operativo", "ETHERNET_TCP" to "Red Ethernet o Wi-Fi"),
                viewModel::onPrinterConnectionTypeChanged, enabled = enabled
            )
            when (state.printerConnectionType) {
                "USB_OS" -> {
                    val names = listOf(state.osPrinterName).filter { it.isNotBlank() } + state.availableOsPrinters
                    SettingsChoice(
                        "Impresora", state.osPrinterName,
                        listOf("" to "Predeterminada del sistema") + names.distinct().map { it to it },
                        viewModel::onOsPrinterNameChanged, enabled = enabled
                    )
                    OutlinedButton(onClick = viewModel::refreshAvailableOsPrinters, enabled = enabled) { Text("Detectar impresoras") }
                }
                "ETHERNET_TCP" -> {
                    SettingsField("IP o nombre del equipo", state.tcpHost, viewModel::onTcpHostChanged, enabled = enabled)
                    SettingsField(
                        "Puerto", state.tcpPort, viewModel::onTcpPortChanged, enabled = enabled,
                        error = if (invalidPort) "Ingresa un puerto entre 1 y 65535." else null
                    )
                }
            }
        }
        SettingsSection("Papel y cobro") {
            SettingsChoice("Ancho de papel", state.paperWidth, listOf("80mm" to "80 mm", "58mm" to "58 mm"), viewModel::onPaperWidthChanged, enabled = enabled)
            SettingsToggle("Imprimir al cobrar", "Emite el ticket al completar una venta.", state.autoPrintOnSale, viewModel::onAutoPrintOnSaleChanged, enabled)
            SettingsToggle("Abrir cajón al cobrar en efectivo", "Envía la orden de apertura a la impresora.", state.openDrawerOnCashSale, viewModel::onOpenDrawerOnCashSaleChanged, enabled)
        }
        SettingsSection("Diagnóstico") {
            val canTest = enabled && connectionValid && state.printerConnectionType != "NONE"
            Text("Las pruebas usan los datos de conexión del formulario.", style = MaterialTheme.typography.bodySmall)
            if (state.isTestingPrinter) LinearProgressIndicator(Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(viewModel::testPrinter, enabled = canTest) {
                    Text("Imprimir prueba")
                }
                OutlinedButton(viewModel::testCashDrawer, enabled = canTest) { Text("Abrir cajón") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ScaleSettingsV2(state: SettingsUiState, viewModel: SettingsViewModel) {
    val enabled = !state.isTestingScale
    val hasChanges = viewModel.getCurrentScaleConfig() != state.appliedScaleConfig
    TerminalSettingsForm("Guardar báscula", viewModel::saveScaleConfig, hasChanges && enabled) {
        SettingsSection("Conexión y pesaje") {
            SettingsToggle("Activar báscula", "Habilita el pesaje en esta caja.", state.scaleEnabled, viewModel::onScaleEnabledChanged, enabled)
            if (state.scaleEnabled) {
                val ports = (listOf(state.scalePortName) + state.availableScalePorts)
                    .filter { it.isNotBlank() && it != "AUTO" }.distinct()
                SettingsChoice(
                    "Puerto serial", state.scalePortName,
                    listOf("AUTO" to "Detección automática") + ports.map { it to it },
                    viewModel::onScalePortNameChanged, enabled = enabled
                )
                OutlinedButton(viewModel::refreshAvailableScalePorts, enabled = enabled) { Text("Detectar puertos") }
                SettingsChoice(
                    "Protocolo", state.scaleProtocol,
                    listOf("TORREY" to "Torrey", "RHINO" to "Rhino", "GENERIC" to "Genérico RS-232", "SIMULATOR" to "Simulador"),
                    viewModel::onScaleProtocolChanged, enabled = enabled
                )
                SettingsChoice(
                    "Velocidad", state.scaleBaudRate,
                    listOf("2400", "4800", "9600", "19200", "115200").map { it to "$it baudios" },
                    viewModel::onScaleBaudRateChanged, enabled = enabled
                )
                SettingsToggle(
                    "Aceptar peso estable automáticamente", "Agrega el peso al capturar un producto a granel.",
                    state.scaleAutoAcceptWeight, viewModel::onScaleAutoAcceptWeightChanged, enabled
                )
            }
        }
        SettingsSection("Diagnóstico") {
            if (hasChanges) {
                Text("Guarda los cambios para probar la conexión nueva.", style = MaterialTheme.typography.bodySmall)
            } else {
                TestResult(state.scaleTestResult, state.isScaleTestSuccess)
            }
            val canTest = state.scaleEnabled && !hasChanges && enabled
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(viewModel::testScale, enabled = canTest) {
                    Text(if (state.isTestingScale) "Leyendo…" else "Probar lectura")
                }
                OutlinedButton(viewModel::tareScale, enabled = canTest) { Text("Tarar") }
                OutlinedButton(viewModel::zeroScale, enabled = canTest) { Text("Poner a cero") }
            }
        }
    }
}

@Composable
internal fun NetworkSettingsV2(state: SettingsUiState, viewModel: SettingsViewModel) {
    val validHost = state.serverHost.isNotBlank() && state.serverHost.none { it.isWhitespace() || it in "/?#@" }
    val validPort = state.serverPort.isTcpPort()
    val canConnect = validHost && validPort && !state.isTestingNetwork && !state.isLoading && !state.isLoadingUsers &&
        state.userStatusChangeId == null
    TerminalSettingsForm("Guardar y conectar", viewModel::saveNetworkConfig, canConnect && state.registerId.isNotBlank()) {
        SettingsSection("Servidor LAN") {
            SettingsField(
                "IP o nombre del servidor", state.serverHost, viewModel::onServerHostChanged,
                enabled = !state.isTestingNetwork,
                error = if (!validHost) "Ingresa la IP o el nombre del servidor sin la ruta." else null
            )
            SettingsField(
                "Puerto", state.serverPort, viewModel::onServerPortChanged, enabled = !state.isTestingNetwork,
                error = if (!validPort) "Ingresa un puerto entre 1 y 65535." else null
            )
            OutlinedButton(viewModel::testNetwork, enabled = canConnect) {
                Text(if (state.isTestingNetwork) "Probando…" else "Probar conexión")
            }
            TestResult(state.networkTestResult, state.isNetworkTestSuccess)
        }
        SettingsSection("Identidad de esta caja") {
            SettingsField("Identificador de caja", state.registerId, viewModel::onRegisterIdChanged)
            SettingsField("Nombre de la terminal", state.registerName, viewModel::onRegisterNameChanged)
            Text("Al guardar se aplica la conexión a esta terminal.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TestResult(message: String?, success: Boolean) {
    if (message == null) return
    Text(
        message,
        style = MaterialTheme.typography.bodySmall,
        color = if (success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    )
}

@Composable
private fun TerminalSettingsForm(
    saveLabel: String,
    save: () -> Unit,
    canSave: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
        SettingsSave(saveLabel, save, canSave)
    }
}

private fun String.isTcpPort(): Boolean = toIntOrNull()?.let { it in 1..65535 } == true
