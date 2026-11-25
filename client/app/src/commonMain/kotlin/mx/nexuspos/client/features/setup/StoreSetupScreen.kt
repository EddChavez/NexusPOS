package mx.nexuspos.client.features.setup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import mx.nexuspos.client.core.ApiClient
import mx.nexuspos.contracts.system.*

private val steps = listOf("Negocio", "Sucursal", "Caja", "Administrador raíz", "Preferencias", "Revisar y guardar")

@Composable
fun StoreSetupScreen(api: ApiClient, onConfigureConnection: () -> Unit, onComplete: (StoreSetupResult) -> Unit) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) }
    var business by remember { mutableStateOf("") }
    var businessType by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("MX") }
    var branch by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var zone by remember { mutableStateOf("America/Mexico_City") }
    var register by remember { mutableStateOf("Caja Principal") }
    var rootName by remember { mutableStateOf("") }
    var rootPin by remember { mutableStateOf("") }
    var pinAgain by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var keypadVisible by remember { mutableStateOf(false) }
    var negative by remember { mutableStateOf(true) }
    var blind by remember { mutableStateOf(false) }
    var discount by remember { mutableStateOf("10") }
    var loyalty by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val parsedDiscount = discount.toDoubleOrNull()
    val canAdvance = when (step) {
        0 -> business.isNotBlank() && country.trim().length == 2
        1 -> branch.isNotBlank() && address.isNotBlank() && zone.isNotBlank()
        2 -> register.isNotBlank()
        3 -> rootName.isNotBlank() && rootPin.length == 6 && rootPin == pinAgain
        4 -> parsedDiscount != null && parsedDiscount in 0.0..100.0
        else -> code.length == 8
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(Modifier.widthIn(max = 620.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Configura NexusPOS", style = MaterialTheme.typography.headlineMedium)
                LinearProgressIndicator(progress = { (step + 1f) / steps.size }, modifier = Modifier.fillMaxWidth())
                Text("Paso ${step + 1} de ${steps.size} · ${steps[step]}", style = MaterialTheme.typography.titleMedium)
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        when (step) {
                            0 -> {
                                SetupText("Nombre del negocio", business, { business = it }, 128)
                                SetupText("Giro (opcional)", businessType, { businessType = it }, 80)
                                SetupText("País (código de dos letras)", country, { country = it.uppercase() }, 2)
                            }
                            1 -> {
                                SetupText("Nombre de la sucursal", branch, { branch = it }, 128)
                                SetupText("Dirección", address, { address = it }, 300)
                                SetupText("Teléfono (opcional)", phone, { phone = it }, 40)
                                SetupText("Zona horaria IANA", zone, { zone = it }, 80)
                            }
                            2 -> SetupText("Nombre de la caja", register, { register = it }, 128)
                            3 -> {
                                SetupText("Nombre del administrador", rootName, { rootName = it }, 128)
                                SetupPin("PIN de 6 dígitos", rootPin, { rootPin = it }, 6)
                                SetupPin("Confirmar PIN", pinAgain, { pinAgain = it }, 6)
                                TextButton(onClick = { keypadVisible = !keypadVisible }) {
                                    Text(if (keypadVisible) "Ocultar teclado numérico" else "Mostrar teclado numérico")
                                }
                                if (keypadVisible) SetupKeypad(
                                    onDigit = { digit ->
                                        if (rootPin.length < 6) rootPin += digit else if (pinAgain.length < 6) pinAgain += digit
                                    },
                                    onDelete = {
                                        if (pinAgain.isNotEmpty()) pinAgain = pinAgain.dropLast(1) else rootPin = rootPin.dropLast(1)
                                    }
                                )
                                if (pinAgain.isNotEmpty() && rootPin != pinAgain) Text("Los PIN deben coincidir.", color = MaterialTheme.colorScheme.error)
                            }
                            4 -> {
                                SetupToggle("Permitir inventario negativo", negative) { negative = it }
                                SetupToggle("Activar arqueo ciego", blind) { blind = it }
                                SetupText("Descuento máximo de cajero (%)", discount, { discount = it }, 6)
                                SetupToggle("Habilitar monedero", loyalty) { loyalty = it }
                            }
                            else -> {
                                Text("$business · $branch")
                                Text("$address · $zone")
                                Text("$register · Administrador raíz: $rootName")
                                Text("Descuento máximo: $discount % · Arqueo ciego: ${if (blind) "sí" else "no"}")
                                Text("Genera el código de activación en el servidor y captúralo aquí.", style = MaterialTheme.typography.bodyMedium)
                                SetupPin("Código de activación", code, { code = it }, 8, !busy)
                            }
                        }
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (step == 0) TextButton(onConfigureConnection, enabled = !busy) { Text("Configurar conexión") }
                    else TextButton(onClick = { step--; error = null }, enabled = !busy) { Text("Atrás") }
                    Button(onClick = {
                        if (step < steps.lastIndex) {
                            step++
                            error = null
                        } else {
                            busy = true
                            error = null
                            scope.launch {
                                api.completeSetup(StoreSetupRequest(
                                    code, business, businessType, country, branch, address, phone, zone, register,
                                    rootName, rootPin, negative, blind, checkNotNull(parsedDiscount), loyalty
                                )).onSuccess {
                                    rootPin = ""
                                    pinAgain = ""
                                    code = ""
                                    onComplete(it)
                                }.onFailure { error = it.message }
                                busy = false
                            }
                        }
                    }, enabled = canAdvance && !busy) { Text(if (step == steps.lastIndex) "Guardar configuración" else "Continuar") }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun RootCredentialForm(api: ApiClient, recovery: Boolean, onCancel: () -> Unit, onFinish: () -> Unit) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var adminPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var cashierPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    Column(
        Modifier.widthIn(max = 550.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(if (recovery) "Recuperar PIN raíz" else "Configurar administrador raíz", style = MaterialTheme.typography.titleLarge)
        Text("Solicita un código de un solo uso en el servidor.")
        SetupPin("Código de configuración", code, { code = it }, 8, !busy)
        if (!recovery) SetupPin("PIN del administrador vigente", adminPin, { adminPin = it }, 4, !busy)
        SetupPin("Nuevo PIN raíz de 6 dígitos", newPin, { newPin = it }, 6, !busy)
        SetupPin("Confirmar PIN raíz", repeat, { repeat = it }, 6, !busy)
        if (!recovery) SetupPin("Nuevo PIN del cajero inicial (si usa el de fábrica)", cashierPin, { cashierPin = it }, 4, !busy)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onCancel, enabled = !busy) { Text("Volver") }
            Button(onClick = {
                busy = true
                error = null
                scope.launch {
                    val result = if (recovery) api.recoverRoot(RecoverRootRequest(code, newPin))
                    else api.adoptRoot(AdoptRootRequest(code, adminPin, newPin, cashierPin.ifBlank { null }))
                    result.onSuccess { onFinish() }.onFailure { error = it.message }
                    busy = false
                }
            }, enabled = !busy && code.length == 8 && newPin.length == 6 && newPin == repeat && (recovery || adminPin.length == 4)) {
                Text("Guardar PIN")
            }
        }
    }
}

@Composable
internal fun SetupText(label: String, value: String, onChange: (String) -> Unit, max: Int = 128) {
    OutlinedTextField(value, { onChange(it.take(max)) }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun SetupPin(label: String, value: String, onChange: (String) -> Unit, max: Int, enabled: Boolean = true) {
    OutlinedTextField(
        value, { onChange(it.filter { c -> c in '0'..'9' }.take(max)) }, modifier = Modifier.fillMaxWidth(),
        label = { Text(label) }, enabled = enabled, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        visualTransformation = PasswordVisualTransformation()
    )
}

@Composable
internal fun SetupToggle(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, change)
    }
}


@Composable
private fun SetupKeypad(onDigit: (Char) -> Unit, onDelete: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf("123", "456", "789", "0").forEach { digits ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                digits.forEach { digit -> OutlinedButton(onClick = { onDigit(digit) }) { Text(digit.toString()) } }
                if (digits == "0") TextButton(onDelete) { Text("Borrar") }
            }
        }
    }
}
