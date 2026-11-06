package mx.nexuspos.client.features.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.nexuspos.client.theme.NexusTheme
import mx.nexuspos.client.ui.components.FeedbackMessageHost
import mx.nexuspos.contracts.identity.UserDto

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBackToSales: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()

    // User dialog
    if (state.showUserDialog) {
        UserFormDialog(
            editingUser = state.editingUser,
            name = state.userFormName,
            role = state.userFormRole,
            pin = state.userFormPin,
            errorMessage = state.userFormError,
            isSaving = state.isSaving,
            onNameChanged = viewModel::onUserFormNameChanged,
            onRoleChanged = viewModel::onUserFormRoleChanged,
            onPinChanged = viewModel::onUserFormPinChanged,
            onConfirm = viewModel::saveUser,
            onDismiss = viewModel::closeUserDialog
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusTheme.colors.bgCanvas)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top toolbar
        SettingsTopToolbar(
            selectedTab = state.selectedTab,
            onTabSelected = viewModel::selectTab,
            onBackToSales = onBackToSales
        )

        // Banners de notificación unificados
        FeedbackMessageHost(
            errorMessage = state.errorBanner,
            successMessage = state.successBanner,
            onDismissError = viewModel::clearBanners,
            onDismissSuccess = viewModel::clearBanners
        )

        // Tab content
        Surface(
            color = NexusTheme.colors.bgSurface,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, NexusTheme.colors.borderPrimary),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            when (state.selectedTab) {
                SettingsTab.STORE_TICKET -> StoreTicketTab(state = state, viewModel = viewModel)
                SettingsTab.POLICIES -> PoliciesTab(state = state, viewModel = viewModel)
                SettingsTab.PRINTER -> PrinterTab(state = state, viewModel = viewModel)
                SettingsTab.USERS -> UsersTab(state = state, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun SettingsTopToolbar(
    selectedTab: SettingsTab,
    onTabSelected: (SettingsTab) -> Unit,
    onBackToSales: () -> Unit
) {
    Surface(
        color = NexusTheme.colors.bgSurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, NexusTheme.colors.borderPrimary),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = null,
                    tint = NexusTheme.colors.accent,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Configuración del Sistema",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = NexusTheme.colors.textPrimary
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tab pills
                SettingsTab.entries.forEach { tab ->
                    val isSelected = tab == selectedTab
                    val (label, icon) = when (tab) {
                        SettingsTab.STORE_TICKET -> "Tienda & Ticket" to Icons.Default.Store
                        SettingsTab.POLICIES -> "Políticas" to Icons.Default.Policy
                        SettingsTab.PRINTER -> "Impresora" to Icons.Default.Print
                        SettingsTab.USERS -> "Usuarios" to Icons.Default.Group
                    }
                    FilterChip(
                        selected = isSelected,
                        onClick = { onTabSelected(tab) },
                        label = { Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        leadingIcon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }

                Spacer(Modifier.width(8.dp))

                OutlinedButton(
                    onClick = onBackToSales,
                    border = BorderStroke(1.dp, NexusTheme.colors.btnSecondaryBorder),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = NexusTheme.colors.btnSecondaryText
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("[Esc] Ventas", color = NexusTheme.colors.btnSecondaryText)
                }
            }
        }
    }
}

// =====================================================
// TAB 1: Datos de Tienda & Ticket
// =====================================================

@Composable
private fun StoreTicketTab(state: SettingsUiState, viewModel: SettingsViewModel) {
    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = NexusTheme.colors.accent)
        }
        return
    }

    Row(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        // Left: Form fields
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Datos de la Tienda",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = NexusTheme.colors.textPrimary
            )
            Divider(color = NexusTheme.colors.borderSubtle)

            OutlinedTextField(
                value = state.storeName,
                onValueChange = viewModel::onStoreNameChanged,
                label = { Text("Nombre de la Tienda") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.storeAddress,
                onValueChange = viewModel::onStoreAddressChanged,
                label = { Text("Dirección") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.storePhone,
                onValueChange = viewModel::onStorePhoneChanged,
                label = { Text("Teléfono") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.storeTaxId,
                onValueChange = viewModel::onStoreTaxIdChanged,
                label = { Text("RFC / ID Fiscal") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.currencySymbol,
                onValueChange = viewModel::onCurrencySymbolChanged,
                label = { Text("Símbolo de Moneda") },
                singleLine = true,
                modifier = Modifier.width(120.dp)
            )

            Spacer(Modifier.height(4.dp))
            Text(
                text = "Leyenda del Ticket",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = NexusTheme.colors.textPrimary
            )
            Divider(color = NexusTheme.colors.borderSubtle)

            OutlinedTextField(
                value = state.ticketHeader,
                onValueChange = viewModel::onTicketHeaderChanged,
                label = { Text("Encabezado del Ticket") },
                placeholder = { Text("Ej: ¡Bienvenido! Gracias por su preferencia") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.ticketFooter,
                onValueChange = viewModel::onTicketFooterChanged,
                label = { Text("Pie del Ticket") },
                placeholder = { Text("Ej: No se aceptan devoluciones sin ticket") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = viewModel::saveStoreSettings,
                enabled = !state.isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = NexusTheme.colors.actionPrimary),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = NexusTheme.colors.bgCanvas
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Guardar Configuración", fontWeight = FontWeight.Bold)
            }
        }

        // Right: Ticket preview
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Vista Previa del Ticket",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = NexusTheme.colors.textPrimary
            )
            Divider(color = NexusTheme.colors.borderSubtle)

            ThermalTicketPreview(state = state)
        }
    }
}

