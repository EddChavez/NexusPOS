package mx.nexuspos.client.features.products

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.nexuspos.client.theme.NexusTheme
import mx.nexuspos.contracts.catalog.DepartmentDto
import mx.nexuspos.contracts.catalog.ProductDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductFormDialog(
    productToEdit: ProductDto?,
    departments: List<DepartmentDto>,
    onCreateDepartment: ((String, (DepartmentDto) -> Unit) -> Unit)? = null,
    onSave: (
        sku: String,
        name: String,
        barcode: String,
        departmentId: String?,
        baseUnit: String,
        costCents: Long,
        priceCents: Long,
        taxRate: Double,
        usesInventory: Boolean,
        initialStockMillis: Long,
        minStockMillis: Long,
        maxStockMillis: Long,
        isActive: Boolean
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val isEdit = productToEdit != null

    var sku by remember { mutableStateOf(productToEdit?.sku ?: "") }
    var name by remember { mutableStateOf(productToEdit?.name ?: "") }
    var barcode by remember {
        mutableStateOf(productToEdit?.presentations?.firstOrNull()?.barcode ?: "")
    }
    var departmentId by remember { mutableStateOf(productToEdit?.departmentId) }
    var baseUnit by remember { mutableStateOf(productToEdit?.baseUnit ?: "PZA") }

    var costPesos by remember {
        mutableStateOf(
            if (productToEdit != null && productToEdit.costCents > 0) {
                (productToEdit.costCents / 100.0).toString()
            } else ""
        )
    }
    var pricePesos by remember {
        mutableStateOf(
            if (productToEdit != null && productToEdit.presentations.isNotEmpty()) {
                (productToEdit.presentations.first().priceCents / 100.0).toString()
            } else ""
        )
    }

    var taxRate by remember { mutableStateOf(productToEdit?.taxRate ?: 0.0) }
    var usesInventory by remember { mutableStateOf(productToEdit?.usesInventory ?: true) }

    var initialStock by remember { mutableStateOf("0") }
    var minStock by remember {
        mutableStateOf(
            if (productToEdit != null && productToEdit.minStockMillis > 0) {
                (productToEdit.minStockMillis / 1000.0).toString()
            } else "0"
        )
    }
    var maxStock by remember {
        mutableStateOf(
            if (productToEdit != null && productToEdit.maxStockMillis > 0) {
                (productToEdit.maxStockMillis / 1000.0).toString()
            } else "0"
        )
    }
    var isActive by remember { mutableStateOf(productToEdit?.isActive ?: true) }

    var departmentDropdownExpanded by remember { mutableStateOf(false) }
    var showQuickCreateDeptDialog by remember { mutableStateOf(false) }
    var quickDeptName by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    val selectedDepartmentName = remember(departmentId, departments) {
        if (departmentId == null) "General"
        else departments.firstOrNull { it.id == departmentId }?.name ?: "General"
    }

    if (showQuickCreateDeptDialog && onCreateDepartment != null) {
        AlertDialog(
            onDismissRequest = { showQuickCreateDeptDialog = false },
            title = {
                Text(
                    text = "Nuevo Departamento",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = NexusTheme.colors.textPrimary
                )
            },
            text = {
                OutlinedTextField(
                    value = quickDeptName,
                    onValueChange = { quickDeptName = it },
                    label = { Text("Nombre del Departamento") },
                    placeholder = { Text("Ej. Panadería, Farmacia, Mascotas...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = quickDeptName.trim()
                        if (trimmed.isNotBlank()) {
                            onCreateDepartment(trimmed) { created ->
                                departmentId = created.id
                            }
                            showQuickCreateDeptDialog = false
                        }
                    },
                    enabled = quickDeptName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = NexusTheme.colors.actionPrimary)
                ) {
                    Text("Crear y Asignar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showQuickCreateDeptDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (productToEdit != null) "Editar Producto (${productToEdit.sku})" else "Registrar Nuevo Producto",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = NexusTheme.colors.textPrimary
                )
                if (productToEdit != null && productToEdit.sku.isNotBlank()) {
                    Surface(
                        color = NexusTheme.colors.accentLight,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, NexusTheme.colors.accent)
                    ) {
                        Text(
                            text = productToEdit.sku,
                            color = NexusTheme.colors.accent,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(min = 540.dp, max = 640.dp)
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (validationError != null) {
                    Surface(
                        color = NexusTheme.colors.dangerBg,
                        border = BorderStroke(1.dp, NexusTheme.colors.dangerBorder),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = validationError ?: "",
                            color = NexusTheme.colors.dangerText,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                // 1. Identificación y Catálogo
                Text(
                    text = "1. Identificación del Producto",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = NexusTheme.colors.accent
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = barcode,
                        onValueChange = {
                            barcode = it
                            validationError = null
                        },
                        label = { Text("Código de Barras *") },
                        placeholder = { Text("Ej. 7501000123456") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = sku,
                        onValueChange = { sku = it.uppercase() },
                        label = { Text(if (isEdit) "SKU / Clave" else "SKU (Opcional)") },
                        placeholder = { Text("Ej. PRD-0001") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        validationError = null
                    },
                    label = { Text("Nombre / Descripción del Producto *") },
                    placeholder = { Text("Ej. Leche Lala Entera 1 Litro") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Departamento selector
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = selectedDepartmentName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Departamento / Categoría") },
                            trailingIcon = {
                                IconButton(onClick = { departmentDropdownExpanded = true }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Seleccionar")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { departmentDropdownExpanded = true }
                        )
                        DropdownMenu(
                            expanded = departmentDropdownExpanded,
                            onDismissRequest = { departmentDropdownExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("General", fontWeight = FontWeight.Medium) },
                                onClick = {
                                    departmentId = null
                                    departmentDropdownExpanded = false
                                }
                            )
                            departments.forEach { dept ->
                                DropdownMenuItem(
                                    text = { Text(dept.name) },
                                    onClick = {
                                        departmentId = dept.id
                                        departmentDropdownExpanded = false
                                    }
                                )
                            }
                            if (onCreateDepartment != null) {
                                HorizontalDivider(color = NexusTheme.colors.borderSubtle)
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Add,
                                                contentDescription = null,
                                                tint = NexusTheme.colors.accent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                "Crear nuevo departamento...",
                                                color = NexusTheme.colors.accent,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    },
                                    onClick = {
                                        departmentDropdownExpanded = false
                                        quickDeptName = ""
                                        showQuickCreateDeptDialog = true
                                    }
                                )
                            }
                        }
                    }

                    // Unidad de medida selector
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Unidad de Medida",
                            style = MaterialTheme.typography.bodySmall,
                            color = NexusTheme.colors.textSecondary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("PZA" to "Pieza", "KG" to "Kilo", "LT" to "Litro").forEach { (code, label) ->
                                val isSelected = baseUnit == code
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { baseUnit = code },
                                    label = { Text(label, fontSize = 12.sp) }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = NexusTheme.colors.borderSubtle, modifier = Modifier.padding(vertical = 2.dp))

                // 2. Precios y Costos
                Text(
                    text = "2. Precios e Impuestos",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = NexusTheme.colors.accent
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = pricePesos,
                        onValueChange = {
                            pricePesos = it
                            validationError = null
                        },
                        label = { Text("Precio de Venta ($ MXN) *") },
                        prefix = { Text("$ ") },
                        placeholder = { Text("0.00") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = costPesos,
                        onValueChange = { costPesos = it },
                        label = { Text("Costo de Compra ($ MXN)") },
                        prefix = { Text("$ ") },
                        placeholder = { Text("0.00") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Impuesto (IVA):",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = NexusTheme.colors.textPrimary
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = taxRate == 0.0,
                            onClick = { taxRate = 0.0 }
                        )
                        Text(
                            text = "Exento (0%)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NexusTheme.colors.textPrimary,
                            modifier = Modifier.padding(end = 16.dp)
                        )

                        RadioButton(
                            selected = taxRate == 0.16,
                            onClick = { taxRate = 0.16 }
                        )
                        Text(
                            text = "IVA General (16%)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NexusTheme.colors.textPrimary
                        )
                    }
                }

                HorizontalDivider(color = NexusTheme.colors.borderSubtle, modifier = Modifier.padding(vertical = 2.dp))

                // 3. Inventario y Existencias
                Text(
                    text = "3. Control de Existencias e Inventario",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = NexusTheme.colors.accent
                )

                Surface(
                    color = NexusTheme.colors.bgSurfaceSubtle,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Rastrear inventario de este producto",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = NexusTheme.colors.textPrimary
                                )
                                Text(
                                    text = "Descuenta existencias en cada venta y alerta sobre stock bajo",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NexusTheme.colors.textSecondary
                                )
                            }
                            Switch(
                                checked = usesInventory,
                                onCheckedChange = { usesInventory = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = NexusTheme.colors.actionPrimary)
                            )
                        }

                        if (usesInventory) {
                            HorizontalDivider(color = NexusTheme.colors.borderSubtle)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (!isEdit) {
                                    OutlinedTextField(
                                        value = initialStock,
                                        onValueChange = { initialStock = it },
                                        label = { Text("Stock Inicial ($baseUnit)") },
                                        placeholder = { Text("0") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                } else {
                                    val currentQty = (productToEdit.currentStockMillis / 1000.0)
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = "Stock Actual",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = NexusTheme.colors.textSecondary
                                        )
                                        Text(
                                            text = "$currentQty $baseUnit",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (productToEdit.currentStockMillis <= productToEdit.minStockMillis) {
                                                NexusTheme.colors.dangerText
                                            } else {
                                                NexusTheme.colors.textPrimary
                                            }
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = minStock,
                                    onValueChange = { minStock = it },
                                    label = { Text("Stock Mínimo ($baseUnit)") },
                                    placeholder = { Text("0") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )

                                OutlinedTextField(
                                    value = maxStock,
                                    onValueChange = { maxStock = it },
                                    label = { Text("Stock Máximo ($baseUnit)") },
                                    placeholder = { Text("0") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // 4. Estado de activación (solo en edición)
                if (isEdit) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Producto Activo en Catálogo",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = NexusTheme.colors.textPrimary
                            )
                            Text(
                                text = "Los productos inactivos no aparecen en búsqueda de venta rápida",
                                style = MaterialTheme.typography.bodySmall,
                                color = NexusTheme.colors.textSecondary
                            )
                        }
                        Switch(
                            checked = isActive,
                            onCheckedChange = { isActive = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = NexusTheme.colors.actionPrimary)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        validationError = "El nombre del producto es obligatorio"
                        return@Button
                    }
                    if (barcode.isBlank()) {
                        validationError = "El código de barras es obligatorio"
                        return@Button
                    }
                    val parsedPrice = pricePesos.toDoubleOrNull()
                    if (parsedPrice == null || parsedPrice < 0.0) {
                        validationError = "Ingresa un precio de venta válido mayor o igual a 0"
                        return@Button
                    }
                    val parsedCost = if (costPesos.isNotBlank()) {
                        val c = costPesos.toDoubleOrNull()
                        if (c == null || c < 0.0) {
                            validationError = "El costo ingresado no es un número válido"
                            return@Button
                        }
                        c
                    } else 0.0

                    val parsedInitialStock = if (!isEdit && usesInventory) {
                        val s = initialStock.toDoubleOrNull()
                        if (s == null || s < 0.0) {
                            validationError = "El stock inicial no es válido"
                            return@Button
                        }
                        s
                    } else 0.0

                    val parsedMinStock = if (usesInventory && minStock.isNotBlank()) {
                        val m = minStock.toDoubleOrNull()
                        if (m == null || m < 0.0) {
                            validationError = "El stock mínimo no es válido"
                            return@Button
                        }
                        m
                    } else 0.0

                    val parsedMaxStock = if (usesInventory && maxStock.isNotBlank()) {
                        val m = maxStock.toDoubleOrNull()
                        if (m == null || m < 0.0) {
                            validationError = "El stock máximo no es válido"
                            return@Button
                        }
                        m
                    } else 0.0

                    val priceCents = (parsedPrice * 100.0).toLong()
                    val costCents = (parsedCost * 100.0).toLong()
                    val initialStockMillis = (parsedInitialStock * 1000.0).toLong()
                    val minStockMillis = (parsedMinStock * 1000.0).toLong()
                    val maxStockMillis = (parsedMaxStock * 1000.0).toLong()

                    onSave(
                        sku,
                        name,
                        barcode,
                        departmentId,
                        baseUnit,
                        costCents,
                        priceCents,
                        taxRate,
                        usesInventory,
                        initialStockMillis,
                        minStockMillis,
                        maxStockMillis,
                        isActive
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = NexusTheme.colors.actionPrimary)
            ) {
                Text(if (isEdit) "Guardar Cambios" else "Registrar Producto", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
