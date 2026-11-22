package mx.nexuspos.client.features.sales.v2.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.nexuspos.client.features.sales.v2.shortcuts.HostOs
import mx.nexuspos.client.features.sales.v2.shortcuts.HostOsDetector
import mx.nexuspos.client.features.sales.v2.shortcuts.PosAction
import mx.nexuspos.client.features.sales.v2.shortcuts.ShortcutBadge
import mx.nexuspos.contracts.credit.CustomerDto
import mx.nexuspos.core.Money

/**
 * Cuadrícula oficial Material Design 3 de 8 Acciones Rápidas (2 columnas × 4 filas) para operaciones de caja.
 * Implementada con FilledTonalButton oficial, insignias dinámicas según SO y soporte de crédito/monedero (Requisito 4.4).
 */
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
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // FILA 1: Venta Rápida & Verificador de Precios
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickActionButton(
                label = "Venta Rápida",
                icon = Icons.Default.FlashOn,
                action = PosAction.FAST_SALE,
                onClick = onFastSale,
                modifier = Modifier.weight(1f),
                os = os
            )
            QuickActionButton(
                label = "Verif. Precios",
                icon = Icons.Default.Search,
                action = PosAction.PRICE_CHECKER,
                onClick = onPriceChecker,
                modifier = Modifier.weight(1f),
                os = os
            )
        }

        // FILA 2: Entrada Efectivo & Salida Efectivo
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickActionButton(
                label = "Entrada Efectivo",
                icon = Icons.Default.ArrowDownward,
                action = PosAction.CASH_IN,
                onClick = onCashIn,
                modifier = Modifier.weight(1f),
                os = os
            )
            QuickActionButton(
                label = "Salida Efectivo",
                icon = Icons.Default.ArrowUpward,
                action = PosAction.CASH_OUT,
                onClick = onCashOut,
                modifier = Modifier.weight(1f),
                os = os
            )
        }

        // FILA 3: Asignar Cliente (Con Monedero/Crédito Requisito 4.4) & Descuento Global
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val (customerLabel, customerSubtitle) = if (selectedCustomer != null) {
                val loyalty = Money.fromCents(selectedCustomer.loyaltyBalanceCents).format()
                Pair(selectedCustomer.name.take(12), "💰 $loyalty Mon.")
            } else {
                Pair("Asignar Cliente", null)
            }

            QuickActionButton(
                label = customerLabel,
                subtitle = customerSubtitle,
                icon = Icons.Default.Person,
                action = PosAction.ASSIGN_CUSTOMER,
                onClick = onAssignCustomer,
                modifier = Modifier.weight(1f),
                highlight = selectedCustomer != null,
                os = os
            )
            QuickActionButton(
                label = "Desc. Global",
                icon = Icons.Default.Percent,
                action = PosAction.GLOBAL_DISCOUNT,
                onClick = onGlobalDiscount,
                modifier = Modifier.weight(1f),
                os = os
            )
        }

        // FILA 4: WhatsApp & Abrir Cajón
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickActionButton(
                label = "WhatsApp",
                icon = Icons.Default.Chat,
                action = null,
                customBadge = if (os == HostOs.MACOS) "⌥W" else "Alt+W",
                onClick = onWhatsApp,
                modifier = Modifier.weight(1f),
                os = os
            )
            QuickActionButton(
                label = "Abrir Cajón",
                icon = Icons.Default.PointOfSale,
                action = PosAction.OPEN_DRAWER,
                onClick = onOpenDrawer,
                modifier = Modifier.weight(1f),
                os = os
            )
        }
    }
}

@Composable
private fun QuickActionButton(
    label: String,
    icon: ImageVector,
    action: PosAction?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    customBadge: String? = null,
    highlight: Boolean = false,
    os: HostOs = HostOsDetector.current
) {
    FilledTonalButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = if (highlight) {
            ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        } else {
            ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        modifier = modifier.height(58.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Insignia <kbd> en esquina superior derecha
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                if (action != null) {
                    ShortcutBadge(action = action, os = os)
                } else if (customBadge != null) {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text(
                            text = customBadge,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            // Icono y Texto (con subtítulo opcional para Monedero)
            Row(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(0.82f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )

                Column(verticalArrangement = Arrangement.Center) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
