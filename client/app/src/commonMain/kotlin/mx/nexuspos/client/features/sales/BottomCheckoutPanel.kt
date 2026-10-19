package mx.nexuspos.client.features.sales

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import mx.nexuspos.client.theme.NexusTheme

/**
 * Componente UI del "Panel Inferior de Totales y Gestión" (Bottom Checkout Panel).
 *
 * Franja horizontal anclada en la parte inferior de la pantalla de ventas (justo sobre el SystemFooter).
 * Contiene 3 tarjetas distribuidas proporcionalmente en un [Row] de altura uniforme (176.dp):
 * 1. Tarjeta Izquierda (~0.30f): GESTIÓN DE TICKET (Atajos F5, F6, DEL con alto contraste y Apertura de Cajón centrada).
 * 2. Tarjeta Central (~0.35f): VENTA ANTERIOR / POST-VENTA (Fondo gris surfaceVariant, cajas amplias con cambio verde brillante y botones auxiliares).
 * 3. Tarjeta Derecha (~0.35f): TOTAL A COBRAR (Borde verde de 2.5.dp, total gigante cian luminoso y botón masivo F12 de 58.dp).
 */
@Composable
fun BottomCheckoutPanel(
    state: BottomCheckoutPanelState,
    onSwitchTicket: () -> Unit,
    onHoldTicket: () -> Unit,
    // Este evento elimina por completo el ticket actual en foco, a diferencia de la eliminación de un producto individual de la parrilla
    onCancelCurrentTicket: () -> Unit,
    onOpenCashDrawer: () -> Unit,
    onReprintTicket: () -> Unit,
    onDailySalesAndReturns: () -> Unit,
    onCheckout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(176.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // =====================================================================
        // 1. TARJETA IZQUIERDA: GESTIÓN DE TICKET (weight ~0.30f)
        // =====================================================================
        TicketManagementCard(
            state = state,
            onSwitchTicket = onSwitchTicket,
            onHoldTicket = onHoldTicket,
            onCancelCurrentTicket = onCancelCurrentTicket,
            onOpenCashDrawer = onOpenCashDrawer,
            modifier = Modifier.weight(0.30f).fillMaxHeight()
        )

        // =====================================================================
        // 2. TARJETA CENTRAL: VENTA ANTERIOR / POST-VENTA (weight ~0.35f)
        // =====================================================================
        PreviousSaleCard(
            state = state,
            onReprintTicket = onReprintTicket,
            onDailySalesAndReturns = onDailySalesAndReturns,
            modifier = Modifier.weight(0.35f).fillMaxHeight()
        )

        // =====================================================================
        // 3. TARJETA DERECHA: TOTAL A COBRAR (weight ~0.35f)
        // =====================================================================
        TotalToChargeCard(
            state = state,
            onCheckout = onCheckout,
            modifier = Modifier.weight(0.35f).fillMaxHeight()
        )
    }
}

/**
 * Tarjeta 1: Gestión de ticket activo, alternancia multi-cuenta, tickets pendientes,
 * eliminación de partidas y apertura manual de cajón de dinero.
 */
@Composable
private fun TicketManagementCard(
    state: BottomCheckoutPanelState,
    onSwitchTicket: () -> Unit,
    onHoldTicket: () -> Unit,
    // Este evento elimina por completo el ticket actual en foco, a diferencia de la eliminación de un producto individual de la parrilla
    onCancelCurrentTicket: () -> Unit,
    onOpenCashDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, NexusTheme.colors.borderPrimary),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Cabecera: Título y badge "En Curso"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "GESTIÓN DE TICKET",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Surface(
                    color = NexusTheme.colors.surfaceGreen,
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, NexusTheme.colors.borderGreen)
                ) {
                    Text(
                        text = state.statusBadgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Fila de 3 botones compactos: [F5], [F6], [DEL]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // [F5] Cambiar Ticket
                OutlinedButton(
                    onClick = onSwitchTicket,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, NexusTheme.colors.borderPrimary),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("[F5]", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Cambiar",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }

                // [F6] Dejar Pendiente
                OutlinedButton(
                    onClick = onHoldTicket,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, NexusTheme.colors.borderPrimary),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("[F6]", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Dejar Pend",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }

                // [DEL] Eliminar (Destacado en tokens oficiales Neutro/DEL de alto contraste)
                // Este evento elimina por completo el ticket actual en foco, a diferencia de la eliminación de un producto individual de la parrilla
                Button(
                    onClick = onCancelCurrentTicket,
                    enabled = state.canCancelTicket,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NexusTheme.colors.neutralDelBg,
                        contentColor = NexusTheme.colors.neutralDelText,
                        disabledContainerColor = NexusTheme.colors.neutralDelBg.copy(alpha = 0.35f),
                        disabledContentColor = NexusTheme.colors.neutralDelText.copy(alpha = 0.35f)
                    ),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "[DEL]",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (state.canCancelTicket) NexusTheme.colors.neutralDelText else NexusTheme.colors.neutralDelText.copy(alpha = 0.35f)
                        )
                        Text(
                            "Eliminar",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (state.canCancelTicket) NexusTheme.colors.neutralDelText else NexusTheme.colors.neutralDelText.copy(alpha = 0.35f),
                            maxLines = 1
                        )
                    }
                }
            }

            // Fila inferior: Botón de ancho completo "Abrir Cajón" (Centrado y más alto)
            OutlinedButton(
                onClick = onOpenCashDrawer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, NexusTheme.colors.borderPrimary),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Inbox,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Abrir Cajón",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * Tarjeta 2: Resumen visual de la venta anterior (post-venta).
 * Utiliza un fondo grisáceo suave ([surfaceVariant]) para denotar contexto pasado.
 */
