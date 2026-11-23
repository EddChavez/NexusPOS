package mx.nexuspos.client.features.sales.v2.shortcuts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Insignia visual estilo <kbd> de Material Design 3 para atajos de teclado.
 * Se adapta automáticamente al sistema operativo detectado (macOS vs Windows/Linux).
 */
@Composable
fun ShortcutBadge(
    action: PosAction,
    modifier: Modifier = Modifier,
    os: HostOs = HostOsDetector.current,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = containerColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Box(modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)) {
            Text(
                text = action.getLabel(os),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = contentColor
            )
        }
    }
}
