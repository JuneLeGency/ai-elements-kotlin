package dev.ai.elements.ui.confirmation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * The state of a tool approval, mirroring the web `ToolUIPart["state"]` values.
 */
enum class ConfirmationState {
    /** The model is still streaming the input — nothing to confirm yet. */
    INPUT_STREAMING,
    /** Input is ready but approval hasn't been requested. */
    INPUT_AVAILABLE,
    /** The model is asking the user to approve / deny the action. */
    APPROVAL_REQUESTED,
    /** The user has responded to the request. */
    APPROVAL_RESPONDED,
    /** The tool ran and produced output. */
    OUTPUT_AVAILABLE,
    /** The tool ran and produced a denied result. */
    OUTPUT_DENIED,
}

/**
 * The "Confirmation" block, mirroring the web `Confirmation` component (an
 * `Alert` that asks the user to approve / deny a tool call, then shows the
 * accepted or rejected state).
 *
 * The web uses a context so `ConfirmationRequest` / `ConfirmationAccepted` /
 * `ConfirmationRejected` can each render only when the right [state] is active.
 * In Compose we fold that into a single [Confirmation] composable:
 *
 * - [state] == [ConfirmationState.APPROVAL_REQUESTED] → shows [requestText] and
 *   the [onApprove] / [onDeny] buttons.
 * - [state] == APPROVAL_RESPONDED / OUTPUT_* + [approved] == true → [acceptedText].
 * - [state] == APPROVAL_RESPONDED / OUTPUT_* + [approved] == false → [rejectedText].
 * - input-streaming / input-available → renders nothing (the web returns `null`).
 *
 * @param state the current approval state.
 * @param approved whether the user approved (only meaningful once responded).
 * @param requestText the prompt shown while approval is requested.
 * @param acceptedText the confirmation shown after approval.
 * @param rejectedText the confirmation shown after rejection.
 * @param reason an optional reason the model supplied with the response.
 * @param onApprove invoked when the user taps "Approve".
 * @param onDeny invoked when the user taps "Deny".
 */
@Composable
fun Confirmation(
    state: ConfirmationState,
    approved: Boolean = false,
    modifier: Modifier = Modifier,
    requestText: String = "This tool wants to run an action. Approve it?",
    acceptedText: String = "Approved.",
    rejectedText: String = "Rejected.",
    reason: String? = null,
    onApprove: () -> Unit = {},
    onDeny: () -> Unit = {},
) {
    // The web returns `null` before an approval is actually requested.
    if (state == ConfirmationState.INPUT_STREAMING ||
        state == ConfirmationState.INPUT_AVAILABLE
    ) {
        return
    }

    val isRequesting = state == ConfirmationState.APPROVAL_REQUESTED
    val icon: ImageVector = when {
        isRequesting -> Icons.Filled.Warning
        approved -> Icons.Filled.Check
        else -> Icons.Filled.Close
    }
    val accent: Color = when {
        isRequesting -> MaterialTheme.colorScheme.primary
        approved -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }
    val bodyText: String = when {
        isRequesting -> requestText
        approved -> acceptedText
        else -> rejectedText
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.08f))
            .border(1.dp, accent.copy(alpha = 0.4f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = bodyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 10.dp),
            )
        }

        if (reason != null && !isRequesting) {
            Text(
                text = reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (isRequesting) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onDeny,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    modifier = Modifier.height(36.dp),
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Text("Deny", modifier = Modifier.padding(start = 6.dp))
                }
                Button(
                    onClick = onApprove,
                    modifier = Modifier.padding(start = 8.dp).height(36.dp),
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Text("Approve", modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}
