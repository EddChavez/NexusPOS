package mx.nexuspos.client.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import mx.nexuspos.client.theme.NexusTheme

enum class NotificationType {
    SUCCESS,
    ERROR,
    WARNING,
    INFO
}

/**
 * Reusable notification banner for feedback messages across all modules.
 *
 * Automatically dismisses after [autoDismissMillis] (by default 4000ms for SUCCESS, null for ERROR).
 */
@Composable
fun NotificationBanner(
    message: String?,
    type: NotificationType = NotificationType.INFO,
    autoDismissMillis: Long? = if (type == NotificationType.SUCCESS) 4000L else null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (message != null && autoDismissMillis != null) {
        LaunchedEffect(message) {
            delay(autoDismissMillis)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = !message.isNullOrBlank(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        val (bgColor, borderColor, textColor) = when (type) {
            NotificationType.SUCCESS -> Triple(
                NexusTheme.colors.surfaceGreen,
                NexusTheme.colors.borderGreen,
                NexusTheme.colors.textGreen
            )
            NotificationType.ERROR -> Triple(
                NexusTheme.colors.dangerBg,
                NexusTheme.colors.dangerBorder,
                NexusTheme.colors.dangerText
            )
            NotificationType.WARNING -> Triple(
                NexusTheme.colors.warningBg,
                NexusTheme.colors.warningBorder,
                NexusTheme.colors.warningText
            )
            NotificationType.INFO -> Triple(
                NexusTheme.colors.accentLight,
                NexusTheme.colors.accent,
                NexusTheme.colors.textPrimary
            )
        }

        Surface(
            color = bgColor,
            border = BorderStroke(1.dp, borderColor),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = message ?: "",
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (type == NotificationType.SUCCESS) FontWeight.Medium else FontWeight.Normal,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar",
                        tint = textColor
                    )
                }
            }
        }
    }
}

/**
 * Host component to display both error and success feedback messages consistently.
 */
@Composable
fun FeedbackMessageHost(
    errorMessage: String?,
    successMessage: String?,
    onDismissError: () -> Unit,
    onDismissSuccess: () -> Unit,
    autoDismissSuccessMillis: Long? = 4000L,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!errorMessage.isNullOrBlank()) {
            NotificationBanner(
                message = errorMessage,
                type = NotificationType.ERROR,
                autoDismissMillis = null,
                onDismiss = onDismissError
            )
        }
        if (!successMessage.isNullOrBlank()) {
            NotificationBanner(
                message = successMessage,
                type = NotificationType.SUCCESS,
                autoDismissMillis = autoDismissSuccessMillis,
                onDismiss = onDismissSuccess
            )
        }
    }
}
