package mx.nexuspos.desktop.products

import mx.nexuspos.client.features.products.ProductsUiState
import mx.nexuspos.contracts.catalog.DepartmentDto
import mx.nexuspos.contracts.catalog.ProductDto
import mx.nexuspos.contracts.catalog.ProductPresentationDto
import kotlin.test.*

class ProductsFeatureTest {

    private val samplePresentation = ProductPresentationDto(
        id = "pres-1",
        productId = "prod-1",
        barcode = "7501000123456",
        name = "Pieza",
        factorMillis = 1000L,
        priceCents = 2400L
    )

    private val sampleProduct = ProductDto(
        id = "prod-1",
        sku = "PRD-0001",
        name = "Leche Lala Entera 1L",
        departmentId = "dept-lacteos",
        departmentName = "Lácteos",
        baseUnit = "PZA",
        costCents = 1850L,
        taxRate = 0.0,
        usesInventory = true,
        currentStockMillis = 20000L, // 20 PZA
        minStockMillis = 5000L,     // 5 PZA
        maxStockMillis = 50000L,
        isActive = true,
        presentations = listOf(samplePresentation)
    )

    @Test
    fun testDefaultUiState() {
        val state = ProductsUiState()

        assertTrue(state.products.isEmpty())
        assertNull(state.selectedProduct)
        assertTrue(state.departments.isEmpty())
        assertNull(state.selectedDepartmentId)
        assertEquals("", state.searchQuery)
        assertFalse(state.includeInactive)
        assertFalse(state.isLoading)
        assertFalse(state.showFormDialog)
        assertNull(state.productToEdit)
        assertFalse(state.showDepartmentsDialog)
        assertFalse(state.isDepartmentSubmitting)
        assertNull(state.errorMessage)
        assertNull(state.successMessage)
    }

    @Test
    fun testProductSelectionAndDepartmentFiltering() {
        val depts = listOf(
            DepartmentDto(id = "dept-lacteos", name = "Lácteos"),
            DepartmentDto(id = "dept-bebidas", name = "Bebidas")
        )

        var state = ProductsUiState(
            products = listOf(sampleProduct),
            departments = depts,
            selectedProduct = sampleProduct
        )

        assertEquals(1, state.products.size)
        assertEquals("prod-1", state.selectedProduct?.id)
        assertEquals("PRD-0001", state.selectedProduct?.sku)

        // Select department
        state = state.copy(selectedDepartmentId = "dept-lacteos")
        assertEquals("dept-lacteos", state.selectedDepartmentId)

        // Toggle inactive
        state = state.copy(includeInactive = true)
        assertTrue(state.includeInactive)
    }

    @Test
    fun testFormDialogTransitions() {
        var state = ProductsUiState()

        // Open create
        state = state.copy(showFormDialog = true, productToEdit = null)
        assertTrue(state.showFormDialog)
        assertNull(state.productToEdit)

        // Open edit
        state = state.copy(showFormDialog = true, productToEdit = sampleProduct)
        assertTrue(state.showFormDialog)
        assertEquals(sampleProduct, state.productToEdit)

        // Close dialog
        state = state.copy(showFormDialog = false, productToEdit = null)
        assertFalse(state.showFormDialog)
        assertNull(state.productToEdit)
    }

    @Test
    fun testProductStockPresentation() {
        assertEquals("7501000123456", sampleProduct.presentations.first().barcode)
        assertEquals(2400L, sampleProduct.presentations.first().priceCents)
        assertEquals(20000L, sampleProduct.currentStockMillis)
        assertTrue(sampleProduct.currentStockMillis > sampleProduct.minStockMillis)
    }

    @Test
    fun testDepartmentsDialogTransitions() {
        var state = ProductsUiState()

        // Open departments dialog
        state = state.copy(showDepartmentsDialog = true)
        assertTrue(state.showDepartmentsDialog)

        // Close departments dialog
        state = state.copy(showDepartmentsDialog = false)
        assertFalse(state.showDepartmentsDialog)
    }
}
