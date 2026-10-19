package mx.nexuspos.client.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Tokens semánticos oficiales de NexusPOS para Modo Oscuro (Dark - Slate Carbón)
 * y Modo Claro (Light - Alto Contraste).
 * Calibrados con ergonomía visual y alto contraste WCAG AAA para terminales POS.
 */
object NexusColors {
    // =========================================================================
    // 1. LIENZO / BACKGROUND BASE & SUPERFICIES
    // =========================================================================

    // Lienzo / Background Base
    val BgCanvasLight = Color(0xFFF8F9FF)       // Gris hielo suave
    val BgCanvasDark = Color(0xFF0B1326)        // Azul marino profundo

    // Superficie / Cards Elevadas
    val BgSurfaceLight = Color(0xFFFFFFFF)      // Blanco puro
    val BgSurfaceDark = Color(0xFF131B2E)       // Slate elevado

    // Superficie Contenedora Baja (Inputs / Fondos de tablas / Celdas alternadas)
    val BgSurfaceSubtleLight = Color(0xFFEFF4FF) // Azul neutro tenue
    val BgSurfaceSubtleDark = Color(0xFF060E20)  // Negro pizarra
    val BgInputLight = BgSurfaceSubtleLight
    val BgInputDark = BgSurfaceSubtleDark

    // Bordes & Separadores
    val BorderPrimaryLight = Color(0xFFCBD5E1)  // Gris delimitador
    val BorderSubtleLight = Color(0xFFCBDBF5)   // Gris/azul delimitador sutil
    val BorderPrimaryDark = Color(0xFF1E293B)   // Slate delimitador
    val BorderSubtleDark = Color(0xFF334155)    // Slate sutil

    // =========================================================================
    // 2. TEXTO Y TIPOGRAFÍA
    // =========================================================================

    // Texto Primario / Títulos
    val TextPrimaryLight = Color(0xFF0F172A)    // Slate 900 oscuro
    val TextPrimaryDark = Color(0xFFFFFFFF)     // Blanco puro

    // Texto Secundario / Labels
    val TextSecondaryLight = Color(0xFF64748B)  // Slate 500 neutro
    val TextSecondaryDark = Color(0xFF94A3B8)   // Slate 400 claro

    // Texto Atenuado / Placeholders
    val TextMutedLight = Color(0xFF94A3B8)      // Slate 400
    val TextMutedDark = Color(0xFF64748B)       // Slate 500

    // =========================================================================
    // 3. ACCIONES OPERATIVAS Y ACENTOS
    // =========================================================================

    // Acción Primaria (Cobrar/Enter) - [F12] COBRAR, ENTER Añadir
    val ActionPrimaryLight = Color(0xFF059669)      // Verde esmeralda sólido
    val ActionPrimaryHoverLight = Color(0xFF047857)
    val ActionPrimaryFgLight = Color(0xFFFFFFFF)
    val ActionPrimaryDark = Color(0xFF10B981)       // Verde esmeralda vibrante
    val ActionPrimaryHoverDark = Color(0xFF059669)
    val ActionPrimaryFgDark = Color(0xFF022C22)

    // Acento Activo & Foco - Pestaña activa, borde del buscador, báscula
    val AccentLight = Color(0xFF0284C7)             // Azul corporativo brillante
    val AccentLightSubtle = Color(0xFFE0F2FE)       // Fondo tenue de acento
    val AccentDark = Color(0xFF38BDF8)              // Cyan neón / Azul eléctrico
    val AccentDarkSubtle = Color(0x2638BDF8)        // rgba(56, 189, 248, 0.15)

    // Display Numérico (Total gigante y cambio)
    val TotalDisplayLight = Color(0xFF0369A1)       // Azul / Cyan contraste
    val TotalDisplayDark = Color(0xFF38BDF8)        // Cyan vibrante

    // Acción Destructiva (Eliminar/Salir) - [DEL] Quitar, [Esc] Salir
    val DangerBgLight = Color(0xFFFEE2E2)
    val DangerBorderLight = Color(0xFFFCA5A5)
    val DangerTextLight = Color(0xFFDC2626)

    val DangerBgDark = Color(0xFF450A0A)
    val DangerBorderDark = Color(0xFF991B1B)
    val DangerTextDark = Color(0xFFF87171)

    // Botones Secundarios (Outline) - [F4] Cantidad, [F8] Descuento, etc.
    val BtnSecondaryBorderLight = Color(0xFFCBD5E1)
    val BtnSecondaryTextLight = Color(0xFF334155)

