package mx.nexuspos.client.features.sales.v2.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.nexuspos.client.features.header.AppModule

/**
 * Barra vertical oficial Material Design 3 (NavigationRail) de ancho 72.dp.
 * Contiene el logo, el selector de módulos y el perfil de cajero con bloqueo.
 */
@Composable
fun SalesNavigationRail(
    activeModule: AppModule,
    onModuleSelected: (AppModule) -> Unit,
    cashierName: String,
    cashierRole: String,
    onLockTerminal: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationRail(
        modifier = modifier.width(72.dp).fillMaxHeight(),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        header = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "NP",
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                HorizontalDivider(
                    thickness = 0.8.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    modifier = Modifier.width(44.dp)
                )
            }
        }
    ) {
        // =====================================================================
        // ITEMS OFICIALES MATERIAL DESIGN 3 (NavigationRailItem)
        // =====================================================================
        NavigationRailItem(
            selected = activeModule == AppModule.SALES,
            onClick = { onModuleSelected(AppModule.SALES) },
            icon = { Icon(Icons.Default.PointOfSale, contentDescription = "Ventas") },
            label = { Text("Ventas", style = MaterialTheme.typography.labelSmall) }
        )

        NavigationRailItem(
            selected = activeModule == AppModule.CUSTOMERS,
            onClick = { onModuleSelected(AppModule.CUSTOMERS) },
            icon = { Icon(Icons.Default.People, contentDescription = "Clientes") },
            label = { Text("Clientes", style = MaterialTheme.typography.labelSmall) }
        )

        NavigationRailItem(
            selected = activeModule == AppModule.PRODUCTS,
            onClick = { onModuleSelected(AppModule.PRODUCTS) },
            icon = { Icon(Icons.Default.Inventory2, contentDescription = "Productos") },
            label = { Text("Productos", style = MaterialTheme.typography.labelSmall) }
        )

        NavigationRailItem(
            selected = activeModule == AppModule.INVENTORY,
            onClick = { onModuleSelected(AppModule.INVENTORY) },
            icon = { Icon(Icons.Default.Warehouse, contentDescription = "Inventario") },
            label = { Text("Inventario", style = MaterialTheme.typography.labelSmall) }
        )

        if (cashierRole in setOf("ADMIN", "SUPERVISOR")) {
            NavigationRailItem(
                selected = activeModule == AppModule.PURCHASES,
                onClick = { onModuleSelected(AppModule.PURCHASES) },
                icon = { Icon(Icons.Default.LocalShipping, contentDescription = "Compras") },
                label = { Text("Compras", style = MaterialTheme.typography.labelSmall) }
            )
        }

        NavigationRailItem(
            selected = activeModule == AppModule.CASH_CUT,
            onClick = { onModuleSelected(AppModule.CASH_CUT) },
            icon = { Icon(Icons.Default.Assessment, contentDescription = "Cortes") },
            label = { Text("Cortes", style = MaterialTheme.typography.labelSmall) }
        )

        NavigationRailItem(
            selected = activeModule == AppModule.SETTINGS,
            onClick = { onModuleSelected(AppModule.SETTINGS) },
            icon = { Icon(Icons.Default.Settings, contentDescription = "Ajustes") },
            label = { Text("Ajustes", style = MaterialTheme.typography.labelSmall) }
        )

        Spacer(modifier = Modifier.weight(1f))

        // =====================================================================
        // PERFIL DE CAJERO Y BLOQUEO DE TERMINAL
        // =====================================================================
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            HorizontalDivider(
                thickness = 0.8.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                modifier = Modifier.width(44.dp)
            )

            val initials = cashierName.split(" ")
                .filter { it.isNotBlank() }
                .take(2)
                .map { it.first().uppercaseChar() }
                .joinToString("")
                .ifBlank { "CJ" }

            FilledTonalIconButton(
                onClick = onLockTerminal,
                modifier = Modifier.size(38.dp),
                shape = CircleShape
            ) {
                Text(
                    text = initials,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            IconButton(
                onClick = onLockTerminal,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Bloquear terminal",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
