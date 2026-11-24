package mx.nexuspos.client.features.sales.v2.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import mx.nexuspos.client.core.ApiClient
import mx.nexuspos.contracts.credit.CustomerDto
import mx.nexuspos.core.Money

private sealed interface CustomerSearchResult {
    data object Loading : CustomerSearchResult
    data object Failed : CustomerSearchResult
    data class Found(val customers: List<CustomerDto>) : CustomerSearchResult
}

@Composable
fun CustomerSelectionDialogV2(
    apiClient: ApiClient,
    currentCustomer: CustomerDto?,
    onSelectCustomer: (CustomerDto?) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var retry by remember { mutableIntStateOf(0) }
    val term = query.trim()
    var result by remember(apiClient, term, retry) { mutableStateOf<CustomerSearchResult>(CustomerSearchResult.Loading) }
    var highlightedIndex by remember(apiClient, term, retry) { mutableIntStateOf(-1) }
    val customers = (result as? CustomerSearchResult.Found)?.customers.orEmpty()
    val listState = rememberLazyListState()
    val searchFocus = remember { FocusRequester() }
    val windowSize = LocalWindowInfo.current.containerDpSize
    val width = if (windowSize.width > 0.dp) (windowSize.width - 32.dp).coerceIn(1.dp, 780.dp) else 780.dp
    val height = if (windowSize.height > 0.dp) (windowSize.height - 32.dp).coerceIn(1.dp, 620.dp) else 620.dp

    LaunchedEffect(apiClient, term, retry) {
        if (term.isNotEmpty()) delay(200)
        val response = apiClient.searchCustomers(query = term)
        ensureActive()
        result = response.fold(
            onSuccess = { CustomerSearchResult.Found(it.filter(CustomerDto::isActive)) },
            onFailure = { CustomerSearchResult.Failed }
        )
        listState.requestScrollToItem(0)
    }

    LaunchedEffect(highlightedIndex) {
        if (highlightedIndex < 0) return@LaunchedEffect
        val layout = listState.layoutInfo
        val item = layout.visibleItemsInfo.firstOrNull { it.index == highlightedIndex }
        val fullyVisible = item != null && item.offset >= layout.viewportStartOffset &&
            item.offset + item.size <= layout.viewportEndOffset
        if (!fullyVisible) listState.scrollToItem(highlightedIndex)
    }

    Dialog(onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.size(width, height),
            shape = MaterialTheme.shapes.extraLarge,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Seleccionar cliente", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cerrar selector de cliente") }
                }

                if (currentCustomer != null) AssignedCustomer(currentCustomer)

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Buscar cliente") },
                    placeholder = { Text("Nombre, folio, teléfono o RFC") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = if (query.isEmpty()) null else {
                        {
                            IconButton(onClick = {
                                query = ""
                                searchFocus.requestFocus()
                            }) {
                                Icon(Icons.Default.Clear, "Limpiar búsqueda")
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    ),
                    modifier = Modifier.fillMaxWidth().focusRequester(searchFocus).onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown || event.isCtrlPressed || event.isMetaPressed ||
                            event.isAltPressed || customers.isEmpty()
                        ) return@onPreviewKeyEvent false

                        when (event.key) {
                            Key.DirectionDown, Key.DirectionUp -> {
                                val step = if (event.key == Key.DirectionDown) 1 else -1
                                highlightedIndex = if (highlightedIndex == -1 && step < 0) customers.lastIndex
                                else (highlightedIndex + step).coerceIn(customers.indices)
                                true
                            }
                            Key.Enter, Key.NumPadEnter -> {
                                val customer = customers.getOrNull(highlightedIndex) ?: customers.singleOrNull()
                                customer?.let(onSelectCustomer)
                                customer != null
                            }
                            else -> false
                        }
                    }
                )
                LaunchedEffect(Unit) { searchFocus.requestFocus() }

                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    when (result) {
                        CustomerSearchResult.Loading -> CircularProgressIndicator()
                        CustomerSearchResult.Failed -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("No se pudo consultar a los clientes.", textAlign = TextAlign.Center)
                                FilledTonalButton(onClick = { retry++ }) { Text("Reintentar") }
                            }
                        }
                        is CustomerSearchResult.Found -> if (customers.isEmpty()) {
                            Text(
                                if (term.isEmpty()) "No hay clientes disponibles."
                                else "Ningún cliente coincide con la búsqueda.",
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                state = listState,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(customers, key = { it.id }) { customer ->
                                    CustomerResultRow(
                                        customer = customer,
                                        isAssigned = customer.id == currentCustomer?.id,
                                        isHighlighted = customers.getOrNull(highlightedIndex)?.id == customer.id,
                                        compact = width < 610.dp,
                                        onClick = { onSelectCustomer(customer) }
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    if (currentCustomer != null) {
                        TextButton(onClick = { onSelectCustomer(null) }) { Text("Quitar cliente") }
                    }
                    OutlinedButton(onClick = onDismiss) { Text("Cerrar") }
                }
            }
        }
    }
}

@Composable
private fun AssignedCustomer(customer: CustomerDto) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text("Cliente asignado", style = MaterialTheme.typography.labelMedium)
                Text(customer.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun CustomerResultRow(
    customer: CustomerDto,
    isAssigned: Boolean,
    isHighlighted: Boolean,
    compact: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().semantics { selected = isAssigned },
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isHighlighted) colors.secondaryContainer else colors.surface,
            contentColor = if (isHighlighted) colors.onSecondaryContainer else colors.onSurface
        ),
        border = BorderStroke(1.dp, if (isHighlighted) colors.primary else colors.outlineVariant)
    ) {
        val modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)
        val secondary = if (isHighlighted) colors.onSecondaryContainer else colors.onSurfaceVariant
        if (compact) {
            Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CustomerIdentity(customer, isAssigned, secondary)
                CustomerBalances(customer, secondary, Alignment.Start)
            }
        } else {
            Row(
                modifier = modifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(Modifier.weight(1f)) { CustomerIdentity(customer, isAssigned, secondary) }
                CustomerBalances(customer, secondary, Alignment.End)
            }
        }
    }
}

@Composable
private fun CustomerIdentity(customer: CustomerDto, isAssigned: Boolean, secondary: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isAssigned) {
                Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Text(customer.name, style = MaterialTheme.typography.titleSmall)
        }
        val metadata = listOf(customer.folio, customer.phone, customer.rfc).filter { it.isNotBlank() }
        if (metadata.isNotEmpty()) {
            Text(metadata.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = secondary)
        }
    }
}

@Composable
private fun CustomerBalances(
    customer: CustomerDto,
    secondary: Color,
    alignment: Alignment.Horizontal
) {
    Column(horizontalAlignment = alignment, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val credit = when {
            !customer.creditEnabled -> "Sin crédito habilitado"
            !customer.hasCreditLimit -> "Crédito sin límite"
            else -> "Disponible · ${Money(customer.availableCreditCents).format()}"
        }
        Text(credit, style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"))
        if (customer.creditEnabled || customer.currentBalanceCents > 0L) {
            val balance = Money(customer.currentBalanceCents).format()
            val limit = if (customer.creditEnabled && customer.hasCreditLimit) {
                " · Límite ${Money(customer.creditLimitCents).format()}"
            } else ""
            Text("Saldo · $balance$limit", style = MaterialTheme.typography.bodySmall, color = secondary)
        }
        Text(
            "Monedero · ${Money(customer.loyaltyBalanceCents).format()}",
            style = MaterialTheme.typography.bodySmall,
            color = secondary
        )
    }
}