@Composable
private fun ThermalTicketPreview(state: SettingsUiState) {
    Surface(
        color = NexusTheme.colors.bgSurfaceSubtle,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, NexusTheme.colors.borderPrimary)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Ticket card
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    val mono = FontFamily.Monospace
                    val ticketText = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = mono, color = Color.Black, fontSize = 11.sp
                    )
                    val ticketBold = ticketText.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    val separator = "-".repeat(32)

                    if (state.ticketHeader.isNotBlank()) {
                        Text(state.ticketHeader, style = ticketText.copy(fontSize = 10.sp), color = Color.DarkGray)
                        Text(separator, style = ticketText, color = Color.Gray)
                    }

                    Text(
                        state.storeName.ifBlank { "NOMBRE DE LA TIENDA" },
                        style = ticketBold,
                        color = Color.Black
                    )
                    if (state.storeAddress.isNotBlank()) {
                        Text(state.storeAddress, style = ticketText, color = Color.DarkGray)
                    }
                    if (state.storePhone.isNotBlank()) {
                        Text("Tel: ${state.storePhone}", style = ticketText, color = Color.DarkGray)
                    }
                    if (state.storeTaxId.isNotBlank()) {
                        Text("RFC: ${state.storeTaxId}", style = ticketText, color = Color.DarkGray)
                    }

                    Text(separator, style = ticketText, color = Color.Gray)
                    Text("TICKET #0001", style = ticketText, color = Color.Gray)
                    Text("Fecha: 06/11/2026  11:30", style = ticketText, color = Color.Gray)
                    Text("Cajero: Cajero Ejemplo", style = ticketText, color = Color.Gray)
                    Text(separator, style = ticketText, color = Color.Gray)

                    // Sample items
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("1  Refresco 600ml", style = ticketText, color = Color.Black, modifier = Modifier.weight(1f))
                        Text("${state.currencySymbol}19.00", style = ticketText, color = Color.Black)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("2  Pan Blanco", style = ticketText, color = Color.Black, modifier = Modifier.weight(1f))
                        Text("${state.currencySymbol}18.00", style = ticketText, color = Color.Black)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("1  Leche 1L", style = ticketText, color = Color.Black, modifier = Modifier.weight(1f))
                        Text("${state.currencySymbol}25.50", style = ticketText, color = Color.Black)
                    }

                    Text(separator, style = ticketText, color = Color.Gray)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("SUBTOTAL", style = ticketText, color = Color.Black)
                        Text("${state.currencySymbol}62.50", style = ticketText, color = Color.Black)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("TOTAL", style = ticketBold, color = Color.Black)
                        Text("${state.currencySymbol}62.50", style = ticketBold, color = Color.Black)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("EFECTIVO", style = ticketText, color = Color.DarkGray)
                        Text("${state.currencySymbol}70.00", style = ticketText, color = Color.DarkGray)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("CAMBIO", style = ticketText, color = Color.DarkGray)
                        Text("${state.currencySymbol}7.50", style = ticketText, color = Color.DarkGray)
                    }

                    Text(separator, style = ticketText, color = Color.Gray)

                    if (state.ticketFooter.isNotBlank()) {
                        Text(state.ticketFooter, style = ticketText.copy(fontSize = 10.sp), color = Color.DarkGray)
                    } else {
                        Text("¡Gracias por su compra!", style = ticketText.copy(fontSize = 10.sp), color = Color.DarkGray)
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = "Vista previa — el ticket real puede variar según impresora",
                style = MaterialTheme.typography.labelSmall,
                color = NexusTheme.colors.textSecondary
            )
        }
    }
}

