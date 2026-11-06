package mx.nexuspos.client.features.products

import mx.nexuspos.contracts.catalog.DepartmentDto
import mx.nexuspos.contracts.catalog.ProductDto

data class ProductsUiState(
    val products: List<ProductDto> = emptyList(),
    val selectedProduct: ProductDto? = null,
    val departments: List<DepartmentDto> = emptyList(),
    val selectedDepartmentId: String? = null,
    val searchQuery: String = "",
    val includeInactive: Boolean = false,
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val showFormDialog: Boolean = false,
    val productToEdit: ProductDto? = null,
    val showDepartmentsDialog: Boolean = false,
    val isDepartmentSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)
