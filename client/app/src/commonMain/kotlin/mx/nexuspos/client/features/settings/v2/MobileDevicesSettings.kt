package mx.nexuspos.client.features.settings.v2

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import mx.nexuspos.client.core.ApiClient
import mx.nexuspos.client.features.sales.v2.dialogs.formatShiftHistoryTimestamp
import mx.nexuspos.contracts.mobile.MobileDevice
import mx.nexuspos.contracts.mobile.MobilePairingCode

@Composable
internal fun MobileDevicesSettings(api: ApiClient) {
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var code by remember { mutableStateOf<MobilePairingCode?>(null) }
    var devices by remember { mutableStateOf<List<MobileDevice>?>(null) }
    var revoke by remember { mutableStateOf<MobileDevice?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun run(action: suspend (String) -> Unit) {
        if (busy || pin.length !in setOf(4, 6)) return
        val authorization = pin
        busy = true
        error = null
        scope.launch {
            try {
                action(authorization)
            } finally {
                pin = ""
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Vincula celulares y tablets con un código de un solo uso.")
        OutlinedTextField(
            pin, { input -> if (input.length <= 6 && input.all { it in '0'..'9' }) pin = input },
            label = { Text("PIN de administrador") }, enabled = !busy, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { run { code = api.issueMobilePairing(it).getOrElse { failure -> error = failure.message; null } } },
                enabled = pin.length in setOf(4, 6) && !busy) { Text("Generar código") }
            OutlinedButton(onClick = { run { devices = api.listMobileDevices(it).getOrElse { failure -> error = failure.message; null } } },
                enabled = pin.length in setOf(4, 6) && !busy) { Text("Consultar dispositivos") }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        code?.let {
            Text(it.code, style = MaterialTheme.typography.headlineMedium)
            Text("Vence ${formatShiftHistoryTimestamp(it.expiresAt, true)}. Ingrésalo en NexusPOS Móvil con la dirección HTTPS de la tienda.")
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(devices.orEmpty(), key = { it.id }) { device ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(device.name, style = MaterialTheme.typography.titleSmall)
                            Text(if (device.revoked) "Revocado" else "Vinculado")
                        }
                        if (!device.revoked) TextButton(onClick = { revoke = device }, enabled = pin.length in setOf(4, 6) && !busy) { Text("Revocar") }
                    }
                }
            }
        }
    }
    revoke?.let { device ->
        AlertDialog(
            onDismissRequest = { revoke = null },
            title = { Text("Revocar dispositivo") },
            text = { Text("${device.name} tendrá que vincularse de nuevo para consultar la tienda.") },
            confirmButton = {
                TextButton(onClick = {
                    revoke = null
                    run { authorization ->
                        api.revokeMobileDevice(authorization, device.id).onSuccess {
                            devices = devices?.map { if (it.id == device.id) it.copy(revoked = true) else it }
                        }.onFailure { error = it.message }
                    }
                }) { Text("Revocar acceso") }
            },
            dismissButton = { TextButton(onClick = { revoke = null }) { Text("Cancelar") } }
        )
    }
}
