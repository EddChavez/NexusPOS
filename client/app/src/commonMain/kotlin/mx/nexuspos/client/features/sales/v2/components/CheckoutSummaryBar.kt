package mx.nexuspos.client.features.sales.v2.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mx.nexuspos.client.features.sales.CartLine
import mx.nexuspos.client.features.sales.v2.shortcuts.HostOs
import mx.nexuspos.client.features.sales.v2.shortcuts.HostOsDetector
import mx.nexuspos.client.features.sales.v2.shortcuts.PosAction
import mx.nexuspos.client.features.sales.v2.shortcuts.ShortcutBadge
import mx.nexuspos.core.Money

@Composable
fun CheckoutSummaryBar(
    cartLines: List<CartLine>,
    total: Money,
    ticketNote: String,
    onOpenNoteDialog: () -> Unit,
    onReprintTicket: () -> Unit,
    onCheckout: () -> Unit,
    modifier: Modifier = Modifier,
    os: HostOs = HostOsDetector.current
) {
    val summary = formatCheckoutSummary(cartLines)
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            if (maxWidth >= 640.dp) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SummaryText(summary, ticketNote, Modifier.weight(1f))
                    CheckoutActions(total, onOpenNoteDialog, onReprintTicket, onCheckout, os)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SummaryText(summary, ticketNote)
                    CheckoutActions(
                        total, onOpenNoteDialog, onReprintTicket,
                        onCheckout, os, Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryText(summary: String, ticketNote: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(summary, style = MaterialTheme.typography.titleSmall)
        if (ticketNote.isNotBlank()) {
            val preview = ticketNote.lineSequence().first { it.isNotBlank() }.trim()
            Text(
                "Nota: $preview",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CheckoutActions(
    total: Money,
    onOpenNoteDialog: () -> Unit,
    onReprintTicket: () -> Unit,
    onCheckout: () -> Unit,
    os: HostOs,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onOpenNoteDialog, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Icon(Icons.Default.EditNote, null, Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Nota")
        }
        TextButton(onClick = onReprintTicket, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Icon(Icons.Default.Print, null, Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Reimprimir")
        }
        Button(
            onClick = onCheckout,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Column {
                Text("Cobrar", style = MaterialTheme.typography.labelMedium)
                Text(
                    total.format(),
                    style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(8.dp))
            ShortcutBadge(
                PosAction.CHECKOUT,
                os = os,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
