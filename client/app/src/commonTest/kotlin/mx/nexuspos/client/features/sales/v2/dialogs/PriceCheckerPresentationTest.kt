package mx.nexuspos.client.features.sales.v2.dialogs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.contracts.catalog.ProductPresentationDto

class PriceCheckerPresentationTest {
    private val unit = ProductPresentationDto("unit", "product", "100", "Pieza", 1000, 1200)
    private val pack = ProductPresentationDto("pack", "product", "200", "Caja", 12000, 12500)
    private val product = ProductDto("product", "SKU", "Galletas", presentations = listOf(unit, pack))

    @Test
    fun usesThePriceOfTheScannedPresentation() {
        assertEquals(pack, product.presentationForPriceCheck("200"))
    }

    @Test
    fun matchesPresentationNameAndFallsBackToTheBasePresentation() {
        assertEquals(pack, product.presentationForPriceCheck("caja"))
        assertEquals(unit, product.presentationForPriceCheck("Galletas"))
        assertNull(product.copy(presentations = emptyList()).presentationForPriceCheck("Galletas"))
    }
}
