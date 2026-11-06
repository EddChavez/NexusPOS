package mx.nexuspos.client.features.header

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.nexuspos.client.core.ServerConnectionState
import mx.nexuspos.client.theme.NexusTheme

/**
 * Top Header persistente de la aplicación NexusPOS para Compose Desktop.
 * Organizado en 3 secciones: Identidad de Marca, Navegación de Módulos (F1-F6) y Periféricos/Red/Sesión.
 */
@Composable
fun TopHeader(
    state: TopHeaderUiState,
    onModuleSelected: (AppModule) -> Unit,
    onLogout: () -> Unit,
    onSwitchCashier: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp,
        modifier = modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = NexusTheme.colors.borderSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. IDENTIDAD DE MARCA (IZQUIERDA)
            BrandIdentitySection(
                title = state.brandTitle,
                subtitle = state.brandSubtitle
            )

            Spacer(modifier = Modifier.width(16.dp))

            // 2. NAVEGACIÓN POR MÓDULOS (CENTRO)
            ModuleNavigationSection(
                activeModule = state.activeModule,
                onModuleSelected = onModuleSelected
            )

            Spacer(modifier = Modifier.width(16.dp))

            // 3. MONITOR DE PERIFÉRICOS, RED Y SESIÓN (DERECHA)
            RightStatusSection(
                state = state,
                onLogout = onLogout,
                onSwitchCashier = onSwitchCashier
            )
        }
    }
}

@Composable
private fun BrandIdentitySection(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 20.sp,
            lineHeight = 22.sp
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun ModuleNavigationSection(
    activeModule: AppModule,
    onModuleSelected: (AppModule) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppModule.entries.forEach { module ->
            val isSelected = module == activeModule

            // Pestaña activa en Azul / Cian (Focus) según especificación oficial
            val containerColor = if (isSelected) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            }

            val contentColor = if (isSelected) {
                MaterialTheme.colorScheme.onSecondary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }

            Surface(
                color = containerColor,
                contentColor = contentColor,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onModuleSelected(module) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "[${module.keyShortcut}]",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp,
                        color = if (isSelected) contentColor else MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = module.title,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun RightStatusSection(
    state: TopHeaderUiState,
    onLogout: () -> Unit,
    onSwitchCashier: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Identificador de Sesión (Caja, Cajero y Rol)
        SessionIdentifierWidget(
            registerId = state.registerId,
            cashierName = state.cashierName,
            cashierRole = state.cashierRole,
            onClick = onSwitchCashier
        )

        // Botón de Bloqueo / Cambio de Cajero [Ctrl+L]
        Button(
            onClick = { onSwitchCashier?.invoke() },
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            Text(
                text = "🔒 Cambiar Cajero [Ctrl+L]",
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            )
        }

        // Botón de Salir con Atajo (Acción destructiva / Salir)
        OutlinedButton(
            onClick = onLogout,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, NexusTheme.colors.dangerBorder),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = NexusTheme.colors.dangerBg,
                contentColor = NexusTheme.colors.dangerText
            )
        ) {
            Text(
                text = "Salir [Esc]",
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = NexusTheme.colors.dangerText
            )
        }
    }
}

@Composable
private fun SessionIdentifierWidget(
    registerId: String,
    cashierName: String,
    cashierRole: String = "CASHIER",
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val (roleLabel, roleBg, roleText) = when (cashierRole.uppercase()) {
        "ADMIN" -> Triple("ADMIN", Color(0xFF7C3AED), Color.White)
        "SUPERVISOR" -> Triple("SUPERVISOR", Color(0xFF0284C7), Color.White)
        else -> Triple("CAJERO", Color(0xFF0D9488), Color.White)
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle),
        modifier = modifier.then(
            if (onClick != null) Modifier.clip(RoundedCornerShape(8.dp)).clickable { onClick() }
            else Modifier
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Caja: $registerId",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "•",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                text = "👤 $cashierName",
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Surface(
                color = roleBg,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.padding(start = 2.dp)
            ) {
                Text(
                    text = roleLabel,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.sp,
                    color = roleText,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
        }
    }
}
