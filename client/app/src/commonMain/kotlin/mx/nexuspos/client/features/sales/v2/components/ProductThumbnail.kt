package mx.nexuspos.client.features.sales.v2.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Componente de miniatura visual para productos en Compose Desktop.
 * Utiliza avatar procedural con icono temático por departamento e iniciales
 * cuando la imagen física/URL no está configurada.
 */
@Composable
fun ProductThumbnail(
    name: String,
    category: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    imageUrl: String? = null
) {
    val (bgColor, iconVector) = getCategoryStyling(category)
    val initials = name.trim().split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .map { it.first().uppercaseChar() }
        .joinToString("")
        .ifBlank { "PR" }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor.copy(alpha = 0.18f),
        modifier = modifier.size(size)
    ) {
        Box(
            modifier = Modifier.size(size),
            contentAlignment = Alignment.Center
        ) {
            if (size >= 40.dp) {
                Box(
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = null,
                        tint = bgColor.copy(alpha = 0.25f),
                        modifier = Modifier.size(size * 0.7f)
                    )
                }
            }

            Text(
                text = initials,
                fontSize = if (size < 40.dp) 11.sp else 14.sp,
                fontWeight = FontWeight.Black,
                color = bgColor
            )
        }
    }
}

private fun getCategoryStyling(category: String): Pair<Color, ImageVector> {
    val lower = category.lowercase()
    return when {
        lower.contains("carne") || lower.contains("res") || lower.contains("pollo") || lower.contains("cerdo") ->
            Pair(Color(0xFFE11D48), Icons.Default.Restaurant) // Carmesí / Carne
        lower.contains("bebida") || lower.contains("refresco") || lower.contains("jugo") || lower.contains("agua") ->
            Pair(Color(0xFF0284C7), Icons.Default.LocalDrink) // Azul / Bebidas
        lower.contains("lacteo") || lower.contains("leche") || lower.contains("queso") || lower.contains("crema") ->
            Pair(Color(0xFFF59E0B), Icons.Default.Egg) // Ámbar / Lácteos
        lower.contains("fruta") || lower.contains("verdura") || lower.contains("legumbre") ->
            Pair(Color(0xFF10B981), Icons.Default.Eco) // Verde esmeralda / Granel
        lower.contains("pan") || lower.contains("tortilla") || lower.contains("harina") ->
            Pair(Color(0xFFD97706), Icons.Default.BakeryDining) // Naranja tostado / Panadería
        lower.contains("limpieza") || lower.contains("higiene") || lower.contains("jabon") ->
            Pair(Color(0xFF06B6D4), Icons.Default.CleaningServices) // Cyan / Limpieza
        else ->
            Pair(Color(0xFF6366F1), Icons.Default.ShoppingBag) // Índigo corporativo / Abarrotes
    }
}