    val BtnSecondaryBorderDark = Color(0xFF334155)
    val BtnSecondaryTextDark = Color(0xFFCBD5E1)

    // Alertas / Stock Crítico / Warning (Ámbar)
    val WarningBgLight = Color(0xFFFEF3C7)
    val WarningTextLight = Color(0xFFD97706)
    val WarningBorderLight = Color(0xFFFDE68A)

    val WarningBgDark = Color(0xFF451A03)
    val WarningTextDark = Color(0xFFF59E0B)
    val WarningBorderDark = Color(0xFF78350F)

    // Chips y Báscula (Verde menta / Superficie verde)
    val SurfaceGreenLight = Color(0xFFDCFCE7)
    val BorderGreenLight = Color(0xFF86EFAC)
    val TextGreenLight = Color(0xFF166534)

    val SurfaceGreenDark = Color(0xFF064E3B)
    val BorderGreenDark = Color(0xFF059669)
    val TextGreenDark = Color(0xFFA7F3D0)

    // Aliases para compatibilidad con código existente
    val ActionSuccessLight = ActionPrimaryLight
    val ActionSuccessDark = ActionPrimaryDark
    val HighlightCyanLight = AccentLight
    val HighlightCyanDark = AccentDark
    val HighlightCyanDeepDark = TotalDisplayDark
    val DangerLight = DangerTextLight
    val DangerDark = DangerTextDark
    val WarningLight = WarningTextLight
    val WarningDark = WarningTextDark
    val NeutralDelBgLight = DangerBgLight
    val NeutralDelTextLight = DangerTextLight
    val NeutralDelBgDark = DangerBgDark
    val NeutralDelTextDark = DangerTextDark
}

/**
 * Tokens extendidos que no tienen mapeo 1:1 en Material 3 ColorScheme pero son críticos
 * para la ergonomía operativa de NexusPOS.
 */
@Immutable
data class NexusExtendedColors(
    val bgCanvas: Color,
    val bgSurface: Color,
    val bgSurfaceSubtle: Color,
    val borderPrimary: Color,
    val borderSubtle: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val actionPrimary: Color,
    val actionPrimaryHover: Color,
    val actionPrimaryFg: Color,
    val accent: Color,
    val accentLight: Color,
    val dangerBg: Color,
    val dangerBorder: Color,
    val dangerText: Color,
    val btnSecondaryBorder: Color,
    val btnSecondaryText: Color,
    val totalDisplay: Color,
    val warningBg: Color,
    val warningText: Color,
    val warningBorder: Color,
    val surfaceGreen: Color,
    val borderGreen: Color,
    val textGreen: Color,
    // Propiedades heredadas para compatibilidad con llamadas existentes
    val bgInput: Color = bgSurfaceSubtle,
    val actionSuccess: Color = actionPrimary,
    val actionSuccessHover: Color = actionPrimaryHover,
    val highlightCyan: Color = accent,
    val highlightCyanHover: Color = totalDisplay,
    val danger: Color = dangerText,
    val neutralDelBg: Color = dangerBg,
    val neutralDelText: Color = dangerText,
    val warning: Color = warningText
)

fun nexusLightExtendedColors() = NexusExtendedColors(
    bgCanvas = NexusColors.BgCanvasLight,
    bgSurface = NexusColors.BgSurfaceLight,
    bgSurfaceSubtle = NexusColors.BgSurfaceSubtleLight,
    borderPrimary = NexusColors.BorderPrimaryLight,
    borderSubtle = NexusColors.BorderSubtleLight,
    textPrimary = NexusColors.TextPrimaryLight,
    textSecondary = NexusColors.TextSecondaryLight,
    textMuted = NexusColors.TextMutedLight,
    actionPrimary = NexusColors.ActionPrimaryLight,
    actionPrimaryHover = NexusColors.ActionPrimaryHoverLight,
    actionPrimaryFg = NexusColors.ActionPrimaryFgLight,
    accent = NexusColors.AccentLight,
    accentLight = NexusColors.AccentLightSubtle,
    dangerBg = NexusColors.DangerBgLight,
    dangerBorder = NexusColors.DangerBorderLight,
    dangerText = NexusColors.DangerTextLight,
    btnSecondaryBorder = NexusColors.BtnSecondaryBorderLight,
    btnSecondaryText = NexusColors.BtnSecondaryTextLight,
    totalDisplay = NexusColors.TotalDisplayLight,
    warningBg = NexusColors.WarningBgLight,
    warningText = NexusColors.WarningTextLight,
    warningBorder = NexusColors.WarningBorderLight,
    surfaceGreen = NexusColors.SurfaceGreenLight,
    borderGreen = NexusColors.BorderGreenLight,
    textGreen = NexusColors.TextGreenLight
)