// =====================================================
// TAB 2: Políticas Operativas
// =====================================================

@Composable
private fun PoliciesTab(state: SettingsUiState, viewModel: SettingsViewModel) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.TopStart
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.widthIn(max = 600.dp)) {
            Text(
                text = "Políticas Operativas",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = NexusTheme.colors.textPrimary
            )

            Surface(
                color = NexusTheme.colors.bgSurfaceSubtle,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, NexusTheme.colors.borderPrimary)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Política de Inventario Negativo",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = NexusTheme.colors.textPrimary
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = if (state.allowNegativeStock)
                                    "Modo: Flexible — Ventas permitidas aunque no haya existencias"
                                else
                                    "Modo: Estricto — Ventas bloqueadas sin stock suficiente",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (state.allowNegativeStock)
                                    NexusTheme.colors.textGreen
                                else
                                    NexusTheme.colors.warningText
                            )
                        }

                        Switch(
                            checked = state.allowNegativeStock,
                            onCheckedChange = { viewModel.toggleNegativeStock() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = NexusTheme.colors.textGreen,
                                checkedTrackColor = NexusTheme.colors.surfaceGreen,
                                uncheckedThumbColor = NexusTheme.colors.warningText,
                                uncheckedTrackColor = NexusTheme.colors.warningBg
                            )
                        )
                    }

                    Divider(color = NexusTheme.colors.borderSubtle)

                    // Explanation cards
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PolicyExplanationCard(
                            title = "✅ Flexible (ON)",
                            description = "El sistema permite completar ventas aunque el inventario registrado sea insuficiente o negativo. Útil para operaciones donde el conteo de stock no es crítico en tiempo real.",
                            color = NexusTheme.colors.surfaceGreen,
                            borderColor = NexusTheme.colors.borderGreen,
                            textColor = NexusTheme.colors.textGreen,
                            isActive = state.allowNegativeStock,
                            modifier = Modifier.weight(1f)
                        )
                        PolicyExplanationCard(
                            title = "🚫 Estricto (OFF)",
                            description = "El sistema bloquea la venta con error 422 INSUFFICIENT_STOCK cuando no hay existencias suficientes. Recomendado para tiendas con inventario controlado.",
                            color = NexusTheme.colors.warningBg,
                            borderColor = NexusTheme.colors.warningBorder,
                            textColor = NexusTheme.colors.warningText,
                            isActive = !state.allowNegativeStock,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PolicyExplanationCard(
    title: String,
    description: String,
    color: androidx.compose.ui.graphics.Color,
    borderColor: androidx.compose.ui.graphics.Color,
    textColor: androidx.compose.ui.graphics.Color,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (isActive) color else NexusTheme.colors.bgSurface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(if (isActive) 1.5.dp else 1.dp, if (isActive) borderColor else NexusTheme.colors.borderSubtle),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = if (isActive) textColor else NexusTheme.colors.textSecondary
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = if (isActive) textColor.copy(alpha = 0.85f) else NexusTheme.colors.textSecondary
            )
        }
    }
}

