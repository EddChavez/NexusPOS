package mx.nexuspos.desktop.reports

import mx.nexuspos.contracts.reports.*
import kotlin.test.*

class SalesAnalysisCsvTest {
    @Test
    fun exportsHistoricalCostAndEscapesTextWhileKeepingAmountsNumeric() {
        val totals = SalesAnalysisTotals(4500, 100, 200, 4200, 1250, 2950, 1)
        val report = SalesAnalysisReport(
            "2026-11-24", "2026-11-25", "America/Mexico_City", SalesAnalysisGroup.PRODUCT, totals,
            listOf(SalesAnalysisRow("p", "Abarrotes, \"Norte\"", "=SKU", "KG", 1250, totals))
        )
        val csv = salesAnalysisCsv(report)
        assertTrue(csv.startsWith("\uFEFFDesde"))
        assertTrue(csv.contains("\"Abarrotes, \"\"Norte\"\"\""))
        assertTrue(csv.contains("\"'=SKU\""))
        assertTrue(csv.contains(",1.250,45.00,1.00,2.00,42.00,12.50,29.50,70.24,1"))
        assertTrue(csv.lines().any { it.contains("\"Total\"") })
    }
}
