package mx.nexuspos.client.features.sales.v2.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mx.nexuspos.client.features.sales.v2.shortcuts.HostOs
import mx.nexuspos.client.features.sales.v2.shortcuts.HostOsDetector
import mx.nexuspos.client.features.sales.v2.shortcuts.PosAction
import mx.nexuspos.client.features.sales.v2.shortcuts.ShortcutBadge
import mx.nexuspos.contracts.credit.CustomerDto
import mx.nexuspos.core.Money

private data class QuickAction(
    val label: String,
    val icon: ImageVector,
    val shortcut: PosAction?,
    val onClick: () -> Unit
)

@Composable
fun QuickActionsGrid(
    selectedCustomer: CustomerDto?,
    onFastSale: () -> Unit,
    onPriceChecker: () -> Unit,
    onCashIn: () -> Unit,
    onCashOut: () -> Unit,
    onAssignCustomer: () -> Unit,
    onGlobalDiscount: () -> Unit,
    onWhatsApp: () -> Unit,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
    os: HostOs = HostOsDetector.current
) {
    val fastSale = QuickAction("Venta rápida", Icons.Default.FlashOn, PosAction.FAST_SALE, onFastSale)
    val actions = listOf(
        QuickAction("Verificar precios", Icons.Default.Search, PosAction.PRICE_CHECKER, onPriceChecker),
        QuickAction("Descuento global", Icons.Default.Percent, PosAction.GLOBAL_DISCOUNT, onGlobalDiscount),
        QuickAction("Entrada de efectivo", Icons.Default.ArrowDownward, PosAction.CASH_IN, onCashIn),
        QuickAction("Salida de efectivo", Icons.Default.ArrowUpward, PosAction.CASH_OUT, onCashOut),
        QuickAction("WhatsApp", Icons.AutoMirrored.Filled.Chat, null, onWhatsApp),
        QuickAction("Abrir cajón", Icons.Default.PointOfSale, PosAction.OPEN_DRAWER, onOpenDrawer)
    )
    val labelStyle = MaterialTheme.typography.labelLarge
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val minButtonWidth = actions.maxOf {
        with(density) { measurer.measure(it.label, labelStyle, maxLines = 1).size.width.toDp() }
    } + 52.dp
    val gap = 8.dp

    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = colors.surfaceVariant,
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp)) {
            val columns = if (maxWidth >= minButtonWidth * 2 + gap) 2 else 1
            val canPlaceShortcutsInline = maxWidth >= minButtonWidth + 72.dp
            val showCustomerShortcut = maxWidth >= if (os == HostOs.MACOS) 200.dp else 270.dp
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                Text(
                    "Acciones rápidas",
                    modifier = Modifier.padding(horizontal = 4.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface
                )
                QuickCustomerAction(selectedCustomer, onAssignCustomer, os, showCustomerShortcut)
                QuickActionButton(fastSale, canPlaceShortcutsInline, os, Modifier.fillMaxWidth())
                actions.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        row.forEach { action ->
                            QuickActionButton(action, columns == 1 && canPlaceShortcutsInline, os, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickCustomerAction(customer: CustomerDto?, onClick: () -> Unit, os: HostOs, showShortcut: Boolean) {
    val colors = MaterialTheme.colorScheme
    TooltipBox(
        modifier = Modifier.fillMaxWidth(),
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            PlainTooltip {
                Text(if (customer == null) "Asignar cliente" else "Cambiar cliente · ${customer.name}")
            }
        },
        state = rememberTooltipState()
    ) {
        FilledTonalButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = if (customer == null) colors.surface else colors.secondaryContainer,
                contentColor = if (customer == null) colors.onSurface else colors.onSecondaryContainer
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(Icons.Default.Person, null, Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    customer?.name ?: "Asignar cliente",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (customer != null) {
                    Text(
                        "Monedero · ${Money(customer.loyaltyBalanceCents).format()}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (showShortcut) {
                Spacer(Modifier.width(8.dp))
                ShortcutBadge(PosAction.ASSIGN_CUSTOMER, os = os)
            }
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (customer == null) null else "Cambiar cliente",
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun QuickActionButton(
    action: QuickAction,
    inlineShortcut: Boolean,
    os: HostOs,
    modifier: Modifier
) {
    val colors = MaterialTheme.colorScheme
    OutlinedButton(
        onClick = action.onClick,
        modifier = modifier.heightIn(min = 56.dp),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, colors.outlineVariant),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = colors.surface, contentColor = colors.onSurface),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Icon(action.icon, null, Modifier.size(18.dp), tint = colors.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        val shortcut = action.shortcut
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(action.label, style = MaterialTheme.typography.labelLarge)
            if (shortcut != null && !inlineShortcut) {
                ShortcutBadge(shortcut, os = os)
            }
        }
        if (shortcut != null && inlineShortcut) {
            Spacer(Modifier.width(8.dp))
            ShortcutBadge(shortcut, os = os)
        }
    }
}
