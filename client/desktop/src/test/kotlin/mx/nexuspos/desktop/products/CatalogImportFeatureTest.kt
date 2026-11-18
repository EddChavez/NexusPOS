package mx.nexuspos.desktop.products

import mx.nexuspos.client.features.catalog.CsvCatalogHelper
import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.desktop.hardware.EscPosReceiptPrinter
import mx.nexuspos.desktop.hardware.PaperWidth
import kotlin.test.*

class CatalogImportFeatureTest {

    @Test
    fun testParseCsvToRowsWithQuotesAndCurrency() {
        val csv = """
            SKU,Código de Barras,Nombre,Departamento,Precio,Costo,Unidad,Stock Actual,Stock Mínimo,Impuesto %,Activo
            ACE-123,75010001,"Aceite 1-2-3, 1 Litro",Abarrotes,"$ 45.50","$ 38.00",PZA,12,3,0,true
            MANZ-KG,2001,Manzana Golden,Frutas,$ 39.90,$ 25.00,KG,8.5,2,0,SI
        """.trimIndent()

        val rows = CsvCatalogHelper.parseCsvToRows(csv)
        assertEquals(2, rows.size)

        val row1 = rows[0]
        assertEquals("ACE-123", row1.sku)
        assertEquals("75010001", row1.barcode)
        assertEquals("Aceite 1-2-3, 1 Litro", row1.name)
        assertEquals("Abarrotes", row1.department)
        assertEquals(4550L, row1.priceCents)
        assertEquals(3800L, row1.costCents)
        assertEquals("PZA", row1.baseUnit)
        assertEquals(12000L, row1.currentStockMillis)
        assertEquals(3000L, row1.minStockMillis)
        assertTrue(row1.isActive)

        val row2 = rows[1]
        assertEquals("MANZ-KG", row2.sku)
        assertEquals("2001", row2.barcode)
        assertEquals("Manzana Golden", row2.name)
        assertEquals("Frutas", row2.department)
        assertEquals(3990L, row2.priceCents)
        assertEquals(2500L, row2.costCents)
        assertEquals("KG", row2.baseUnit)
        assertEquals(8500L, row2.currentStockMillis)
        assertEquals(2000L, row2.minStockMillis)
        assertTrue(row2.isActive)
    }

    @Test
    fun testFormatShelfLabelText() {
        val printer = EscPosReceiptPrinter(PaperWidth.MM_58)
        val text = printer.formatShelfLabelText(
            productName = "Nutella 350g",
            barcode = "7501055301111",
            priceCents = 8550L,
            presentationName = "Pieza",
            storeName = "ABARROTES EL CENTRO"
        )

        assertTrue(text.contains("ABARROTES EL CENTRO"))
        assertTrue(text.contains("NUTELLA 350G"))
        assertTrue(text.contains("$85.50"))
        assertTrue(text.contains("7501055301111"))
        assertTrue(text.contains("Presentación: Pieza"))
    }

    @Test
    fun testGenerateShelfLabelBytesEscPos() {
        val printer = EscPosReceiptPrinter(PaperWidth.MM_80)
        val bytes = printer.generateShelfLabelBytes(
            productName = "Arroz SOS 1kg",
            barcode = "7501000999888",
            priceCents = 3200L,
            presentationName = "Bolsa",
            storeName = "NEXUS POS",
            cutPaper = true
        )

        assertTrue(bytes.isNotEmpty())
        // Verifica que los bytes contengan el comando ESC @ (0x1B 0x40) de inicialización
        assertEquals(0x1B.toByte(), bytes[0])
        assertEquals(0x40.toByte(), bytes[1])

        // Verifica que contenga el comando GS k 73 (0x1D 0x6B 73) para código de barras Code 128
        var hasCode128 = false
        for (i in 0 until bytes.size - 2) {
            if (bytes[i] == 0x1D.toByte() && bytes[i + 1] == 'k'.code.toByte() && bytes[i + 2] == 73.toByte()) {
                hasCode128 = true
                break
            }
        }
        assertTrue(hasCode128, "Los bytes generados deben incluir el comando GS k 73 para código 128")
    }
}
