package mx.nexuspos.client.features.settings.v2

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import mx.nexuspos.client.core.ApiClient
import mx.nexuspos.client.features.setup.RootCredentialForm
import mx.nexuspos.client.features.setup.SetupText
import mx.nexuspos.contracts.cash.CashRegisterDto
import mx.nexuspos.contracts.cash.SaveCashRegisterRequest
import mx.nexuspos.contracts.system.SetupState
import mx.nexuspos.contracts.system.StoreProfile

@Composable
internal fun BusinessSettingsV2(api: ApiClient, terminalId: String, registerId: String, onRootConfigured: () -> Unit, onSave: () -> Unit) {
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf<StoreProfile?>(null) }
    var mode by remember { mutableStateOf<SetupState?>(null) }
    var adopt by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(api, reload) {
        error = null
        mode = api.setupStatus().getOrNull()?.state
        api.getStoreProfile().onSuccess { profile = it }.onFailure { error = it.message }
    }
    if (adopt) {
        RootCredentialForm(api, recovery = false, onCancel = { adopt = false }, onFinish = onRootConfigured)
        return
    }
    Column(Modifier.verticalScroll(rememberScrollState()).widthIn(max = 650.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (mode == SetupState.LEGACY) {
            FilledTonalButton(onClick = { adopt = true }) { Text("Configurar administrador raíz") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (saved) Text("Guardado en la sucursal.", color = MaterialTheme.colorScheme.primary)
        val data = profile
        if (data == null) {
            if (error == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            else OutlinedButton(onClick = { reload++ }) { Text("Reintentar") }
            return@Column
        }
        data.ownerName?.let { Text("Administrador raíz · $it") }
        SelectionContainer {
            Column {
                data.businessId?.let { Text("ID de negocio · $it") }
                data.branchId?.let { Text("ID de sucursal · $it") }
                if (terminalId.isNotBlank()) Text("ID de terminal · $terminalId")
                Text("Caja asignada · $registerId")
            }
        }
        SetupText("Nombre del negocio", data.businessName, { profile = data.copy(businessName = it) })
        SetupText("Giro", data.businessType, { profile = data.copy(businessType = it) }, 80)
        SetupText("País", data.country, { profile = data.copy(country = it.uppercase()) }, 2)
        SetupText("Sucursal", data.branchName, { profile = data.copy(branchName = it) })
        SetupText("Domicilio", data.address, { profile = data.copy(address = it) }, 300)
        SetupText("Teléfono", data.phone, { profile = data.copy(phone = it) }, 40)
        SetupText("Zona horaria IANA", data.timeZone, { profile = data.copy(timeZone = it) }, 80)
        Button(onClick = {
            busy = true
            error = null
            saved = false
            scope.launch {
                api.updateStoreProfile(data).onSuccess {
                    profile = it
                    saved = true
                    onSave()
                }.onFailure { error = it.message }
                busy = false
            }
        }, enabled = !busy && data.businessName.isNotBlank() && data.branchName.isNotBlank() && data.address.isNotBlank()) {
            Text("Guardar negocio y sucursal")
        }
        RegisterSettings(api)
    }
}

@Composable
internal fun DiagnosticsSettingsV2(api: ApiClient, registerId: String) {
    val scope = rememberCoroutineScope()
    var health by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SelectionContainer {
            Column {
                Text("NexusPOS V2 · Escritorio")
                Text("Caja asignada · $registerId")
                Text("Servidor · ${api.baseUrl}")
                health?.let { Text(it) }
            }
        }
        Button(onClick = {
            busy = true
            scope.launch {
                health = if (api.checkHealth()) "Conexión al servidor disponible" else "Servidor inaccesible"
                busy = false
            }
        }, enabled = !busy) { Text("Probar conexión") }
    }
}


@Composable
private fun RegisterSettings(api: ApiClient) {
    val scope = rememberCoroutineScope()
    var registers by remember { mutableStateOf<List<CashRegisterDto>>(emptyList()) }
    var selected by remember { mutableStateOf<CashRegisterDto?>(null) }
    var name by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    suspend fun load() { api.cashRegisters().onSuccess { registers = it }.onFailure { error = it.message } }
    LaunchedEffect(api) { load() }
    HorizontalDivider()
    Text("Cajas de la sucursal", style = MaterialTheme.typography.titleMedium)
    registers.forEach { item ->
        TextButton(onClick = { selected = item; name = item.name; active = item.active }, enabled = !busy) {
            Text("${item.name} · ${item.id}" + if (item.active) "" else " · Inactiva")
        }
    }
    if (selected != null) TextButton(onClick = { selected = null; name = ""; active = true }) { Text("Nueva caja") }
    SetupText("Nombre de la caja", name, { name = it }, 100)
    if (selected != null) mx.nexuspos.client.features.setup.SetupToggle("Caja activa", active) { active = it }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Button(onClick = {
        busy = true
        error = null
        scope.launch {
            api.saveCashRegister(selected?.id, SaveCashRegisterRequest(name, active)).onSuccess {
                selected = it
                load()
            }.onFailure { error = it.message }
            busy = false
        }
    }, enabled = !busy && name.isNotBlank()) { Text(if (selected == null) "Crear caja" else "Guardar caja") }
}
