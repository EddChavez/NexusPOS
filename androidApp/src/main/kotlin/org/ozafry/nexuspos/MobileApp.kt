package org.ozafry.nexuspos

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import mx.nexuspos.contracts.mobile.*
import mx.nexuspos.core.Money
import mx.nexuspos.core.ProductUnit
import mx.nexuspos.core.Quantity
import java.text.DateFormat
import java.util.Date

private val mobileLightColors = lightColorScheme(
    primary = Color(0xFF047857),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCFCE7),
    onPrimaryContainer = Color(0xFF166534),
    secondary = Color(0xFF0369A1),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0369A1),
    background = Color(0xFFF8F9FF),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFEFF4FF),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFF64748B),
    outlineVariant = Color(0xFFCBDBF5),
    error = Color(0xFFDC2626)
)

private val mobileDarkColors = darkColorScheme(
    primary = Color(0xFF10B981),
    onPrimary = Color(0xFF022C22),
    primaryContainer = Color(0xFF064E3B),
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondary = Color(0xFF38BDF8),
    onSecondary = Color(0xFF0B1326),
    secondaryContainer = Color(0xFF083344),
    onSecondaryContainer = Color(0xFFBAE6FD),
    background = Color(0xFF0B1326),
    surface = Color(0xFF131B2E),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF060E20),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF94A3B8),
    outlineVariant = Color(0xFF334155),
    error = Color(0xFFF87171)
)

@Composable
internal fun MobileApp(model: MobileModel, owner: LifecycleOwner) {
    val state by model.state.collectAsState()
    val colors = if (isSystemInDarkTheme()) mobileDarkColors else mobileLightColors
    val context = LocalContext.current
    val localNetworkPermission = "android.permission.ACCESS_LOCAL_NETWORK"
    var awaitingAccess by remember { mutableStateOf<(() -> Unit)?>(null) }
    var permissionError by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = awaitingAccess
        awaitingAccess = null
        permissionError = !granted
        if (granted) action?.invoke()
    }
    val requestNetwork: (() -> Unit) -> Unit = { action ->
        if (Build.VERSION.SDK_INT >= 37 && ContextCompat.checkSelfPermission(context, localNetworkPermission) != PackageManager.PERMISSION_GRANTED) {
            awaitingAccess = action
            permission.launch(localNetworkPermission)
        } else {
            permissionError = false
            action()
        }
    }

    MaterialTheme(colorScheme = colors) {
        Surface(Modifier.fillMaxSize()) {
            when {
                !state.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.user == null -> SignIn(state, model, requestNetwork, permissionError)
                else -> key(state.user) { MobileWorkspace(state, model, owner) }
            }
        }
    }
}

