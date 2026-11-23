package mx.nexuspos.client.features.sales.v2.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.automirrored.filled.Chat
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
    val actions = listOf(
        QuickAction("Venta rápida", Icons.Default.FlashOn, PosAction.FAST_SALE, onFastSale),
        QuickAction("Verificar precios", Icons.Default.Search, PosAction.PRICE_CHECKER, onPriceChecker),
        QuickAction("Entrada de efectivo", Icons.Default.ArrowDownward, PosAction.CASH_IN, onCashIn),
        QuickAction("Salida de efectivo", Icons.Default.ArrowUpward, PosAction.CASH_OUT, onCashOut),
        QuickAction("Asignar cliente", Icons.Default.Person, PosAction.ASSIGN_CUSTOMER, onAssignCustomer),
        QuickAction("Descuento global", Icons.Default.Percent, PosAction.GLOBAL_DISCOUNT, onGlobalDiscount),
        QuickAction("WhatsApp", Icons.AutoMirrored.Filled.Chat, null, onWhatsApp),
        QuickAction("Abrir cajón", Icons.Default.PointOfSale, PosAction.OPEN_DRAWER, onOpenDrawer)
    )

    val labelStyle = MaterialTheme.typography.labelLarge
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val minButtonWidth = actions.maxOf {
        with(density) { measurer.measure(it.label, labelStyle, maxLines = 1).size.width.toDp() }
    } + 56.dp
    val gap = 8.dp

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
        Text("Acciones rápidas", style = MaterialTheme.typography.titleSmall)
        if (selectedCustomer != null) {
            Text(
                selectedCustomer.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Monedero: ${Money.fromCents(selectedCustomer.loyaltyBalanceCents).format()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = if (maxWidth >= minButtonWidth * 2 + gap) 2 else 1
            val buttonWidth = (maxWidth - gap * (columns - 1)) / columns
            Column(verticalArrangement = Arrangement.spacedBy(if (columns == 2) gap else 4.dp)) {
                actions.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        row.forEach { action ->
                            val shortcut = action.shortcut?.takeIf { buttonWidth >= minButtonWidth }
                            val inlineShortcut = shortcut != null && columns == 1 && buttonWidth >= minButtonWidth + 70.dp
                            FilledTonalButton(
                                onClick = action.onClick,
                                modifier = Modifier.width(buttonWidth).heightIn(min = 48.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                colors = if (action.shortcut == PosAction.ASSIGN_CUSTOMER && selectedCustomer != null) {
                                    ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                } else {
                                    ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            ) {
                                Icon(action.icon, null, Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(action.label, style = labelStyle)
                                    if (shortcut != null && !inlineShortcut) {
                                        ShortcutBadge(shortcut, os = os)
                                    }
                                }
                                if (inlineShortcut) {
                                    Spacer(Modifier.width(6.dp))
                                    ShortcutBadge(shortcut, os = os)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
