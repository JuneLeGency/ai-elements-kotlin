package dev.ai.elements.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ai.elements.ui.theme.AiSize
import dev.ai.elements.ui.theme.AiSpacing

/**
 * Human-in-the-loop approval (AI Elements `<Confirmation>`): what the agent
 * wants to do, with approve / deny actions. Null callbacks render read-only.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Confirmation(
    title: String,
    description: String,
    onApprove: (() -> Unit)?,
    onDeny: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth().testTag("confirmation"),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(AiSpacing.s), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.PanTool, null, Modifier.size(AiSize.compactIcon))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(description, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            if (onApprove != null && onDeny != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onDeny, shapes = ButtonDefaults.shapes(), modifier = Modifier.testTag("deny")) {
                        Icon(Icons.Outlined.Close, null, Modifier.size(ButtonDefaults.IconSize))
                        Text("Deny", Modifier.padding(start = ButtonDefaults.IconSpacing))
                    }
                    Button(onClick = onApprove, shapes = ButtonDefaults.shapes(), modifier = Modifier.testTag("approve")) {
                        Icon(Icons.Outlined.Check, null, Modifier.size(ButtonDefaults.IconSize))
                        Text("Approve", Modifier.padding(start = ButtonDefaults.IconSpacing))
                    }
                }
            }
        }
    }
}
