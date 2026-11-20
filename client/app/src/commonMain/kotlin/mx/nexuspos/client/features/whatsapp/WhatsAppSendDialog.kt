package mx.nexuspos.client.features.whatsapp

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import mx.nexuspos.client.theme.NexusTheme

@Composable
fun WhatsAppSendDialog(
    isOpen: Boolean,
    title: String = "Enviar por WhatsApp",
    initialPhone: String = "",
    messageText: String,
    defaultCountryCode: String = "52",
    onSend: (url: String) -> Unit,
    onCopyText: (text: String) -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    var phoneNumber by remember(initialPhone) { mutableStateOf(initialPhone) }
    var copiedFeedback by remember { mutableStateOf(false) }

    val cleanPhone = remember(phoneNumber, defaultCountryCode) {
        WhatsAppMessageFormatter.cleanAndFormatPhoneNumber(phoneNumber, defaultCountryCode)
    }

    val whatsappUrl = remember(cleanPhone, messageText) {
        WhatsAppMessageFormatter.buildWhatsAppUrl(cleanPhone, messageText, defaultCountryCode)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = NexusTheme.colors.bgSurface,
            border = BorderStroke(1.dp, NexusTheme.colors.borderPrimary),
            tonalElevation = 8.dp,
            modifier = Modifier
                .width(580.dp)
                .heightIn(max = 680.dp)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = Color(0xFF25D366).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = null,
                                    tint = Color(0xFF25D366)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = NexusTheme.colors.textPrimary
                            )
                            Text(
                                text = "Envío directo a WhatsApp Web / Desktop",
                                style = MaterialTheme.typography.bodySmall,
                                color = NexusTheme.colors.textSecondary
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }

                HorizontalDivider(color = NexusTheme.colors.borderSubtle)

                // Phone Input Card
                Surface(
                    color = NexusTheme.colors.bgCanvas,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Número de Teléfono del Cliente (10 dígitos):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = NexusTheme.colors.textPrimary
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                color = NexusTheme.colors.bgSurfaceSubtle,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle),
                                modifier = Modifier.height(54.dp)
                            ) {
                                Box(modifier = Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "+$defaultCountryCode",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = NexusTheme.colors.textPrimary
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = phoneNumber,
                                onValueChange = { phoneNumber = it },
                                placeholder = { Text("ej. 55 1234 5678") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (cleanPhone.isNotBlank()) {
                            Text(
                                text = "Destino normalizado: +$cleanPhone",
                                fontSize = 11.sp,
                                color = Color(0xFF25D366),
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Text(
                                text = "Puede dejar el número vacío si prefiere elegir el contacto directamente en WhatsApp.",
                                fontSize = 11.sp,
                                color = NexusTheme.colors.textSecondary
                            )
                        }
                    }
                }

                // Message Preview
                Text(
                    text = "Vista Previa del Mensaje:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = NexusTheme.colors.textSecondary
                )

                Surface(
                    color = NexusTheme.colors.bgCanvas,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = messageText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp,
                            color = NexusTheme.colors.textPrimary
                        )
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            onCopyText(messageText)
                            copiedFeedback = true
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = if (copiedFeedback) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (copiedFeedback) "¡Copiado!" else "Copiar Mensaje")
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(8.dp)) {
                            Text("Cerrar")
                        }

                        Button(
                            onClick = {
                                onSend(whatsappUrl)
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                            Spacer(Modifier.width(6.dp))
                            Text("Abrir en WhatsApp", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
