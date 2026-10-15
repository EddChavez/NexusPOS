package mx.nexuspos.core

enum class ProductUnit(val code: String, val label: String) {
    PIECE("PZA", "Pieza"),
    KILOGRAM("KG", "Kilogramo"),
    LITER("LT", "Litro");

    companion object {
        fun fromCode(code: String): ProductUnit {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) }
                ?: entries.firstOrNull { it.name.equals(code, ignoreCase = true) }
                ?: PIECE
        }
    }
}