@Composable
private fun SignIn(state: MobileState, model: MobileModel, network: (() -> Unit) -> Unit, permissionError: Boolean) {
    var address by rememberSaveable { mutableStateOf("https://") }
    var name by rememberSaveable { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var forget by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(Modifier.widthIn(max = 460.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("NexusPOS Móvil", style = MaterialTheme.typography.headlineMedium)
            Text(if (state.address == null) "Vincula tu dispositivo desde Configuración → Dispositivos en una caja NexusPOS." else "Ingresa el PIN de encargado.")
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (permissionError) Text("Autoriza el acceso a la red local para consultar la tienda.", color = MaterialTheme.colorScheme.error)
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())

            if (state.address == null) {
                MobileInput("Dirección HTTPS", address, { address = it }, !state.loading, KeyboardType.Uri)
                MobileInput("Nombre del dispositivo", name, { name = it.take(80) }, !state.loading)
                MobileInput("Código de 8 dígitos", code, { code = it.digits(8) }, !state.loading, KeyboardType.Number)
                Button(onClick = {
                    val submitted = code
                    network { model.pair(address, submitted, name); code = "" }
                }, enabled = !state.loading && code.length == 8 && name.isNotBlank()) { Text("Vincular") }
            } else {
                Text(state.address, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(pin, { pin = it.digits(6) }, enabled = !state.loading, singleLine = true,
                    label = { Text("PIN") }, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation())
                Button(onClick = {
                    val submitted = pin
                    network { model.login(submitted); pin = "" }
                }, enabled = pin.length in setOf(4, 6) && !state.loading) { Text("Entrar") }
                TextButton(onClick = { forget = true }, enabled = !state.loading) { Text("Cambiar vinculación") }
            }
        }
    }
    if (forget) AlertDialog(
        onDismissRequest = { forget = false }, title = { Text("Cambiar vinculación") },
        text = { Text("Necesitarás un código nuevo para volver a vincular este dispositivo.") },
        confirmButton = { TextButton(onClick = { forget = false; pin = ""; model.forget() }) { Text("Cambiar") } },
        dismissButton = { TextButton(onClick = { forget = false }) { Text("Cancelar") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MobileWorkspace(state: MobileState, model: MobileModel, owner: LifecycleOwner) {
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val tabs = rememberSaveableStateHolder()
        val rail = maxWidth >= 600.dp
        val split = maxWidth >= 840.dp
        BackHandler(enabled = (state.selected == MobileTab.SHIFTS && state.shiftId != null) ||
            (state.selected == MobileTab.PRODUCTS && state.productId != null)) {
            if (state.selected == MobileTab.SHIFTS) model.selectShift(null) else model.selectProduct(null)
        }
        Column {
            TopAppBar(
                title = { Column { Text("NexusPOS"); Text(state.user.orEmpty(), style = MaterialTheme.typography.labelMedium) } },
                actions = {
                    TextButton(model::refresh, enabled = !state.loading) { Text("Actualizar") }
                    TextButton(model::lock) { Text("Bloquear") }
                }
            )
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.error) }
            if (state.offline) {
                Text(state.updatedAt?.let { "Sin conexión · Última respuesta ${time(it)}" } ?: "Sin conexión",
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.labelLarge)
            }
            Row(Modifier.weight(1f).fillMaxWidth()) {
                if (rail) NavigationRail { MobileTab.entries.forEach { tab ->
                    NavigationRailItem(state.selected == tab, { model.select(tab) }, icon = { Text(tab.symbol()) }, label = { Text(tab.label) })
                } }
                Box(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 16.dp)) {
                    tabs.SaveableStateProvider(state.selected.name) {
                        when (state.selected) {
                            MobileTab.HOME -> Overview(state.overview, state.updatedAt)
                            MobileTab.PRODUCTS -> Products(state, model, owner, split)
                            MobileTab.SHIFTS -> Shifts(state, model, split)
                        }
                    }
                }
            }
            if (!rail) NavigationBar { MobileTab.entries.forEach { tab ->
                NavigationBarItem(state.selected == tab, { model.select(tab) }, icon = { Text(tab.symbol()) }, label = { Text(tab.label) })
            } }
        }
    }
}

private fun MobileTab.symbol(): String = when (this) {
    MobileTab.HOME -> "⌂"
    MobileTab.PRODUCTS -> "▦"
    MobileTab.SHIFTS -> "◷"
}

@Composable
private fun Overview(data: MobileOverview?, updatedAt: Long?) {
    if (data == null) return
    LazyColumn(contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Ventas netas sin IVA · ${data.date}")
                    Text(Money(data.netSalesCents).format(), style = MaterialTheme.typography.headlineLarge)
                    updatedAt?.let { Text("Actualizado ${time(it)}", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        item { Text("Cajas", style = MaterialTheme.typography.titleLarge) }
        items(data.registers, key = { it.id }) { register ->
            ListItem(headlineContent = { Text(register.name) }, trailingContent = { Text(if (register.open) "Abierta" else "Cerrada") })
        }
    }
}

@Composable
private fun Products(state: MobileState, model: MobileModel, owner: LifecycleOwner, split: Boolean) {
    val context = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }
    var scanning by remember { mutableStateOf(false) }
    var cameraDenied by remember { mutableStateOf(false) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        scanning = granted
        cameraDenied = !granted
    }
    val selected = state.products?.firstOrNull { it.id == state.productId }
    if (scanning) BarcodeScanner(owner, onClose = { scanning = false }) { barcode ->
        scanning = false
        text = barcode
        model.search(barcode, barcode = true)
    }
    if (!split && selected != null) {
        ProductDetail(selected) { model.selectProduct(null) }
        return
    }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(if (split) 0.48f else 1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MobileInput("Nombre, SKU o código", text, { text = it.take(100) }, !state.loading)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { model.search(text) }, enabled = !state.loading && text.isNotBlank()) { Text("Buscar") }
                OutlinedButton(onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) scanning = true
                    else camera.launch(Manifest.permission.CAMERA)
                }, enabled = !state.loading) { Text("Escanear") }
            }
            if (cameraDenied) Text("Cámara sin permiso. Puedes capturar el código manualmente.")
            if (state.products?.isEmpty() == true) Text("Sin resultados.")
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.products.orEmpty(), key = { it.id }) { product ->
                    OutlinedCard(onClick = { model.selectProduct(product.id) }, modifier = Modifier.fillMaxWidth()) {
                        ListItem(headlineContent = { Text(product.name) }, supportingContent = { Text(product.sku) })
                    }
                }
            }
        }
        if (split) Box(Modifier.weight(0.52f).fillMaxHeight()) {
            if (selected == null) Text("Selecciona un producto para ver sus precios.")
            else ProductDetail(selected) { model.selectProduct(null) }
        }
    }
}

@Composable
private fun ProductDetail(product: MobileProduct, back: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            TextButton(back) { Text("Volver a productos") }
            Text(product.name, style = MaterialTheme.typography.titleLarge)
            Text(product.sku)
            Text(product.stockMillis?.let { "Existencias · ${Quantity(it).format(ProductUnit.fromCode(product.unit))}" }
                ?: "Sin existencias propias")
        }
        val presentations = product.presentations.sortedByDescending { it.id == product.matchedPresentationId }
        items(presentations, key = { it.id }) { item ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (item.id == product.matchedPresentationId) Text("Presentación del código", style = MaterialTheme.typography.labelLarge)
                    Text(item.name, style = MaterialTheme.typography.titleMedium)
                    Text(item.barcode)
                    Text(Money(item.priceCents).format(), style = MaterialTheme.typography.headlineSmall)
                    item.priceTiers.sortedBy { it.minimumQuantityMillis }.forEach { tier ->
                        val count = Quantity(tier.minimumQuantityMillis).format().substringBefore(' ')
                        Text("Desde $count · ${Money(tier.unitPriceCents).format()} por presentación")
                    }
                }
            }
        }
    }
}

