package mx.nexuspos.client.features.products

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import mx.nexuspos.client.features.catalog.CsvCatalogHelper
import mx.nexuspos.client.theme.NexusTheme
import mx.nexuspos.contracts.catalog.CatalogImportResponse
import mx.nexuspos.contracts.catalog.CatalogImportRowDto

enum class ImportExportTab(val title: String) {
    EXPORT("Exportar Catálogo (Excel / CSV)"),
    IMPORT("Importar Catálogo Masivo")
}

@Composable
fun CatalogImportExportDialog(
    isOpen: Boolean,
    exportedCsv: String? = null,
    isExporting: Boolean = false,
    isImporting: Boolean = false,
    importResult: CatalogImportResponse? = null,
    onExport: () -> Unit,
    onImport: (csvContent: String) -> Unit,
    onClearResult: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    var activeTab by remember { mutableStateOf(ImportExportTab.EXPORT) }
    var inputCsvText by remember { mutableStateOf("") }
    var previewRows by remember { mutableStateOf<List<CatalogImportRowDto>>(emptyList()) }
    val clipboardManager = LocalClipboardManager.current
    var copiedFeedback by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = NexusTheme.colors.bgSurface,
            border = BorderStroke(1.5.dp, NexusTheme.colors.borderSubtle),
            modifier = Modifier
                .width(860.dp)
                .height(600.dp)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Dataset,
                            contentDescription = "Importar / Exportar",
                            tint = NexusTheme.colors.actionPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                        Column {
                            Text(
                                text = "Operación Masiva de Catálogo (Excel / CSV)",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = NexusTheme.colors.textPrimary
                            )
                            Text(
                                text = "Compatible con Microsoft Excel, LibreOffice y hojas de cálculo estándar.",
                                fontSize = 12.sp,
                                color = NexusTheme.colors.textSecondary
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = NexusTheme.colors.textSecondary
                        )
                    }
                }

                // Tabs Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ImportExportTab.entries.forEach { tab ->
                        val isSelected = activeTab == tab
                        Button(
                            onClick = { activeTab = tab },
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) NexusTheme.colors.actionPrimary else NexusTheme.colors.bgSurfaceSubtle,
                                contentColor = if (isSelected) NexusTheme.colors.actionPrimaryFg else NexusTheme.colors.textPrimary
                            ),
                            border = if (!isSelected) BorderStroke(1.dp, NexusTheme.colors.borderSubtle) else null,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(tab.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }

                HorizontalDivider(color = NexusTheme.colors.borderSubtle)

                // Tab Content
                when (activeTab) {
                    ImportExportTab.EXPORT -> {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Formato CSV con codificación UTF-8 BOM para apertura sin distorsión de acentos en Excel:",
                                    fontSize = 12.sp,
                                    color = NexusTheme.colors.textSecondary
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            exportedCsv?.let {
                                                clipboardManager.setText(AnnotatedString(it))
                                                copiedFeedback = true
                                            }
                                        },
                                        enabled = exportedCsv != null,
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle)
                                    ) {
                                        Icon(
                                            imageVector = if (copiedFeedback) Icons.Default.Check else Icons.Default.ContentCopy,
                                            contentDescription = "Copiar",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (copiedFeedback) "¡Copiado!" else "Copiar al Portapapeles")
                                    }

                                    Button(
                                        onClick = onExport,
                                        shape = RoundedCornerShape(6.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = NexusTheme.colors.actionPrimary,
                                            contentColor = NexusTheme.colors.actionPrimaryFg
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = "Recargar Exportación",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Actualizar Exportación")
                                    }
                                }
                            }

                            // Vista previa del CSV
                            Surface(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                color = NexusTheme.colors.bgSurfaceSubtle,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle)
                            ) {
                                if (isExporting) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = NexusTheme.colors.actionPrimary)
                                    }
                                } else if (exportedCsv != null) {
                                    LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                                        item {
                                            Text(
                                                text = exportedCsv,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                color = NexusTheme.colors.textPrimary
                                            )
                                        }
                                    }
                                } else {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("Haga clic en 'Actualizar Exportación' para generar los datos.", color = NexusTheme.colors.textMuted)
                                    }
                                }
                            }
                        }
                    }

                    ImportExportTab.IMPORT -> {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (importResult != null) {
                                // Vista de resultados
                                Surface(
                                    color = NexusTheme.colors.bgSurfaceSubtle,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceAround
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Total Filas", fontSize = 11.sp, color = NexusTheme.colors.textSecondary)
                                            Text("${importResult.totalRows}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = NexusTheme.colors.textPrimary)
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Creados", fontSize = 11.sp, color = NexusTheme.colors.textSecondary)
                                            Text("${importResult.createdCount}", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = NexusTheme.colors.textGreen)
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Actualizados", fontSize = 11.sp, color = NexusTheme.colors.textSecondary)
                                            Text("${importResult.updatedCount}", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = NexusTheme.colors.actionPrimary)
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Errores", fontSize = 11.sp, color = NexusTheme.colors.textSecondary)
                                            Text("${importResult.errorCount}", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = if (importResult.errorCount > 0) NexusTheme.colors.dangerText else NexusTheme.colors.textMuted)
                                        }
                                    }
                                }

                                // Detalle por fila
                                Surface(
                                    modifier = Modifier.fillMaxWidth().weight(1f),
                                    color = NexusTheme.colors.bgSurfaceSubtle,
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle)
                                ) {
                                    LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        items(importResult.rows) { row ->
                                            val (badgeBg, badgeFg) = when (row.status) {
                                                "CREATED" -> NexusTheme.colors.surfaceGreen to NexusTheme.colors.textGreen
                                                "UPDATED" -> NexusTheme.colors.bgSurface to NexusTheme.colors.actionPrimary
                                                else -> NexusTheme.colors.dangerBg to NexusTheme.colors.dangerText
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Surface(color = badgeBg, shape = RoundedCornerShape(4.dp)) {
                                                        Text(row.status, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = badgeFg, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                                    }
                                                    Text("Fila ${row.rowNumber}: ${row.name} [${row.sku.ifBlank { row.barcode }}]", fontSize = 12.sp, color = NexusTheme.colors.textPrimary)
                                                }
                                                row.message?.let {
                                                    Text(it, fontSize = 11.sp, color = if (row.status == "ERROR") NexusTheme.colors.dangerText else NexusTheme.colors.textSecondary)
                                                }
                                            }
                                        }
                                    }
                                }

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    Button(onClick = onClearResult) {
                                        Text("Importar Otro Archivo")
                                    }
                                }
                            } else {
                                // Entrada de CSV
                                Text(
                                    text = "Pegue aquí las filas copiadas desde Excel o archivo CSV (debe incluir cabeceras o columnas en orden):",
                                    fontSize = 12.sp,
                                    color = NexusTheme.colors.textSecondary
                                )

                                OutlinedTextField(
                                    value = inputCsvText,
                                    onValueChange = {
                                        inputCsvText = it
                                        previewRows = CsvCatalogHelper.parseCsvToRows(it)
                                    },
                                    modifier = Modifier.fillMaxWidth().weight(1f),
                                    placeholder = {
                                        Text(
                                            "SKU,CODIGO_BARRAS,NOMBRE,DEPARTAMENTO,UNIDAD,COSTO,PRECIO,STOCK_ACTUAL,STOCK_MINIMO,ACTIVO\n" +
                                            "GAL-01,75010001,Galletas 100g,Botanas,PZA,12.50,18.00,50,10,SI\n" +
                                            "REF-02,75010002,Refresco 600ml,Bebidas,PZA,14.00,20.00,100,20,SI",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = NexusTheme.colors.textMuted
                                        )
                                    },
                                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = NexusTheme.colors.bgSurfaceSubtle,
                                        unfocusedContainerColor = NexusTheme.colors.bgSurfaceSubtle,
                                        focusedTextColor = NexusTheme.colors.textPrimary,
                                        unfocusedTextColor = NexusTheme.colors.textPrimary
                                    )
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${previewRows.size} productos detectados para importar.",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (previewRows.isNotEmpty()) NexusTheme.colors.textGreen else NexusTheme.colors.textSecondary
                                    )

                                    Button(
                                        onClick = { onImport(inputCsvText) },
                                        enabled = previewRows.isNotEmpty() && !isImporting,
                                        shape = RoundedCornerShape(6.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = NexusTheme.colors.textGreen,
                                            contentColor = Color.Black
                                        )
                                    ) {
                                        if (isImporting) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Importando...")
                                        } else {
                                            Icon(imageVector = Icons.Default.Upload, contentDescription = "Importar", modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Ejecutar Importación (${previewRows.size} artículos)")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, NexusTheme.colors.borderSubtle)
                    ) {
                        Text("Cerrar [Esc]")
                    }
                }
            }
        }
    }
}
