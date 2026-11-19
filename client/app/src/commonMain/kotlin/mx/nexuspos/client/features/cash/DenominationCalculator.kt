package mx.nexuspos.client.features.cash

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.nexuspos.client.theme.NexusTheme
import mx.nexuspos.contracts.cash.CashDenominationCountDto
import mx.nexuspos.core.Money

val DEFAULT_BILLS = listOf(
    100000L to "$1,000",
    50000L to "$500",
    20000L to "$200",
    10000L to "$100",
    5000L to "$50",
    2000L to "$20"
)

val DEFAULT_COINS = listOf(
    2000L to "$20",
    1000L to "$10",
    500L to "$5",
    200L to "$2",
    100L to "$1",
    50L to "$0.50"
)

@Composable
fun DenominationCalculator(
    denominations: List<CashDenominationCountDto>,
    onPiecesChanged: (denominationCents: Long, isCoin: Boolean, pieces: Int) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalCents = denominations.sumOf { it.totalCents }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = NexusTheme.colors.bgSurface,
        border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header of calculator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Arqueo por Billetes y Monedas",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = NexusTheme.colors.textPrimary
                )

                OutlinedButton(
                    onClick = onReset,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NexusTheme.colors.dangerText)
                ) {
                    Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Limpiar Conteo", fontSize = 11.5.sp)
                }
            }

            // Grid of two columns: Billetes & Monedas
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Column 1: Billetes
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "BILLETES",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = NexusTheme.colors.accent
                    )

                    DEFAULT_BILLS.forEach { (cents, label) ->
                        val count = denominations.firstOrNull { it.denominationCents == cents && !it.isCoin }?.pieces ?: 0
                        DenominationRow(
                            label = label,
                            pieces = count,
                            totalCents = cents * count,
                            onPiecesChange = { onPiecesChanged(cents, false, it) }
                        )
                    }
                }

                // Column 2: Monedas
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "MONEDAS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = NexusTheme.colors.actionPrimary
                    )

                    DEFAULT_COINS.forEach { (cents, label) ->
                        val count = denominations.firstOrNull { it.denominationCents == cents && it.isCoin }?.pieces ?: 0
                        DenominationRow(
                            label = label,
                            pieces = count,
                            totalCents = cents * count,
                            onPiecesChange = { onPiecesChanged(cents, true, it) }
                        )
                    }
                }
            }

            HorizontalDivider(color = NexusTheme.colors.borderSubtle)

            // Total counted bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Total Efectivo Contado en Gaveta:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = NexusTheme.colors.textPrimary
                )
                Text(
                    text = Money.fromCents(totalCents).format(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = NexusTheme.colors.accent
                )
            }
        }
    }
}

@Composable
private fun DenominationRow(
    label: String,
    pieces: Int,
    totalCents: Long,
    onPiecesChange: (Int) -> Unit
) {
    Surface(
        color = NexusTheme.colors.bgSurfaceSubtle.copy(alpha = 0.4f),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, if (pieces > 0) NexusTheme.colors.accent.copy(alpha = 0.6f) else NexusTheme.colors.borderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Label
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = NexusTheme.colors.textPrimary,
                modifier = Modifier.width(48.dp)
            )

            // Stepper buttons & piece count
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilledTonalIconButton(
                    onClick = { onPiecesChange((pieces - 1).coerceAtLeast(0)) },
                    modifier = Modifier.size(24.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = NexusTheme.colors.bgSurface
                    )
                ) {
                    Icon(imageVector = Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(12.dp))
                }

                OutlinedTextField(
                    value = if (pieces == 0) "" else pieces.toString(),
                    onValueChange = { input: String ->
                        val clean = input.filter { it.isDigit() }
                        onPiecesChange(clean.toIntOrNull() ?: 0)
                    },
                    modifier = Modifier.width(60.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                FilledTonalIconButton(
                    onClick = { onPiecesChange(pieces + 1) },
                    modifier = Modifier.size(24.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = NexusTheme.colors.bgSurface
                    )
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp))
                }
            }

            // Subtotal for this denomination
            Text(
                text = Money.fromCents(totalCents).format(),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (pieces > 0) NexusTheme.colors.textPrimary else NexusTheme.colors.textSecondary,
                textAlign = TextAlign.End,
                modifier = Modifier.width(72.dp)
            )
        }
    }
}
