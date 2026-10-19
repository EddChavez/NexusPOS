package mx.nexuspos.client.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Tokens semánticos oficiales de NexusPOS para Modo Claro (Light) y Modo Oscuro (Slate Carbón).
 * Calibrados con contraste ergonómico WCAG AAA (mínimo 7:1 en textos principales).
 */
object NexusColors {
    // =========================================================================
    // 1. TOKENS BASE / CANVAS & SUPERFICIES
    // =========================================================================

    // Fondo Base (Canvas/Body)
    val BgCanvasLight = Color(0xFFF1F5F9)       // Slate 100
    val BgCanvasDark = Color(0xFF090D16)        // Deep Charcoal

    // Superficie de Tarjetas (Cards/Panels)
    val BgSurfaceLight = Color(0xFFFFFFFF)      // Blanco puro
    val BgSurfaceDark = Color(0xFF0F172A)       // Slate 900

    // Superficie Secundaria / Inputs
    val BgInputLight = Color(0xFFF8FAFC)        // Slate 50
    val BgInputDark = Color(0xFF1E293B)         // Slate 800

    // Bordes Principales
    val BorderPrimaryLight = Color(0xFFCBD5E1)  // Slate 300
    val BorderPrimaryDark = Color(0xFF334155)   // Slate 700

    // Bordes Sutiles / Divisores
    val BorderSubtleLight = Color(0xFFE2E8F0)   // Slate 200
    val BorderSubtleDark = Color(0xFF1E293B)    // Slate 800

    // Texto Primario / Títulos
    val TextPrimaryLight = Color(0xFF0F172A)    // Slate 900
    val TextPrimaryDark = Color(0xFFFFFFFF)     // Blanco Puro

    // Texto Secundario / Etiquetas
    val TextSecondaryLight = Color(0xFF475569)  // Slate 600
    val TextSecondaryDark = Color(0xFF94A3B8)   // Slate 400

    // Texto Atenuado / Placeholders
    val TextMutedLight = Color(0xFF94A3B8)      // Slate 400
    val TextMutedDark = Color(0xFF64748B)       // Slate 500

    // =========================================================================
    // 2. ACENTOS OPERATIVOS Y SEMÁNTICOS
    // =========================================================================

    // Verde Éxito / Cobro (Primary CTA: [F12] Cobrar Orden, Cerrar Turno)
    val ActionSuccessLight = Color(0xFF059669)  // Hover: #047857
    val ActionSuccessDark = Color(0xFF10B981)   // Hover: #059669

    // Verde Superficie / Chips (Báscula ESTABLE, saldo al corriente)
    val SurfaceGreenLight = Color(0xFFDCFCE7)   // Borde: #86efac
    val BorderGreenLight = Color(0xFF86EFAC)
    val SurfaceGreenDark = Color(0xFF064E3B)    // Borde: #059669
    val BorderGreenDark = Color(0xFF059669)

    // Azul / Cian (Focus & Totales: Visores de importe total $72.91, pestañas)
    val HighlightCyanLight = Color(0xFF0284C7)
    val HighlightCyanDark = Color(0xFF38BDF8)
    val HighlightCyanDeepDark = Color(0xFF06B6D4)

    // Rojo Alerta / Peligro (Salidas de efectivo, clientes bloqueados)
    val DangerLight = Color(0xFFDC2626)
    val DangerBgLight = Color(0xFFFEE2E2)
    val DangerDark = Color(0xFFEF4444)
    val DangerBgDark = Color(0xFF450A0A)

    // Ámbar Preventivo (Warning: tickets pendientes, stock crítico)
    val WarningLight = Color(0xFFD97706)
    val WarningBgLight = Color(0xFFFEF3C7)
    val WarningDark = Color(0xFFF59E0B)
    val WarningBgDark = Color(0xFF451A03)

    // Neutro / Botones de Salida / DEL
    val NeutralDelBgLight = Color(0xFFFEE2E2)
    val NeutralDelTextLight = Color(0xFFDC2626)
    val NeutralDelBgDark = Color(0xFF334155)
    val NeutralDelTextDark = Color(0xFFF87171)
}

/**
 * Tokens extendidos que no tienen mapeo 1:1 en Material 3 ColorScheme pero son críticos
 * para la ergonomía operativa de NexusPOS.
 */
@androidx.compose.runtime.Immutable
data class NexusExtendedColors(
    val bgCanvas: Color,
    val bgSurface: Color,
    val bgInput: Color,
    val borderPrimary: Color,
    val borderSubtle: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val actionSuccess: Color,
    val actionSuccessHover: Color,
    val surfaceGreen: Color,
    val borderGreen: Color,
    val highlightCyan: Color,
    val highlightCyanHover: Color,
    val danger: Color,
    val dangerBg: Color,
    val warning: Color,
    val warningBg: Color,
    val neutralDelBg: Color,
    val neutralDelText: Color
)

