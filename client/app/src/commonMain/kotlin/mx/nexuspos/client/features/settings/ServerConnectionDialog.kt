package mx.nexuspos.client.features.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import mx.nexuspos.client.theme.NexusTheme

@Composable
fun ServerConnectionDialog(
    isOpen: Boolean,
    initialHost: String = "localhost",
    initialPort: Int = 8080,
    initialScheme: String = "http",
    initialRegisterId: String = "caja-1",
    initialRegisterName: String = "Caja Principal",
    onTestConnection: suspend (scheme: String, host: String, port: Int) -> Result<String>,
    onSaveAndConnect: (scheme: String, host: String, port: Int, registerId: String, registerName: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isOpen) return

    val coroutineScope = rememberCoroutineScope()
    var scheme by remember { mutableStateOf(initialScheme) }
    var host by remember { mutableStateOf(initialHost) }
    var portText by remember { mutableStateOf(initialPort.toString()) }
    var registerId by remember { mutableStateOf(initialRegisterId) }
    var registerName by remember { mutableStateOf(initialRegisterName) }

    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var isTestSuccess by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = NexusTheme.colors.bgSurface,
            border = BorderStroke(1.dp, NexusTheme.colors.borderPrimary),
            tonalElevation = 8.dp,
            modifier = modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth(0.92f)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Encabezado
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Conexión con Servidor NexusPOS",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = NexusTheme.colors.textPrimary
                        )
                        Text(
                            text = "Configuración de red local (LAN) para terminales y cajas",
                            style = MaterialTheme.typography.bodySmall,
                            color = NexusTheme.colors.textSecondary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }

                HorizontalDivider(color = NexusTheme.colors.borderSubtle)

                // Presets rápidos
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Atajos:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = NexusTheme.colors.textSecondary
                    )
                    OutlinedButton(
                        onClick = {
                            host = "localhost"
                            portText = "8080"
                            scheme = "http"
                            testResult = null
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Localhost (Esta máquina)", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            if (!host.startsWith("192.168.")) {
                                host = "192.168.1."
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("IP LAN (192.168.x.x)", fontSize = 11.sp)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("http", "https").forEach { option ->
                        FilterChip(scheme == option, onClick = {
                            scheme = option
                            portText = if (option == "https") "443" else "8080"
                            testResult = null
                            isTestSuccess = false
                        }, label = { Text(option.uppercase()) })
                    }
                }

                // Campos de Servidor
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = {
                            host = it.trim()
                            testResult = null
                        },
                        label = { Text("IP o Host del Servidor") },
                        placeholder = { Text("ej. 192.168.1.100 o localhost") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Dns, contentDescription = null) },
                        modifier = Modifier.weight(0.68f)
                    )

                    OutlinedTextField(
                        value = portText,
                        onValueChange = {
                            portText = it.filter { ch -> ch.isDigit() }
                            testResult = null
                        },
                        label = { Text("Puerto") },
                        placeholder = { Text("8080") },
                        singleLine = true,
                        modifier = Modifier.weight(0.32f)
                    )
                }

                // Campos de Identificación de Caja
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = registerId,
                        onValueChange = { registerId = it.trim() },
                        label = { Text("ID de Caja") },
                        placeholder = { Text("caja-1") },
                        singleLine = true,
                        modifier = Modifier.weight(0.4f)
                    )

                    OutlinedTextField(
                        value = registerName,
                        onValueChange = { registerName = it },
                        label = { Text("Nombre Descriptivo de Terminal") },
                        placeholder = { Text("Caja Principal Mostrador") },
                        singleLine = true,
                        modifier = Modifier.weight(0.6f)
                    )
                }

                // Resultado del Test de Conexión
                testResult?.let { msg ->
                    Surface(
                        color = if (isTestSuccess) Color(0xFF059669).copy(alpha = 0.12f) else Color(0xFFDC2626).copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (isTestSuccess) Color(0xFF059669).copy(alpha = 0.4f) else Color(0xFFDC2626).copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(
                                imageVector = if (isTestSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (isTestSuccess) Color(0xFF059669) else Color(0xFFDC2626)
                            )
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isTestSuccess) Color(0xFF059669) else Color(0xFFDC2626),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Botones de acción
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = {
                            val port = portText.toIntOrNull() ?: 8080
                            isTesting = true
                            testResult = null
                            coroutineScope.launch {
                                val res = onTestConnection(scheme, host, port)
                                isTesting = false
                                res.onSuccess { info ->
                                    isTestSuccess = true
                                    testResult = "Conexión exitosa: $info"
                                }.onFailure { err ->
                                    isTestSuccess = false
                                    testResult = "Error de conexión: ${err.message ?: "Host inaccesible"}"
                                }
                            }
                        },
                        enabled = !isTesting && host.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Probando...")
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Probar Conexión")
                        }
                    }

                    Button(
                        onClick = {
                            val port = portText.toIntOrNull() ?: 8080
                            onSaveAndConnect(scheme, host, port, registerId, registerName)
                            onDismiss()
                        },
                        enabled = host.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Guardar y Conectar")
                    }
                }
            }
        }
    }
}