@Composable
private fun PreviousSaleCard(
    state: BottomCheckoutPanelState,
    onReprintTicket: () -> Unit,
    onDailySalesAndReturns: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, NexusTheme.colors.borderPrimary),
        color = NexusTheme.colors.bgInput,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Cabecera: Título y folio del ticket anterior
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "VENTA ANTERIOR / POST-VENTA",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Surface(
                    color = NexusTheme.colors.bgSurface,
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle)
                ) {
                    Text(
                        text = state.lastSaleFolio,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Fila de 3 cajas informativas con borde y altura aumentada
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // TOTAL ANT.
                InfoBox(
                    label = "TOTAL ANT.",
                    value = state.lastSaleTotalFormatted,
                    modifier = Modifier.weight(1f)
                )

                // PAGÓ CON
                InfoBox(
                    label = "PAGÓ CON",
                    value = state.lastSaleReceivedFormatted,
                    modifier = Modifier.weight(1f)
                )

                // CAMBIO (Destacado con acento de Éxito / Cobro oficial)
                InfoBox(
                    label = "CAMBIO",
                    value = state.lastSaleChangeFormatted,
                    labelColor = NexusTheme.colors.actionSuccessHover,
                    valueColor = NexusTheme.colors.actionSuccess,
                    modifier = Modifier.weight(1f)
                )
            }

            // Fila inferior de 2 botones secundarios con mayor altura y separación
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onReprintTicket,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, NexusTheme.colors.borderPrimary),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Reimprimir Ticket",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }

                OutlinedButton(
                    onClick = onDailySalesAndReturns,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, NexusTheme.colors.borderPrimary),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Ventas / Devol.",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tarjeta 3: Total a cobrar actual y botón primario masivo [F12].
 * Destacada con borde verde de 2.5.dp, visor gigante cian y botón primario masivo en verde esmeralda.
 */
@Composable
private fun TotalToChargeCard(
    state: BottomCheckoutPanelState,
    onCheckout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(2.5.dp, NexusTheme.colors.actionSuccess),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Cabecera: Título e indicador de moneda
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TOTAL A COBRAR",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = NexusTheme.colors.actionSuccess,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = state.currencyLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Centro: Texto GIGANTE de total en Azul / Cian de alto contraste (WCAG AAA)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = state.currentTotalFormatted,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Black,
                    color = NexusTheme.colors.highlightCyan,
                    textAlign = TextAlign.End,
                    maxLines = 1
                )
            }

            // Fila inferior: Botón primario masivo de 58.dp en Verde Éxito / Cobro
            Button(
                onClick = onCheckout,
                enabled = state.canCheckout,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NexusTheme.colors.actionSuccess,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = NexusTheme.colors.actionSuccess.copy(alpha = 0.35f),
                    disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.45f)
                ),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Payments,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "[F12] COBRAR ORDEN",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Efectivo / Tarjeta / Fiado",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

/**
 * Caja de información con borde para valores clave (Total Ant., Pagó con, Cambio).
 * Altura de 64.dp con generoso padding interno vertical (8.dp) para separar
 * el texto descriptivo y las cantidades numéricas de los bordes, otorgando mayor "aire" y legibilidad.
 */
@Composable
fun InfoBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle),
        color = NexusTheme.colors.bgSurface,
        modifier = modifier.height(64.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = labelColor,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = valueColor,
                maxLines = 1
            )
        }
    }
}
