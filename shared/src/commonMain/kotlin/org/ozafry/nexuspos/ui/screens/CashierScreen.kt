package org.ozafry.nexuspos.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import org.ozafry.nexuspos.currentEpochMillis
import org.ozafry.nexuspos.domain.CartCalculation
import org.ozafry.nexuspos.domain.CartItem
import org.ozafry.nexuspos.domain.POSCheckoutService
import org.ozafry.nexuspos.models.*
import org.ozafry.nexuspos.repository.OfflineSaleRepository
import org.ozafry.nexuspos.sync.SyncEngine
import org.ozafry.nexuspos.ui.theme.AmberWarning
import org.ozafry.nexuspos.ui.theme.EmeraldSuccess
import org.ozafry.nexuspos.ui.theme.RoseDanger
import kotlin.math.round

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashierScreen(
    repository: OfflineSaleRepository,
    syncEngine: SyncEngine,
    registerId: String = "REG-01",
    onSwitchToSupervisor: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    var products by remember { mutableStateOf(repository.getProducts()) }
    val categories = remember { repository.getCategories() }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var cartItems by remember { mutableStateOf(listOf<CartItem>()) }
    var currentShift by remember { mutableStateOf(repository.getCurrentShift()) }
    var pendingSyncCount by remember { mutableStateOf(repository.getPendingSyncEvents().size) }
    var isSyncing by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf<String?>(null) }

    var showCheckoutDialog by remember { mutableStateOf(false) }
    var showShiftDialog by remember { mutableStateOf(false) }
    var editingQuantityItem by remember { mutableStateOf<CartItem?>(null) }
    var successNotification by remember { mutableStateOf<String?>(null) }

    fun refreshState() {
        products = if (searchQuery.isBlank()) {
            repository.getProducts()
        } else {
            repository.searchProducts(searchQuery)
        }
        currentShift = repository.getCurrentShift()
        pendingSyncCount = repository.getPendingSyncEvents().size
    }

    val filteredProducts = remember(products, selectedCategoryId, searchQuery) {
        products.filter { p ->
            (selectedCategoryId == null || p.categoryId == selectedCategoryId) &&
            (searchQuery.isBlank() || p.name.contains(searchQuery, ignoreCase = true) || p.sku.contains(searchQuery, ignoreCase = true) || (p.barcode?.contains(searchQuery) == true))
        }
    }

    val cartCalculation = remember(cartItems) {
        POSCheckoutService.calculateCart(cartItems)
    }

    fun addToCart(product: ProductDto, initialQuantity: Double = 1.0) {
        val existingIndex = cartItems.indexOfFirst { it.product.id == product.id }
        cartItems = if (existingIndex >= 0) {
            val item = cartItems[existingIndex]
            val updated = item.copy(quantity = round((item.quantity + initialQuantity) * 1000.0) / 1000.0)
            cartItems.toMutableList().apply { set(existingIndex, updated) }
        } else {
            cartItems + CartItem(product = product, quantity = initialQuantity)
        }
    }

    fun handleBarcodeSubmit(query: String) {
        val clean = query.trim()
        if (clean.isBlank()) return
        val exactMatch = repository.getProductByBarcodeOrSku(clean)
        if (exactMatch != null) {
            addToCart(exactMatch)
            searchQuery = ""
        }
    }

    fun updateQuantity(productId: String, delta: Double) {
        val existingIndex = cartItems.indexOfFirst { it.product.id == productId }
        if (existingIndex >= 0) {
            val item = cartItems[existingIndex]
            val step = if (item.product.unit == "KG") 0.25 else 1.0
            val newQty = round((item.quantity + (delta * step)) * 1000.0) / 1000.0
            cartItems = if (newQty > 0.0) {
                cartItems.toMutableList().apply { set(existingIndex, item.copy(quantity = newQty)) }
            } else {
                cartItems.toMutableList().apply { removeAt(existingIndex) }
            }
        }
    }

    fun setDirectQuantity(productId: String, newQty: Double) {
        val existingIndex = cartItems.indexOfFirst { it.product.id == productId }
        if (existingIndex >= 0) {
            cartItems = if (newQty > 0.0) {
                val item = cartItems[existingIndex]
                cartItems.toMutableList().apply { set(existingIndex, item.copy(quantity = newQty)) }
            } else {
                cartItems.toMutableList().apply { removeAt(existingIndex) }
            }
        }
    }

    fun removeCartItem(productId: String) {
        cartItems = cartItems.filterNot { it.product.id == productId }
    }

    fun triggerSync() {
        coroutineScope.launch {
            isSyncing = true
            syncMessage = "Sincronizando con servidor central..."
            val result = syncEngine.syncNow()
            isSyncing = false
            syncMessage = if (result.isSuccess) {
                "Sincronizado (${result.pushedEventsCount} eventos enviados, ${result.pulledProductsCount} productos actualizados)"
            } else {
                "Modo Offline: ${result.errorMessage ?: "Servidor no disponible"}"
            }
            refreshState()
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Logo & Register info
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("N", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("NexusPOS", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        "Abarrotes & Miscelánea",
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            Text("Caja: $registerId | ${currentShift?.cashierName ?: "Sin Turno Activo"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // Center: Shift Badge & Sync Status
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilledTonalButton(
                            onClick = { showShiftDialog = true },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (currentShift != null) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                if (currentShift != null) "● Turno Activo (#${currentShift?.id?.takeLast(4)})" else "○ Abrir Turno de Caja",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Button(
                            onClick = { triggerSync() },
                            enabled = !isSyncing,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (pendingSyncCount > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (pendingSyncCount > 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                                Text("Sync...", fontSize = 12.sp)
                            } else {
                                Text(
                                    if (pendingSyncCount > 0) "↻ Sync ($pendingSyncCount)" else "✓ Sincronizado",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Right: Supervisor mode toggle
                    OutlinedButton(
                        onClick = onSwitchToSupervisor,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Supervisión ↗", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Notification banner
            AnimatedVisibility(visible = successNotification != null || syncMessage != null) {
                val msg = successNotification ?: syncMessage ?: ""
                val isSuccess = successNotification != null
                Surface(
                    color = if (isSuccess) EmeraldSuccess else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            msg,
                            color = if (isSuccess) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        TextButton(
                            onClick = {
                                successNotification = null
                                syncMessage = null
                            },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("✕", color = if (isSuccess) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Workspace Layout
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Left Panel: Product Catalog & Search
                Column(
                    modifier = Modifier
                        .weight(1.3f)
                        .fillMaxHeight()
                ) {
                    // Search bar with barcode support
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Escanear código de barras (EAN-13), SKU o buscar producto...") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { handleBarcodeSubmit(searchQuery) }),
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (searchQuery.isNotEmpty()) {
                                    TextButton(onClick = { searchQuery = "" }) {
                                        Text("✕")
                                    }
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(Modifier.height(8.dp))

                    // Category filter chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedCategoryId == null,
                            onClick = { selectedCategoryId = null },
                            label = { Text("Todos (${products.size})") }
                        )
                        for (cat in categories) {
                            FilterChip(
                                selected = selectedCategoryId == cat.id,
                                onClick = { selectedCategoryId = if (selectedCategoryId == cat.id) null else cat.id },
                                label = { Text(cat.name) }
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Products Grid
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 165.dp),
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredProducts) { product ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (product.unit == "KG") {
                                            // Granel: abre diálogo de peso o agrega 1 kg inicial
                                            addToCart(product, 1.0)
                                        } else {
                                            addToCart(product, 1.0)
                                        }
                                    },
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(
                                            product.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )

                                        if (product.unit == "KG") {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = EmeraldSuccess.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    "⚖ KG",
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = EmeraldSuccess
                                                )
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "${product.sku} | ${product.barcode ?: ""}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "$${product.price} / ${product.unit}",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (product.stockQuantity < 10) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Text(
                                                "Stock: ${product.stockQuantity.toInt()}",
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Right Panel: Active Ticket
                Surface(
                    modifier = Modifier
                        .weight(1.0f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // Cart Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Ticket de Venta", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            if (cartItems.isNotEmpty()) {
                                TextButton(
                                    onClick = { cartItems = emptyList() },
                                    colors = ButtonDefaults.textButtonColors(contentColor = RoseDanger)
                                ) {
                                    Text("Limpiar", fontSize = 12.sp)
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        // Cart Items
                        if (cartItems.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("🛍️", fontSize = 36.sp)
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "Ticket vacío",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "Escanee código o seleccione producto",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(cartItems) { item ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(item.product.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                                Text("$${item.unitPrice} / ${item.product.unit}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }

                                            // Quantity Controls & Pill
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                FilledTonalIconButton(
                                                    onClick = { updateQuantity(item.product.id, -1.0) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Text("-", fontWeight = FontWeight.Bold)
                                                }

                                                // Clickable quantity pill to enter custom weight / amount
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.surface,
                                                    modifier = Modifier.clickable { editingQuantityItem = item }
                                                ) {
                                                    val qtyFormatted = if (item.product.unit == "KG") "${item.quantity} kg" else "${item.quantity.toInt()} pza"
                                                    Text(
                                                        qtyFormatted,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                    )
                                                }

                                                FilledTonalIconButton(
                                                    onClick = { updateQuantity(item.product.id, 1.0) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Text("+", fontWeight = FontWeight.Bold)
                                                }
                                            }

                                            Spacer(Modifier.width(10.dp))
                                            Text(
                                                "$${item.total}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )

                                            IconButton(
                                                onClick = { removeCartItem(item.product.id) },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Text("✕", color = RoseDanger, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        // Totals summary
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Subtotal:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("$${cartCalculation.subtotal}", fontSize = 13.sp)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("IVA:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("$${cartCalculation.taxTotal}", fontSize = 13.sp)
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("TOTAL A COBRAR:", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                Text(
                                    "$${cartCalculation.total}",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 22.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Checkout Button
                        Button(
                            onClick = {
                                if (currentShift == null) {
                                    showShiftDialog = true
                                } else {
                                    showCheckoutDialog = true
                                }
                            },
                            enabled = cartItems.isNotEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess)
                        ) {
                            Text(
                                if (currentShift == null) "Abrir Turno para Cobrar" else "Cobrar [F12]  •  $${cartCalculation.total}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal para editar cantidad / peso a granel
    if (editingQuantityItem != null) {
        val item = editingQuantityItem!!
        WeightQuantityDialog(
            item = item,
            onDismiss = { editingQuantityItem = null },
            onConfirm = { newQty ->
                setDirectQuantity(item.product.id, newQty)
                editingQuantityItem = null
            }
        )
    }

    // Checkout Modal Dialog
    if (showCheckoutDialog) {
        CheckoutDialog(
            calculation = cartCalculation,
            onDismiss = { showCheckoutDialog = false },
            onConfirmPayment = { method, amountPaid, change ->
                val shift = currentShift ?: return@CheckoutDialog
                val saleId = "sale-${currentEpochMillis()}"
                val now = currentEpochMillis()

                val saleDto = SaleDto(
                    id = saleId,
                    shiftId = shift.id,
                    registerId = registerId,
                    cashierName = shift.cashierName,
                    customerName = "Cliente Mostrador",
                    subtotal = cartCalculation.subtotal,
                    taxTotal = cartCalculation.taxTotal,
                    discountTotal = cartCalculation.discountTotal,
                    total = cartCalculation.total,
                    paymentMethod = method,
                    amountPaid = amountPaid,
                    changeGiven = change,
                    status = "COMPLETED",
                    createdAt = now,
                    items = cartItems.mapIndexed { idx, it ->
                        it.toSaleItemDto(saleId, "$saleId-item-$idx")
                    }
                )

                // Guardado atómico offline-first
                repository.createSale(saleDto)
                cartItems = emptyList()
                showCheckoutDialog = false
                successNotification = "✓ Venta ${saleId.takeLast(6)} registrada. Cambio: $$change"

                refreshState()
                triggerSync()
            }
        )
    }

    // Shift Modal Dialog (Apertura y Cierre / Arqueo de Caja)
    if (showShiftDialog) {
        ShiftDialog(
            currentShift = currentShift,
            registerId = registerId,
            onDismiss = { showShiftDialog = false },
            onOpenShift = { cashier, initialCash ->
                val newShiftId = "shift-${currentEpochMillis()}"
                repository.openShift(newShiftId, registerId, cashier, initialCash)
                showShiftDialog = false
                successNotification = "Turno abierto para $cashier con fondo de $$initialCash"
                refreshState()
                triggerSync()
            },
            onCloseShift = { actualCash, notes ->
                currentShift?.let { shift ->
                    repository.closeShift(shift.id, actualCash, notes)
                    showShiftDialog = false
                    successNotification = "Turno cerrado. Corte de caja registrado."
                    refreshState()
                    triggerSync()
                }
            }
        )
    }
}

@Composable
fun WeightQuantityDialog(
    item: CartItem,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var quantityText by remember { mutableStateOf(item.quantity.toString()) }
    val isGranel = item.product.unit == "KG"

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    if (isGranel) "⚖ Pesar / Cantidad a Granel" else "Modificar Cantidad",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    item.product.name,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Precio: $${item.product.price} / ${item.product.unit}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it },
                    label = { Text(if (isGranel) "Peso en Kilogramos (kg)" else "Cantidad (piezas)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (isGranel) {
                    Spacer(Modifier.height(10.dp))
                    Text("Accesos rápidos de peso:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(0.250, 0.500, 0.750, 1.000, 2.000).forEach { weight ->
                            OutlinedButton(
                                onClick = { quantityText = weight.toString() },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("${weight}k", fontSize = 11.sp)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Cancelar")
                    }
                    Button(
                        onClick = {
                            val parsed = quantityText.toDoubleOrNull() ?: item.quantity
                            onConfirm(parsed)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Aplicar")
                    }
                }
            }
        }
    }
}

@Composable
fun CheckoutDialog(
    calculation: CartCalculation,
    onDismiss: () -> Unit,
    onConfirmPayment: (method: String, amountPaid: Double, change: Double) -> Unit
) {
    var selectedMethod by remember { mutableStateOf("CASH") }
    var amountPaidText by remember { mutableStateOf(calculation.total.toString()) }

    val amountPaid = amountPaidText.toDoubleOrNull() ?: 0.0
    val change = remember(amountPaid, calculation.total) {
        POSCheckoutService.calculateChange(calculation.total, amountPaid)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Cobro de Venta", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(Modifier.height(12.dp))

                Text(
                    "$${calculation.total}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 32.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text("Total a Pagar", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(Modifier.height(16.dp))

                // Payment Methods
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("CASH" to "Efectivo", "CARD" to "Tarjeta", "TRANSFER" to "Transf.").forEach { (id, label) ->
                        FilterChip(
                            selected = selectedMethod == id,
                            onClick = {
                                selectedMethod = id
                                if (id != "CASH") amountPaidText = calculation.total.toString()
                            },
                            label = { Text(label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (selectedMethod == "CASH") {
                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = amountPaidText,
                        onValueChange = { amountPaidText = it },
                        label = { Text("Efectivo Recibido ($)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(8.dp))

                    // Billetes y monedas habituales en tienda de abarrotes
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(calculation.total, 20.0, 50.0, 100.0, 200.0, 500.0).distinct().forEach { cash ->
                            OutlinedButton(
                                onClick = { amountPaidText = cash.toString() },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("$$cash", fontSize = 11.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Change Display
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Cambio a entregar:", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(
                                "$$change",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 24.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancelar")
                    }

                    Button(
                        onClick = {
                            if (amountPaid >= calculation.total || selectedMethod != "CASH") {
                                onConfirmPayment(selectedMethod, amountPaid, change)
                            }
                        },
                        enabled = (selectedMethod != "CASH" || amountPaid >= calculation.total),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess)
                    ) {
                        Text("Confirmar Venta")
                    }
                }
            }
        }
    }
}

@Composable
fun ShiftDialog(
    currentShift: CashShiftDto?,
    registerId: String,
    onDismiss: () -> Unit,
    onOpenShift: (cashier: String, initialCash: Double) -> Unit,
    onCloseShift: (actualCash: Double, notes: String?) -> Unit
) {
    var cashierName by remember { mutableStateOf("Don Pepe") }
    var initialCashText by remember { mutableStateOf("500.0") }
    var actualCashText by remember { mutableStateOf("") }
    var notesText by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                if (currentShift == null) {
                    Text("Apertura de Turno de Tienda", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = cashierName,
                        onValueChange = { cashierName = it },
                        label = { Text("Nombre del Encargado / Cajero") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = initialCashText,
                        onValueChange = { initialCashText = it },
                        label = { Text("Fondo Inicial para Cambio ($)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(20.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                            Text("Cancelar")
                        }
                        Button(
                            onClick = {
                                val cash = initialCashText.toDoubleOrNull() ?: 0.0
                                onOpenShift(cashierName, cash)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Abrir Turno")
                        }
                    }
                } else {
                    Text("Cierre de Turno y Arqueo (Corte X/Z)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(12.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Encargado: ${currentShift.cashierName}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Fondo Inicial: $${currentShift.initialCash}", fontSize = 12.sp)
                            Text("Ventas Efectivo: $${currentShift.cashSales}", fontSize = 12.sp)
                            Text("Ventas Tarjeta: $${currentShift.cardSales}", fontSize = 12.sp)
                            Text("Efectivo Esperado en Caja: $${currentShift.expectedCash}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = actualCashText,
                        onValueChange = { actualCashText = it },
                        label = { Text("Efectivo Contado en Caja ($)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = notesText,
                        onValueChange = { notesText = it },
                        label = { Text("Observaciones del Corte") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(20.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                            Text("Volver")
                        }
                        Button(
                            onClick = {
                                val actual = actualCashText.toDoubleOrNull() ?: currentShift.expectedCash
                                onCloseShift(actual, notesText.ifBlank { null })
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RoseDanger),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cerrar Turno")
                        }
                    }
                }
            }
        }
    }
}
