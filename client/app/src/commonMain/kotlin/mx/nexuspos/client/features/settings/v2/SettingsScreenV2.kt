package mx.nexuspos.client.features.settings.v2

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import mx.nexuspos.client.features.settings.SettingsTab
import mx.nexuspos.client.features.settings.SettingsViewModel
import mx.nexuspos.client.ui.components.FeedbackMessageHost

private data class SettingsDestination(val tab: SettingsTab, val label: String, val icon: ImageVector)

private val storeDestinations = listOf(
    SettingsDestination(SettingsTab.STORE_TICKET, "Tienda y ticket", Icons.Default.Store),
    SettingsDestination(SettingsTab.POLICIES, "Políticas", Icons.Default.Policy),
    SettingsDestination(SettingsTab.USERS, "Usuarios", Icons.Default.Group),
    SettingsDestination(SettingsTab.DEVICES, "Dispositivos", Icons.Default.PhoneAndroid)
)
private val registerDestinations = listOf(
    SettingsDestination(SettingsTab.PRINTER, "Impresora y cajón", Icons.Default.Print),
    SettingsDestination(SettingsTab.SCALE, "Báscula", Icons.Default.Scale),
    SettingsDestination(SettingsTab.NETWORK, "Red y servidor", Icons.Default.Wifi)
)

@Composable
fun SettingsScreenV2(viewModel: SettingsViewModel, onBackToSales: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val selected = (storeDestinations + registerDestinations).first { it.tab == state.selectedTab }
    val stateHolder = rememberSaveableStateHolder()
    val canNavigate = !state.showUserDialog && !state.isSaving
    val navigate: (SettingsTab) -> Unit = {
        viewModel.clearBanners()
        viewModel.selectTab(it, reloadNetwork = false)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Ajustes", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onBackToSales, enabled = canNavigate) {
                Icon(Icons.Default.ArrowBack, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Volver a ventas")
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth >= 880.dp
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                if (wide) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.width(240.dp).fillMaxHeight()
                    ) {
                        Column(
                            Modifier.verticalScroll(rememberScrollState()).padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            SettingsNavGroup("Toda la tienda", storeDestinations, selected, canNavigate, navigate)
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            SettingsNavGroup("Esta caja", registerDestinations, selected, canNavigate, navigate)
                        }
                    }
                }

                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!wide) {
                        SettingsChoice(
                            "Sección",
                            selected.tab.name,
                            storeDestinations.map { it.tab.name to "${it.label} · Tienda" } +
                                registerDestinations.map { it.tab.name to "${it.label} · Esta caja" },
                            { navigate(SettingsTab.valueOf(it)) },
                            enabled = canNavigate
                        )
                    }

                    FeedbackMessageHost(
                        errorMessage = state.errorBanner,
                        successMessage = state.successBanner,
                        onDismissError = viewModel::clearBanners,
                        onDismissSuccess = viewModel::clearBanners
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.large
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(selected.label, style = MaterialTheme.typography.titleLarge)
                            stateHolder.SaveableStateProvider(selected.tab.name) {
                                when (selected.tab) {
                                    SettingsTab.STORE_TICKET, SettingsTab.POLICIES -> when {
                                        state.isLoading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                                        !state.settingsAvailable -> {
                                            Text("No se pudo cargar la configuración de la tienda.")
                                            OutlinedButton(onClick = viewModel::loadSettings) { Text("Reintentar") }
                                        }
                                        selected.tab == SettingsTab.STORE_TICKET -> StoreSettingsV2(state, viewModel)
                                        else -> PolicySettingsV2(state, viewModel)
                                    }
                                    SettingsTab.USERS -> UserSettingsV2(state, viewModel)
                                    SettingsTab.DEVICES -> MobileDevicesSettings(viewModel.apiClient)
                                    SettingsTab.PRINTER -> PrinterSettingsV2(state, viewModel)
                                    SettingsTab.SCALE -> ScaleSettingsV2(state, viewModel)
                                    SettingsTab.NETWORK -> NetworkSettingsV2(state, viewModel)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsNavGroup(
    title: String,
    destinations: List<SettingsDestination>,
    selected: SettingsDestination,
    enabled: Boolean,
    onSelect: (SettingsTab) -> Unit
) {
    Text(
        title,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    destinations.forEach { item ->
        NavigationDrawerItem(
            label = { Text(item.label) },
            icon = { Icon(item.icon, contentDescription = null) },
            selected = item == selected,
            onClick = { if (enabled && item != selected) onSelect(item.tab) }
        )
    }
}

@Composable
internal fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
internal fun SettingsToggle(
    label: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsChoice(
    label: String,
    value: String,
    choices: List<Pair<String, String>>,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = choices.firstOrNull { it.first == value }?.second ?: value,
            onValueChange = {},
            label = { Text(label) },
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded && enabled) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded && enabled, onDismissRequest = { expanded = false }) {
            choices.forEach { (key, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = {
                    expanded = false
                    onChange(key)
                })
            }
        }
    }
}

@Composable
internal fun SettingsField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    multiline: Boolean = false,
    error: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = !multiline,
        minLines = if (multiline) 2 else 1,
        maxLines = if (multiline) 4 else 1,
        enabled = enabled,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
internal fun SettingsSave(label: String, onSave: () -> Unit, enabled: Boolean = true, saving: Boolean = false) {
    HorizontalDivider()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Button(onSave, enabled = enabled && !saving) { Text(if (saving) "Guardando…" else label) }
    }
}
