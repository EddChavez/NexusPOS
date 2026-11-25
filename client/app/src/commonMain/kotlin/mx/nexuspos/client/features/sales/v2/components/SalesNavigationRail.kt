package mx.nexuspos.client.features.sales.v2.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import mx.nexuspos.client.features.header.AppModule

private val operationDestinations = listOf(
    AppModule.SALES to Icons.Default.PointOfSale,
    AppModule.CUSTOMERS to Icons.Default.People,
    AppModule.PRODUCTS to Icons.Default.Inventory2,
    AppModule.INVENTORY to Icons.Default.Warehouse,
    AppModule.PURCHASES to Icons.Default.LocalShipping,
    AppModule.TRANSACTIONS to Icons.Default.ReceiptLong,
    AppModule.CASH_CUT to Icons.Default.Assessment
)

@Composable
fun SalesNavigationRail(
    activeModule: AppModule,
    onModuleSelected: (AppModule) -> Unit,
    cashierName: String,
    cashierRole: String,
    onLockTerminal: () -> Unit,
    modifier: Modifier = Modifier,
    canViewTransactions: Boolean = true,
    canViewPurchases: Boolean = cashierRole in setOf("ADMIN", "SUPERVISOR")
) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier.width(64.dp).fillMaxHeight(), color = colors.surface) {
        Column(
            Modifier.fillMaxSize().padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                operationDestinations.forEach { (module, icon) ->
                    val visible = when (module) {
                        AppModule.PURCHASES -> canViewPurchases
                        AppModule.TRANSACTIONS -> canViewTransactions
                        else -> true
                    }
                    if (visible) {
                        RailIcon(
                            icon, module.title, selected = module == activeModule,
                            onClick = { onModuleSelected(module) }
                        )
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp).width(32.dp), color = colors.outlineVariant)
            RailIcon(
                Icons.Default.Settings, "Ajustes", selected = activeModule == AppModule.SETTINGS,
                onClick = { onModuleSelected(AppModule.SETTINGS) }
            )
            RailIcon(Icons.Default.Lock, "Bloquear terminal · $cashierName", onClick = onLockTerminal)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RailIcon(icon: ImageVector, label: String, onClick: () -> Unit, selected: Boolean? = null) {
    val colors = MaterialTheme.colorScheme
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.End),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState()
    ) {
        if (selected != null) {
            IconToggleButton(
                checked = selected,
                onCheckedChange = { onClick() },
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.iconToggleButtonColors(
                    contentColor = colors.onSurfaceVariant,
                    checkedContainerColor = colors.primaryContainer,
                    checkedContentColor = colors.onPrimaryContainer
                )
            ) {
                Icon(icon, contentDescription = label, modifier = Modifier.size(21.dp))
            }
        } else {
            IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
                Icon(icon, contentDescription = label, modifier = Modifier.size(21.dp), tint = colors.onSurfaceVariant)
            }
        }
    }
}