@Composable
private fun Shifts(state: MobileState, model: MobileModel, split: Boolean) {
    var date by rememberSaveable { mutableStateOf<String?>(null) }
    var register by rememberSaveable { mutableStateOf<String?>(null) }
    var chooseRegister by remember { mutableStateOf(false) }
    val selected = state.sales
    if (!split && state.shiftId != null) {
        ShiftDetail(state.shiftId, selected, state.loading, model)
        return
    }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(if (split) 0.48f else 1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MobileInput("Fecha AAAA-MM-DD", date ?: state.shifts?.date.orEmpty(), { date = it.take(10) }, !state.loading)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedButton(onClick = { chooseRegister = true }, enabled = !state.loading) {
                        Text(state.shifts?.registers?.firstOrNull { it.id == register }?.name ?: "Todas las cajas")
                    }
                    DropdownMenu(chooseRegister, onDismissRequest = { chooseRegister = false }) {
                        DropdownMenuItem({ Text("Todas las cajas") }, onClick = { register = null; chooseRegister = false })
                        state.shifts?.registers.orEmpty().forEach {
                            DropdownMenuItem({ Text(it.name) }, onClick = { register = it.id; chooseRegister = false })
                        }
                    }
                }
                Button(onClick = { model.filterShifts(date ?: state.shifts?.date, register) }, enabled = !state.loading) { Text("Consultar") }
            }
            if (state.shifts?.shifts?.isEmpty() == true) Text("No hay turnos para esta fecha y caja.")
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.shifts?.shifts.orEmpty(), key = { it.id }) { shift ->
                    OutlinedCard(onClick = { model.selectShift(shift.id) }, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) {
                        ListItem(headlineContent = { Text(shift.registerName) },
                            supportingContent = { Text("${shift.cashierName} · ${time(shift.openedAt)}") },
                            trailingContent = { Text(if (shift.closedAt == null) "Abierto" else "Cerrado") })
                    }
                }
            }
        }
        if (split) Box(Modifier.weight(0.52f).fillMaxHeight()) {
            if (state.shiftId == null) Text("Selecciona un turno para ver sus ventas.")
            else ShiftDetail(state.shiftId, selected, state.loading, model)
        }
    }
}

@Composable
private fun ShiftDetail(id: String, data: MobileShiftSales?, loading: Boolean, model: MobileModel) {
    var folio by rememberSaveable(id) { mutableStateOf("") }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TextButton(onClick = { model.selectShift(null) }, enabled = !loading) { Text("Volver a turnos") }
        data?.shift?.let { shift ->
            Text(shift.registerName, style = MaterialTheme.typography.titleLarge)
            Text("Apertura · ${time(shift.openedAt)}")
            shift.closedAt?.let {
                Text("Cierre · ${time(it)}")
                Text("Esperado ${amount(shift.expectedCents)} · Contado ${amount(shift.countedCents)}")
                Text("Diferencia ${amount(shift.differenceCents)}")
            }
        }
        MobileInput("Folio de venta", folio, { folio = it.digits(19) }, !loading, KeyboardType.Number)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val parsed = folio.toLongOrNull()?.takeIf { it > 0 }
            Button(onClick = { model.searchFolio(parsed) }, enabled = !loading && parsed != null) { Text("Buscar folio") }
            TextButton(onClick = { folio = ""; model.searchFolio(null) }, enabled = !loading) { Text("Ver todas") }
        }
        if (data?.sales?.isEmpty() == true) Text("No hay ventas para esta consulta.")
        LazyColumn(Modifier.weight(1f)) {
            items(data?.sales.orEmpty(), key = { it.folio }) { sale ->
                ListItem(headlineContent = { Text("Folio #${sale.folio}") },
                    supportingContent = { Text(when (sale.status) {
                        "COMPLETED" -> "Completada"
                        "CANCELLED" -> "Cancelada"
                        "REFUNDED" -> "Devuelta"
                        "PARTIALLY_REFUNDED" -> "Devolución parcial"
                        else -> sale.status
                    }) }, trailingContent = { Text(Money(sale.totalCents).format()) })
            }
            if (data?.nextBeforeFolio != null) item {
                OutlinedButton(model::moreSales, enabled = !loading) { Text("Cargar más") }
            }
        }
    }
}

@Composable
private fun MobileInput(label: String, value: String, edit: (String) -> Unit, enabled: Boolean, type: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(value, edit, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
        enabled = enabled, keyboardOptions = KeyboardOptions(keyboardType = type))
}

private fun String.digits(max: Int) = filter { it in '0'..'9' }.take(max)
private fun amount(cents: Long?) = cents?.let { Money(it).format() } ?: "Sin registro"
private fun time(epoch: Long): String = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(epoch))
