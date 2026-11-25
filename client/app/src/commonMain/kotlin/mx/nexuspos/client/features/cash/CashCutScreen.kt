package mx.nexuspos.client.features.cash

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.nexuspos.client.theme.NexusTheme
import mx.nexuspos.client.ui.components.FeedbackMessageHost
import mx.nexuspos.contracts.cash.CashSessionSummary
import mx.nexuspos.contracts.cash.DailyFinancialReportDto
import mx.nexuspos.core.Money
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun CashCutScreen(
    viewModel: CashCutViewModel,
    onBackToSales: () -> Unit,
    activeCashierUserId: String = "usr-cajero"
) {
    val state by viewModel.uiState.collectAsState()
    var showConfirmCloseDialog by remember { mutableStateOf(false) }
    val preview = state.ticketPreviewText

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Toolbar superior
        CashCutTopToolbar(
            activeTab = state.activeTab,
            onTabSelected = {
                viewModel.closePreview()
                viewModel.selectTab(it)
            },
            onBackToSales = onBackToSales
        )

        // Banners de error y confirmación
        FeedbackMessageHost(
            errorMessage = state.errorMessage,
            successMessage = state.successMessage,
            onDismissError = viewModel::clearFeedback,
            onDismissSuccess = viewModel::clearFeedback
        )

        // Contenido según la pestaña seleccionada
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            if (preview != null) {
                CashCutReceiptPane(
                    title = state.ticketPreviewTitle,
                    receipt = preview,
                    onBack = viewModel::closePreview,
                    onPrint = viewModel::reprintPreview
                )
            } else when (state.activeTab) {
                CashCutTab.SHIFT_CUT -> {
                    ShiftCutView(
                        state = state,
                        viewModel = viewModel,
                        onRequestCloseSession = { showConfirmCloseDialog = true }
                    )
                }
                CashCutTab.DAILY_REPORT -> {
                    DailyFinancialReportView(
                        state = state,
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    // Modal de confirmación de Cierre Oficial (Corte Z)
    if (showConfirmCloseDialog) {
        val diff = state.differenceCents
        AlertDialog(
            onDismissRequest = { showConfirmCloseDialog = false },
            title = {
                Text(
                    text = "Confirmar Corte Z y Cierre de Turno",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Se cerrará el turno y se guardará el arqueo.")
                    Text("Efectivo contado · ${Money(state.countedCashCents).format()}")
                    if (state.isBlindCashCountEnabled) {
                        Text("La diferencia se mostrará después del cierre.")
                    } else {
                        Text("Efectivo esperado · ${Money(state.activeSession?.expectedCashCents ?: 0L).format()}")
                        Text("Diferencia · ${Money(diff).format()}")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmCloseDialog = false
                        viewModel.performCorteZ()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Cerrar Turno Oficial", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showConfirmCloseDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Modal para abrir nuevo turno si no hay sesión abierta
    if (state.showOpenSessionDialog) {
        OpenSessionModalDialog(
            fundText = state.openFundText,
            onFundTextChanged = viewModel::onOpenFundTextChanged,
            isOpening = state.isOpeningSession,
            onConfirm = { viewModel.openNewSession(activeCashierUserId) },
            onDismiss = { viewModel.showOpenSessionDialog(false) }
        )
    }

}

@Composable
private fun CashCutReceiptPane(title: String, receipt: String, onBack: () -> Unit, onPrint: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onBack) { Text("Volver") }
        }
        Surface(
            Modifier.fillMaxWidth().weight(1f),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                Text(receipt, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
        }
        Button(onClick = onPrint, modifier = Modifier.align(Alignment.End)) {
            Icon(Icons.Default.Print, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Reimprimir")
        }
    }
}

@Composable
private fun CashCutTopToolbar(
    activeTab: CashCutTab,
    onTabSelected: (CashCutTab) -> Unit,
    onBackToSales: () -> Unit
) {
    Surface(
        color = NexusTheme.colors.bgSurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, NexusTheme.colors.borderPrimary),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Icons.Default.PointOfSale,
                    contentDescription = null,
                    tint = NexusTheme.colors.accent,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Módulo de Caja y Cortes [F6]",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = NexusTheme.colors.textPrimary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = activeTab == CashCutTab.SHIFT_CUT,
                    onClick = { onTabSelected(CashCutTab.SHIFT_CUT) },
                    label = { Text("Turno y Arqueo (Cortes X/Z)") },
                    leadingIcon = {
                        Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )

                FilterChip(
                    selected = activeTab == CashCutTab.DAILY_REPORT,
                    onClick = { onTabSelected(CashCutTab.DAILY_REPORT) },
                    label = { Text("Reporte del Día (Ganancia Bruta)") },
                    leadingIcon = {
                        Icon(Icons.Default.TrendingUp, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )

                Spacer(Modifier.width(8.dp))

                OutlinedButton(
                    onClick = onBackToSales,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, NexusTheme.colors.btnSecondaryBorder)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("[Esc] Ventas", color = NexusTheme.colors.btnSecondaryText)
                }
            }
        }
    }
}

@Composable
private fun ShiftCutView(
    state: CashCutUiState,
    viewModel: CashCutViewModel,
    onRequestCloseSession: () -> Unit
) {
    val session = state.activeSession

    if (session == null) {
        Box(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                Text(
                    text = "No hay ningún turno de caja abierto",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Debe abrir una sesión con fondo inicial de efectivo para registrar ventas y arqueos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NexusTheme.colors.textSecondary,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = { viewModel.showOpenSessionDialog(true) },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Abrir Turno de Caja", fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Encabezado de Sesión
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Sesión Activa #${session.sessionId}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = NexusTheme.colors.textPrimary
                    )
                    Surface(
                        color = if (session.status == "OPEN") NexusTheme.colors.surfaceGreen else NexusTheme.colors.bgSurfaceSubtle,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, if (session.status == "OPEN") NexusTheme.colors.borderGreen else NexusTheme.colors.borderPrimary)
                    ) {
                        Text(
                            text = if (session.status == "OPEN") "TURNO ABIERTO" else "CERRADO",
                            color = if (session.status == "OPEN") NexusTheme.colors.textGreen else NexusTheme.colors.textSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Text(
                    text = "Caja: ${session.registerName.ifBlank { session.registerId }} • Cajero: ${session.cashierName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NexusTheme.colors.textSecondary
                )
            }

            OutlinedButton(
                onClick = viewModel::triggerCashDrawer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Abrir Cajón [F7]")
            }
        }

        HorizontalDivider(color = NexusTheme.colors.borderSubtle)

        // Cuadrícula Maestro: Métricas a la izquierda, Arqueo a la derecha
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Columna Izquierda: Tarjetas Financieras del Turno
            Column(
                modifier = Modifier.weight(1.1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Resumen Financiero del Turno",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = NexusTheme.colors.textPrimary
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FinancialMetricCard("Fondo Inicial", Money(session.initialFundCents).format(), Modifier.weight(1f))
                    FinancialMetricCard("Ventas Efectivo", Money(session.salesCashCents).format(), Modifier.weight(1f), isPositive = true)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FinancialMetricCard("Cobranza Fiado", Money(session.creditPaymentsCashCents).format(), Modifier.weight(1f), isPositive = true)
                    FinancialMetricCard("Entradas Caja", Money(session.cashInCents).format(), Modifier.weight(1f))
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FinancialMetricCard("Retiros / Salidas", "-${Money(session.cashOutCents).format()}", Modifier.weight(1f), isNegative = session.cashOutCents > 0)
                    FinancialMetricCard("Reembolsos", Money(session.refundsCashCents).format(), Modifier.weight(1f))
                }

                // Destacado: Efectivo Esperado
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "EFECTIVO ESPERADO EN CAJA",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = if (state.isBlindCashCountEnabled) "*** Oculto (Arqueo Ciego) ***" else Money(session.expectedCashCents).format(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = if (state.isBlindCashCountEnabled) NexusTheme.colors.textSecondary else MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (state.isBlindCashCountEnabled) "Modo Arqueo Ciego activo: el importe esperado está protegido contra manipulación." else "Fondo Inicial + Ventas Efectivo + Abonos Fiado + Entradas - Retiros",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Columna Derecha: Arqueo y Conteo Físico
            Column(
                modifier = Modifier.weight(0.9f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Arqueo y Conteo de Efectivo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = NexusTheme.colors.textPrimary
                )

                DenominationCalculator(
                    denominations = state.denominations,
                    onPiecesChanged = viewModel::onDenominationPiecesChanged,
                    onReset = viewModel::onResetDenominations
                )

                Surface(
                    color = NexusTheme.colors.bgSurfaceSubtle,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, NexusTheme.colors.borderPrimary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = state.countedCashText,
                            onValueChange = viewModel::onCountedCashChanged,
                            label = { Text("Efectivo Físico Contado Total ($)") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Indicador visual de Diferencia
                        if (state.isBlindCashCountEnabled) {
                            Surface(
                                color = NexusTheme.colors.bgSurfaceSubtle,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Diferencia de Arqueo:", fontWeight = FontWeight.Bold, color = NexusTheme.colors.textSecondary)
                                    Text("Se revelará al cerrar turno [Corte Z]", fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = NexusTheme.colors.textSecondary)
                                }
                            }
                        } else {
                            val diff = state.differenceCents
                            val (diffText, diffColor, diffBg) = when {
                                diff == 0L -> Triple("Cuadrado Exacto ($0.00)", NexusTheme.colors.textGreen, NexusTheme.colors.surfaceGreen)
                                diff > 0L -> Triple("Sobrante: +${Money(diff).format()}", Color(0xFF1565C0), Color(0xFFE3F2FD))
                                else -> Triple("Faltante: -${Money(-diff).format()}", MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.errorContainer)
                            }

                            Surface(
                                color = diffBg,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Diferencia de Arqueo:", fontWeight = FontWeight.Bold, color = diffColor)
                                    Text(diffText, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = diffColor)
                                }
                            }
                        }

                        OutlinedTextField(
                            value = state.notesText,
                            onValueChange = viewModel::onNotesChanged,
                            label = { Text("Observaciones del Corte") },
                            placeholder = { Text("Ej: Billete falso detectado, cambio pendiente...") },
                            minLines = 2,
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))
        HorizontalDivider(color = NexusTheme.colors.borderSubtle)

        // Barra inferior de botones de acción
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = viewModel::performCorteX,
                enabled = session.status == "OPEN",
                modifier = Modifier.height(46.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Imprimir Corte X (Parcial)", fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = onRequestCloseSession,
                enabled = session.status == "OPEN" && !state.isClosingSession,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.height(46.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (state.isClosingSession) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(Modifier.width(6.dp))
                } else {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text("Corte Z (Cerrar Turno Oficial)", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DailyFinancialReportView(
    state: CashCutUiState,
    viewModel: CashCutViewModel
) {
    val report = state.dailyReport

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Cabecera del reporte
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Reporte Financiero Consolidado del Día",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = NexusTheme.colors.textPrimary
                )
                Text(
                    text = "Consolida todas las operaciones, desglose de ventas y rentabilidad bruta estimada.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NexusTheme.colors.textSecondary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { viewModel.loadDailyReport() },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Actualizar")
                }

                Button(
                    onClick = viewModel::printDailyReportTicket,
                    enabled = report != null,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Imprimir Reporte Diario", fontWeight = FontWeight.Bold)
                }
            }
        }

        HorizontalDivider(color = NexusTheme.colors.borderSubtle)

        if (state.isLoadingReport) {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }

        if (report == null) {
            Text("No se pudo cargar el reporte consolidado.")
            return
        }

        // Fila 1: Ventas por Método de Pago
        Text(
            text = "1. Desglose de Ventas Brutas",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = NexusTheme.colors.textPrimary
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FinancialMetricCard("Ventas Efectivo", Money(report.salesCashCents).format(), Modifier.weight(1f), isPositive = true)
            FinancialMetricCard("Ventas a Crédito (Fiado)", Money(report.salesCreditCents).format(), Modifier.weight(1f))
            FinancialMetricCard("TOTAL VENTAS", Money(report.totalSalesCents).format(), Modifier.weight(1.2f), isHero = true)
        }

        // Fila 2: Caja y Tickets
        Text(
            text = "2. Flujo de Caja y Operaciones",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = NexusTheme.colors.textPrimary
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FinancialMetricCard("Abonos Fiado Cobrados", Money(report.creditPaymentsCashCents).format(), Modifier.weight(1f), isPositive = true)
            FinancialMetricCard("Entradas de Caja", Money(report.cashInCents).format(), Modifier.weight(1f))
            FinancialMetricCard("Retiros de Caja", "-${Money(report.cashOutCents).format()}", Modifier.weight(1f), isNegative = report.cashOutCents > 0)
            FinancialMetricCard(
                "Tickets Emitidos",
                "${report.completedTicketsCount} (${report.cancelledTicketsCount} cancelados)",
                Modifier.weight(1f)
            )
        }

        // Fila 3: Rentabilidad y Ganancia Bruta (HERO)
        Text(
            text = "3. Análisis de Rentabilidad Estimada",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = NexusTheme.colors.textPrimary
        )

        Surface(
            color = NexusTheme.colors.surfaceGreen,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.5.dp, NexusTheme.colors.borderGreen),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "GANANCIA BRUTA ESTIMADA",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = NexusTheme.colors.textGreen
                    )
                    Text(
                        text = Money(report.estimatedGrossProfitCents).format(),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        color = NexusTheme.colors.textGreen
                    )
                    Text(
                        text = "Cálculo: Ventas Totales (${Money(report.totalSalesCents).format()}) − Costo de Reposición (${Money(report.totalCostCents).format()})",
                        style = MaterialTheme.typography.bodySmall,
                        color = NexusTheme.colors.textGreen.copy(alpha = 0.85f)
                    )
                }

                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, NexusTheme.colors.borderGreen),
                    modifier = Modifier.padding(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Margen de Utilidad",
                            style = MaterialTheme.typography.labelSmall,
                            color = NexusTheme.colors.textSecondary
                        )
                        Text(
                            text = "${report.profitMarginPercent}%",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = NexusTheme.colors.textGreen
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FinancialMetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    isPositive: Boolean = false,
    isNegative: Boolean = false,
    isHero: Boolean = false
) {
    val textColor = when {
        isPositive -> NexusTheme.colors.textGreen
        isNegative -> MaterialTheme.colorScheme.error
        isHero -> MaterialTheme.colorScheme.primary
        else -> NexusTheme.colors.textPrimary
    }

    Surface(
        color = if (isHero) MaterialTheme.colorScheme.primaryContainer else NexusTheme.colors.bgSurfaceSubtle,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (isHero) MaterialTheme.colorScheme.primary else NexusTheme.colors.borderPrimary),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = if (isHero) MaterialTheme.colorScheme.onPrimaryContainer else NexusTheme.colors.textSecondary
            )
            Text(
                text = value,
                style = if (isHero) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
    }
}

@Composable
private fun OpenSessionModalDialog(
    fundText: String,
    onFundTextChanged: (String) -> Unit,
    isOpening: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Abrir Turno de Caja", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Ingrese el fondo inicial en efectivo con el que arrancará este turno:")
                OutlinedTextField(
                    value = fundText,
                    onValueChange = onFundTextChanged,
                    label = { Text("Fondo Inicial ($)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isOpening && (fundText.toDoubleOrNull() ?: -1.0) >= 0.0
            ) {
                if (isOpening) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text("Abrir Turno", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