fun nexusLightExtendedColors() = NexusExtendedColors(
    bgCanvas = NexusColors.BgCanvasLight,
    bgSurface = NexusColors.BgSurfaceLight,
    bgInput = NexusColors.BgInputLight,
    borderPrimary = NexusColors.BorderPrimaryLight,
    borderSubtle = NexusColors.BorderSubtleLight,
    textPrimary = NexusColors.TextPrimaryLight,
    textSecondary = NexusColors.TextSecondaryLight,
    textMuted = NexusColors.TextMutedLight,
    actionSuccess = NexusColors.ActionSuccessLight,
    actionSuccessHover = Color(0xFF047857),
    surfaceGreen = NexusColors.SurfaceGreenLight,
    borderGreen = NexusColors.BorderGreenLight,
    highlightCyan = NexusColors.HighlightCyanLight,
    highlightCyanHover = Color(0xFF0369A1),
    danger = NexusColors.DangerLight,
    dangerBg = NexusColors.DangerBgLight,
    warning = NexusColors.WarningLight,
    warningBg = NexusColors.WarningBgLight,
    neutralDelBg = NexusColors.NeutralDelBgLight,
    neutralDelText = NexusColors.NeutralDelTextLight
)

fun nexusDarkExtendedColors() = NexusExtendedColors(
    bgCanvas = NexusColors.BgCanvasDark,
    bgSurface = NexusColors.BgSurfaceDark,
    bgInput = NexusColors.BgInputDark,
    borderPrimary = NexusColors.BorderPrimaryDark,
    borderSubtle = NexusColors.BorderSubtleDark,
    textPrimary = NexusColors.TextPrimaryDark,
    textSecondary = NexusColors.TextSecondaryDark,
    textMuted = NexusColors.TextMutedDark,
    actionSuccess = NexusColors.ActionSuccessDark,
    actionSuccessHover = Color(0xFF059669),
    surfaceGreen = NexusColors.SurfaceGreenDark,
    borderGreen = NexusColors.BorderGreenDark,
    highlightCyan = NexusColors.HighlightCyanDark,
    highlightCyanHover = Color(0xFF0284C7),
    danger = NexusColors.DangerDark,
    dangerBg = NexusColors.DangerBgDark,
    warning = NexusColors.WarningDark,
    warningBg = NexusColors.WarningBgDark,
    neutralDelBg = NexusColors.NeutralDelBgDark,
    neutralDelText = NexusColors.NeutralDelTextDark
)

val LocalNexusColors = androidx.compose.runtime.staticCompositionLocalOf { nexusLightExtendedColors() }

/**
 * Acceso directo a los tokens de color del tema activo.
 */
object NexusTheme {
    val colors: NexusExtendedColors
        @Composable
        get() = LocalNexusColors.current
}

/**
 * Esquema de color para Modo Claro (Light) basado en Slate 100 y acentos de alto contraste.
 */
fun nexusLightColorScheme(): ColorScheme = lightColorScheme(
    primary = NexusColors.ActionSuccessLight,
    onPrimary = Color.White,
    primaryContainer = NexusColors.SurfaceGreenLight,
    onPrimaryContainer = Color(0xFF065F46),
    secondary = NexusColors.HighlightCyanLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0369A1),
    background = NexusColors.BgCanvasLight,
    onBackground = NexusColors.TextPrimaryLight,
    surface = NexusColors.BgSurfaceLight,
    onSurface = NexusColors.TextPrimaryLight,
    surfaceVariant = NexusColors.BgInputLight,
    onSurfaceVariant = NexusColors.TextSecondaryLight,
    outline = NexusColors.BorderPrimaryLight,
    outlineVariant = NexusColors.BorderSubtleLight,
    error = NexusColors.DangerLight,
    onError = Color.White,
    errorContainer = NexusColors.DangerBgLight,
    onErrorContainer = Color(0xFF991B1B)
)

/**
 * Esquema de color para Modo Oscuro (Slate Carbón) basado en Deep Charcoal (#090D16) y Slate 900 (#0F172A).
 */
fun nexusDarkColorScheme(): ColorScheme = darkColorScheme(
    primary = NexusColors.ActionSuccessDark,
    onPrimary = Color(0xFF090D16),
    primaryContainer = NexusColors.SurfaceGreenDark,
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondary = NexusColors.HighlightCyanDark,
    onSecondary = Color(0xFF090D16),
    secondaryContainer = Color(0xFF083344),
    onSecondaryContainer = Color(0xFFBAE6FD),
    background = NexusColors.BgCanvasDark,
    onBackground = NexusColors.TextPrimaryDark,
    surface = NexusColors.BgSurfaceDark,
    onSurface = NexusColors.TextPrimaryDark,
    surfaceVariant = NexusColors.BgInputDark,
    onSurfaceVariant = NexusColors.TextSecondaryDark,
    outline = NexusColors.BorderPrimaryDark,
    outlineVariant = NexusColors.BorderSubtleDark,
    error = NexusColors.DangerDark,
    onError = Color.White,
    errorContainer = NexusColors.DangerBgDark,
    onErrorContainer = Color(0xFFFCA5A5)
)

/**
 * Proveedor de tema oficial de NexusPOS.
 */
@Composable
fun NexusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) nexusDarkColorScheme() else nexusLightColorScheme()
    val extendedColors = if (darkTheme) nexusDarkExtendedColors() else nexusLightExtendedColors()
    androidx.compose.runtime.CompositionLocalProvider(
        LocalNexusColors provides extendedColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