fun nexusDarkExtendedColors() = NexusExtendedColors(
    bgCanvas = NexusColors.BgCanvasDark,
    bgSurface = NexusColors.BgSurfaceDark,
    bgSurfaceSubtle = NexusColors.BgSurfaceSubtleDark,
    borderPrimary = NexusColors.BorderPrimaryDark,
    borderSubtle = NexusColors.BorderSubtleDark,
    textPrimary = NexusColors.TextPrimaryDark,
    textSecondary = NexusColors.TextSecondaryDark,
    textMuted = NexusColors.TextMutedDark,
    actionPrimary = NexusColors.ActionPrimaryDark,
    actionPrimaryHover = NexusColors.ActionPrimaryHoverDark,
    actionPrimaryFg = NexusColors.ActionPrimaryFgDark,
    accent = NexusColors.AccentDark,
    accentLight = NexusColors.AccentDarkSubtle,
    dangerBg = NexusColors.DangerBgDark,
    dangerBorder = NexusColors.DangerBorderDark,
    dangerText = NexusColors.DangerTextDark,
    btnSecondaryBorder = NexusColors.BtnSecondaryBorderDark,
    btnSecondaryText = NexusColors.BtnSecondaryTextDark,
    totalDisplay = NexusColors.TotalDisplayDark,
    warningBg = NexusColors.WarningBgDark,
    warningText = NexusColors.WarningTextDark,
    warningBorder = NexusColors.WarningBorderDark,
    surfaceGreen = NexusColors.SurfaceGreenDark,
    borderGreen = NexusColors.BorderGreenDark,
    textGreen = NexusColors.TextGreenDark
)

val LocalNexusColors = staticCompositionLocalOf { nexusLightExtendedColors() }

/**
 * Acceso directo a los tokens de color del tema activo.
 */
object NexusTheme {
    val colors: NexusExtendedColors
        @Composable
        get() = LocalNexusColors.current
}

/**
 * Esquema de color para Modo Claro (Light - Alto Contraste).
 */
fun nexusLightColorScheme(): ColorScheme = lightColorScheme(
    primary = NexusColors.ActionPrimaryLight,
    onPrimary = NexusColors.ActionPrimaryFgLight,
    primaryContainer = NexusColors.SurfaceGreenLight,
    onPrimaryContainer = NexusColors.TextGreenLight,
    secondary = NexusColors.AccentLight,
    onSecondary = Color.White,
    secondaryContainer = NexusColors.AccentLightSubtle,
    onSecondaryContainer = NexusColors.TotalDisplayLight,
    background = NexusColors.BgCanvasLight,
    onBackground = NexusColors.TextPrimaryLight,
    surface = NexusColors.BgSurfaceLight,
    onSurface = NexusColors.TextPrimaryLight,
    surfaceVariant = NexusColors.BgSurfaceSubtleLight,
    onSurfaceVariant = NexusColors.TextSecondaryLight,
    outline = NexusColors.BorderPrimaryLight,
    outlineVariant = NexusColors.BorderSubtleLight,
    error = NexusColors.DangerTextLight,
    onError = Color.White,
    errorContainer = NexusColors.DangerBgLight,
    onErrorContainer = Color(0xFF991B1B)
)

/**
 * Esquema de color para Modo Oscuro (Dark - Slate Carbón).
 */
fun nexusDarkColorScheme(): ColorScheme = darkColorScheme(
    primary = NexusColors.ActionPrimaryDark,
    onPrimary = NexusColors.ActionPrimaryFgDark,
    primaryContainer = NexusColors.SurfaceGreenDark,
    onPrimaryContainer = NexusColors.TextGreenDark,
    secondary = NexusColors.AccentDark,
    onSecondary = Color(0xFF0B1326),
    secondaryContainer = Color(0xFF083344),
    onSecondaryContainer = Color(0xFFBAE6FD),
    background = NexusColors.BgCanvasDark,
    onBackground = NexusColors.TextPrimaryDark,
    surface = NexusColors.BgSurfaceDark,
    onSurface = NexusColors.TextPrimaryDark,
    surfaceVariant = NexusColors.BgSurfaceSubtleDark,
    onSurfaceVariant = NexusColors.TextSecondaryDark,
    outline = NexusColors.BorderPrimaryDark,
    outlineVariant = NexusColors.BorderSubtleDark,
    error = NexusColors.DangerTextDark,
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

