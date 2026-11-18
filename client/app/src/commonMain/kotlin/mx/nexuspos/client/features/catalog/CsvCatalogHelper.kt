package mx.nexuspos.client.features.catalog

import mx.nexuspos.contracts.catalog.CatalogImportRowDto

object CsvCatalogHelper {

    fun parseCsvToRows(csvContent: String): List<CatalogImportRowDto> {
        val clean = csvContent.removePrefix("\uFEFF").trim()
        if (clean.isBlank()) return emptyList()

        val lines = splitCsvLines(clean)
        if (lines.isEmpty()) return emptyList()

        // 1. Detectar encabezados
        val headerTokens = parseCsvRow(lines.first()).map { it.trim().uppercase() }
        val hasHeaders = headerTokens.any { it.contains("NOMBRE") || it.contains("PRODUCTO") || it.contains("PRECIO") || it.contains("SKU") }

        var skuIdx = -1
        var barcodeIdx = -1
        var nameIdx = -1
        var deptIdx = -1
        var unitIdx = -1
        var costIdx = -1
        var priceIdx = -1
        var stockIdx = -1
        var minStockIdx = -1
        var activeIdx = -1

        if (hasHeaders) {
            headerTokens.forEachIndexed { i, rawH ->
                val h = rawH.replace("Á", "A")
                    .replace("É", "E")
                    .replace("Í", "I")
                    .replace("Ó", "O")
                    .replace("Ú", "U")
                when {
                    h.contains("SKU") -> skuIdx = i
                    h.contains("BARRAS") || h.contains("CODIGO") || h.contains("BARCODE") -> barcodeIdx = i
                    h.contains("NOMBRE") || h.contains("PRODUCTO") || h.contains("DESCRIPCION") -> nameIdx = i
                    h.contains("DEPARTAMENTO") || h.contains("DEPTO") || h.contains("CATEGORIA") -> deptIdx = i
                    h.contains("UNIDAD") || h.contains("MEDIDA") -> unitIdx = i
                    h.contains("COSTO") -> costIdx = i
                    h.contains("PRECIO") -> priceIdx = i
                    h.contains("ACTUAL") || (h.contains("STOCK") && !h.contains("MIN")) || h.contains("EXISTENCIA") -> stockIdx = i
                    h.contains("MINIM") || h.contains("MIN_STOCK") || h == "MIN" -> minStockIdx = i
                    h.contains("ACTIVO") || h.contains("ESTADO") -> activeIdx = i
                }
            }
        } else {
            // Posicional por defecto: SKU, CODIGO, NOMBRE, DEPTO, UNIDAD, COSTO, PRECIO, STOCK, MIN, ACTIVO
            skuIdx = 0
            barcodeIdx = 1
            nameIdx = 2
            deptIdx = 3
            unitIdx = 4
            costIdx = 5
            priceIdx = 6
            stockIdx = 7
            minStockIdx = 8
            activeIdx = 9
        }

        val dataLines = if (hasHeaders) lines.drop(1) else lines
        val result = mutableListOf<CatalogImportRowDto>()

        for ((index, line) in dataLines.withIndex()) {
            if (line.isBlank()) continue
            val tokens = parseCsvRow(line)
            if (tokens.isEmpty() || tokens.all { it.isBlank() }) continue

            fun getVal(idx: Int): String = if (idx in tokens.indices) tokens[idx].trim() else ""

            val name = if (nameIdx != -1) getVal(nameIdx) else getVal(2)
            if (name.isBlank()) continue

            val sku = if (skuIdx != -1) getVal(skuIdx) else ""
            val barcode = if (barcodeIdx != -1) getVal(barcodeIdx) else ""
            val dept = if (deptIdx != -1) getVal(deptIdx).ifBlank { "General" } else "General"
            val unit = if (unitIdx != -1) getVal(unitIdx).ifBlank { "PZA" } else "PZA"

            val costStr = if (costIdx != -1) getVal(costIdx) else "0"
            val priceStr = if (priceIdx != -1) getVal(priceIdx) else "0"
            val stockStr = if (stockIdx != -1) getVal(stockIdx) else "0"
            val minStockStr = if (minStockIdx != -1) getVal(minStockIdx) else "0"
            val activeStr = if (activeIdx != -1) getVal(activeIdx).uppercase() else "SI"

            val costCents = parseCurrencyToCents(costStr)
            val priceCents = parseCurrencyToCents(priceStr)
            val stockMillis = parseDecimalToMillis(stockStr)
            val minStockMillis = parseDecimalToMillis(minStockStr)
            val isActive = activeStr != "NO" && activeStr != "FALSE" && activeStr != "0"

            result.add(
                CatalogImportRowDto(
                    rowNumber = index + (if (hasHeaders) 2 else 1),
                    sku = sku,
                    barcode = barcode,
                    name = name,
                    department = dept,
                    baseUnit = unit,
                    costCents = costCents,
                    priceCents = priceCents,
                    taxRate = 0.0,
                    currentStockMillis = stockMillis,
                    minStockMillis = minStockMillis,
                    isActive = isActive
                )
            )
        }

        return result
    }

    fun parseCurrencyToCents(value: String): Long {
        val clean = value.replace("$", "").replace(",", "").trim()
        val dbl = clean.toDoubleOrNull() ?: 0.0
        return (dbl * 100.0).toLong().coerceAtLeast(0L)
    }

    fun parseDecimalToMillis(value: String): Long {
        val clean = value.replace(",", "").trim()
        val dbl = clean.toDoubleOrNull() ?: 0.0
        return (dbl * 1000.0).toLong().coerceAtLeast(0L)
    }

    fun splitCsvLines(content: String): List<String> {
        val lines = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < content.length) {
            val c = content[i]
            if (c == '"') {
                inQuotes = !inQuotes
                sb.append(c)
            } else if ((c == '\n' || c == '\r') && !inQuotes) {
                if (c == '\r' && i + 1 < content.length && content[i + 1] == '\n') {
                    i++
                }
                lines.add(sb.toString())
                sb.clear()
            } else {
                sb.append(c)
            }
            i++
        }
        if (sb.isNotEmpty()) {
            lines.add(sb.toString())
        }
        return lines
    }

    fun parseCsvRow(row: String): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < row.length) {
            val c = row[i]
            if (c == '"') {
                if (inQuotes && i + 1 < row.length && row[i + 1] == '"') {
                    sb.append('"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == ',' && !inQuotes) {
                tokens.add(sb.toString().trim())
                sb.clear()
            } else {
                sb.append(c)
            }
            i++
        }
        tokens.add(sb.toString().trim())
        return tokens
    }
}