// =====================================================
// TAB: Impresora de Tickets (Hardware ESC/POS)
// =====================================================

@Composable
private fun PrinterTab(state: SettingsUiState, viewModel: SettingsViewModel) {
    var expandedPrinterMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.TopStart
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Configuración de Impresora de Tickets (ESC/POS)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = NexusTheme.colors.textPrimary
            )

            // Section 1: Modo de Conexión
            Surface(
                color = NexusTheme.colors.bgSurfaceSubtle,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, NexusTheme.colors.borderPrimary)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Tipo de Conexión de Impresora",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = NexusTheme.colors.textPrimary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // NONE
                        FilterChip(
                            selected = state.printerConnectionType == "NONE",
                            onClick = { viewModel.onPrinterConnectionTypeChanged("NONE") },
                            label = { Text("Desactivada") },
                            leadingIcon = {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )
                        // USB_OS
                        FilterChip(
                            selected = state.printerConnectionType == "USB_OS",
                            onClick = { viewModel.onPrinterConnectionTypeChanged("USB_OS") },
                            label = { Text("USB / Sistema Operativo") },
                            leadingIcon = {
                                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )
                        // ETHERNET_TCP
                        FilterChip(
                            selected = state.printerConnectionType == "ETHERNET_TCP",
                            onClick = { viewModel.onPrinterConnectionTypeChanged("ETHERNET_TCP") },
                            label = { Text("Red Ethernet / WiFi (TCP)") },
                            leadingIcon = {
                                Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )
                    }

                    when (state.printerConnectionType) {
                        "USB_OS" -> {
                            Divider(color = NexusTheme.colors.borderSubtle)
                            Text(
                                text = "Seleccione la impresora térmica detectada en el sistema operativo:",
                                style = MaterialTheme.typography.bodySmall,
                                color = NexusTheme.colors.textSecondary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    OutlinedTextField(
                                        value = state.osPrinterName.ifBlank { "Predeterminada del SO (Automático)" },
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Impresora del Sistema") },
                                        trailingIcon = {
                                            IconButton(onClick = { expandedPrinterMenu = true }) {
                                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    DropdownMenu(
                                        expanded = expandedPrinterMenu,
                                        onDismissRequest = { expandedPrinterMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Predeterminada del SO (Automático)") },
                                            onClick = {
                                                viewModel.onOsPrinterNameChanged("")
                                                expandedPrinterMenu = false
                                            }
                                        )
                                        state.availableOsPrinters.forEach { p ->
                                            DropdownMenuItem(
                                                text = { Text(p) },
                                                onClick = {
                                                    viewModel.onOsPrinterNameChanged(p)
                                                    expandedPrinterMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                                OutlinedButton(
                                    onClick = viewModel::refreshAvailableOsPrinters,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(52.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Detectar")
                                }
                            }
                        }
                        "ETHERNET_TCP" -> {
                            Divider(color = NexusTheme.colors.borderSubtle)
                            Text(
                                text = "Parámetros de red para impresora térmica (Puerto estándar 9100):",
                                style = MaterialTheme.typography.bodySmall,
                                color = NexusTheme.colors.textSecondary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = state.tcpHost,
                                    onValueChange = viewModel::onTcpHostChanged,
                                    label = { Text("Dirección IP / Host") },
                                    placeholder = { Text("192.168.1.200") },
                                    singleLine = true,
                                    modifier = Modifier.weight(2f)
                                )
                                OutlinedTextField(
                                    value = state.tcpPort,
                                    onValueChange = viewModel::onTcpPortChanged,
                                    label = { Text("Puerto") },
                                    placeholder = { Text("9100") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        else -> {
                            Text(
                                text = "La impresión física está desactivada. Los tickets se visualizarán solo en pantalla.",
                                style = MaterialTheme.typography.bodySmall,
                                color = NexusTheme.colors.textSecondary
                            )
                        }
                    }
                }
            }

            // Section 2: Formato y Ancho de Papel
            Surface(
                color = NexusTheme.colors.bgSurfaceSubtle,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, NexusTheme.colors.borderPrimary)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Ancho de Papel Térmico",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = NexusTheme.colors.textPrimary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = state.paperWidth == "80mm",
                            onClick = { viewModel.onPaperWidthChanged("80mm") },
                            label = { Text("80 mm (Estándar - 42 columnas)") }
                        )
                        FilterChip(
                            selected = state.paperWidth == "58mm",
                            onClick = { viewModel.onPaperWidthChanged("58mm") },
                            label = { Text("58 mm (Compacto - 32 columnas)") }
                        )
                    }
                }
            }

            // Section 3: Comportamiento Automático
            Surface(
                color = NexusTheme.colors.bgSurfaceSubtle,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, NexusTheme.colors.borderPrimary)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "Automatización al Cobrar",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = NexusTheme.colors.textPrimary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Imprimir ticket automáticamente",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = NexusTheme.colors.textPrimary
                            )
                            Text(
                                text = "Envía el ticket a imprimir en cuanto se finaliza el cobro (efectivo o fiado).",
                                style = MaterialTheme.typography.bodySmall,
                                color = NexusTheme.colors.textSecondary
                            )
                        }
                        Switch(
                            checked = state.autoPrintOnSale,
                            onCheckedChange = viewModel::onAutoPrintOnSaleChanged,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = NexusTheme.colors.textGreen,
                                checkedTrackColor = NexusTheme.colors.surfaceGreen
                            )
                        )
                    }

                    Divider(color = NexusTheme.colors.borderSubtle)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Abrir cajón de dinero automáticamente",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = NexusTheme.colors.textPrimary
                            )
                            Text(
                                text = "Envía pulso ESC/POS (Pin 2 y 5) para abrir cajón en ventas en efectivo.",
                                style = MaterialTheme.typography.bodySmall,
                                color = NexusTheme.colors.textSecondary
                            )
                        }
                        Switch(
                            checked = state.openDrawerOnCashSale,
                            onCheckedChange = viewModel::onOpenDrawerOnCashSaleChanged,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = NexusTheme.colors.textGreen,
                                checkedTrackColor = NexusTheme.colors.surfaceGreen
                            )
                        )
                    }
                }
            }

            // Section 4: Botones de Prueba y Guardar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = viewModel::testPrinter,
                    enabled = state.printerConnectionType != "NONE" && !state.isTestingPrinter,
                    modifier = Modifier.height(44.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (state.isTestingPrinter) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                    } else {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text("Probar Impresión")
                }

                OutlinedButton(
                    onClick = viewModel::testCashDrawer,
                    enabled = state.printerConnectionType != "NONE",
                    modifier = Modifier.height(44.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Probar Cajón")
                }

                Spacer(Modifier.weight(1f))

                Button(
                    onClick = viewModel::savePrinterConfig,
                    colors = ButtonDefaults.buttonColors(containerColor = NexusTheme.colors.actionPrimary),
                    modifier = Modifier.height(44.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Guardar Configuración", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// =====================================================
// TAB 3: Usuarios y Cajeros
// =====================================================

@Composable
private fun UsersTab(state: SettingsUiState, viewModel: SettingsViewModel) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = state.userFilter,
                    onValueChange = viewModel::onUserFilterChanged,
                    placeholder = { Text("Buscar usuario...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Buscar", tint = NexusTheme.colors.textSecondary)
                    },
                    trailingIcon = {
                        if (state.userFilter.isNotBlank()) {
                            IconButton(onClick = { viewModel.onUserFilterChanged("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Limpiar", tint = NexusTheme.colors.textSecondary)
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.width(300.dp).height(48.dp)
                )

                FilterChip(
                    selected = state.includeInactiveUsers,
                    onClick = viewModel::onToggleIncludeInactiveUsers,
                    label = { Text("Ver Inactivos") },
                    leadingIcon = {
                        if (state.includeInactiveUsers) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                )
            }

            Button(
                onClick = viewModel::openCreateUserDialog,
                colors = ButtonDefaults.buttonColors(containerColor = NexusTheme.colors.actionPrimary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("[INS] Nuevo Usuario", fontWeight = FontWeight.Bold)
            }
        }

        // Table header
        Surface(
            color = NexusTheme.colors.bgSurfaceSubtle,
            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
            border = BorderStroke(1.dp, NexusTheme.colors.borderPrimary)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("NOMBRE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.textSecondary, modifier = Modifier.weight(1f))
                Text("ROL", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.textSecondary, modifier = Modifier.width(130.dp))
                Text("ESTADO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.textSecondary, modifier = Modifier.width(100.dp))
                Text("ACCIONES", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.textSecondary, modifier = Modifier.width(120.dp))
            }
        }

        // User list
        val filteredUsers = state.users.filter {
            state.userFilter.isBlank() || it.name.contains(state.userFilter, ignoreCase = true)
        }

        if (state.isLoadingUsers) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NexusTheme.colors.accent)
            }
        } else if (filteredUsers.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(48.dp), tint = NexusTheme.colors.textSecondary)
                    Text(
                        text = if (state.userFilter.isBlank()) "No hay usuarios registrados" else "Sin resultados para '${state.userFilter}'",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NexusTheme.colors.textSecondary
                    )
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp),
                border = BorderStroke(1.dp, NexusTheme.colors.borderPrimary),
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filteredUsers, key = { it.id }) { user ->
                        UserTableRow(
                            user = user,
                            onEdit = { viewModel.openEditUserDialog(user) },
                            onDeactivate = { viewModel.deactivateUser(user) },
                            onReactivate = { viewModel.reactivateUser(user) }
                        )
                        Divider(color = NexusTheme.colors.borderSubtle.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

@Composable
private fun UserTableRow(
    user: UserDto,
    onEdit: () -> Unit,
    onDeactivate: () -> Unit,
    onReactivate: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Name
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = NexusTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "ID: ${user.id.take(8)}...",
                style = MaterialTheme.typography.labelSmall,
                color = NexusTheme.colors.textSecondary,
                fontFamily = FontFamily.Monospace
            )
        }

        // Role badge
        Box(modifier = Modifier.width(130.dp)) {
            RoleBadge(role = user.role)
        }

        // Status badge
        Box(modifier = Modifier.width(100.dp)) {
            StatusBadge(isActive = user.isActive)
        }

        // Actions
        Row(
            modifier = Modifier.width(120.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (user.isActive) {
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Editar",
                        tint = NexusTheme.colors.accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onDeactivate, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.PersonOff,
                        contentDescription = "Desactivar",
                        tint = NexusTheme.colors.dangerText,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                IconButton(onClick = onReactivate, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.PersonAdd,
                        contentDescription = "Reactivar",
                        tint = NexusTheme.colors.textGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RoleBadge(role: String) {
    val (label, color) = when (role) {
        "ADMIN" -> "Administrador" to Color(0xFF7C3AED)
        "SUPERVISOR" -> "Supervisor" to Color(0xFFD97706)
        else -> "Cajero" to Color(0xFF059669)
    }
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun StatusBadge(isActive: Boolean) {
    val color = if (isActive) Color(0xFF059669) else Color(0xFF6B7280)
    val label = if (isActive) "Activo" else "Inactivo"
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
