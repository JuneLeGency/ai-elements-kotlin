package dev.ai.elements.demo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.ai.elements.demo.R
import dev.ai.elements.ui.theme.AiContrast
import dev.ai.elements.ui.theme.AiPalette
import dev.ai.elements.ui.theme.aiColorScheme
import dev.ai.elements.ui.theme.isDark

/**
 * Palette swatches like the system wallpaper-color picker: each shows the
 * palette's own primary, secondary and tertiary containers in the current
 * light/dark mode. Radio semantics for accessibility.
 */
@Composable
fun PalettePicker(selected: AiPalette, enabled: Boolean, onSelect: (AiPalette) -> Unit, modifier: Modifier = Modifier) {
    val dark = MaterialTheme.isDark
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .selectableGroup()
            .alpha(if (enabled) 1f else 0.38f),
    ) {
        AiPalette.entries.forEach { palette ->
            val scheme = aiColorScheme(palette, dark, AiContrast.STANDARD)
            val name = stringResource(palette.label)
            val isSelected = palette == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(52.dp)
                    .border(if (isSelected) 3.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .padding(5.dp)
                    .selectable(selected = isSelected, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(palette) })
                    .semantics { contentDescription = name }
                    .testTag("palette-${palette.name.lowercase()}"),
            ) {
                Canvas(Modifier.size(42.dp)) {
                    val d = size.minDimension
                    drawArc(scheme.primary, 180f, 180f, useCenter = true, size = Size(d, d))
                    drawArc(scheme.secondaryContainer, 90f, 90f, useCenter = true, size = Size(d, d))
                    drawArc(scheme.tertiaryContainer, 0f, 90f, useCenter = true, size = Size(d, d))
                    drawCircle(scheme.surfaceContainerHighest, radius = d * 0.18f, center = Offset(d / 2, d / 2))
                }
                if (isSelected) Icon(Icons.Outlined.Check, null, Modifier.size(18.dp), tint = scheme.onSurface)
            }
        }
    }
}

val AiPalette.label: Int
    get() = when (this) {
        AiPalette.VIOLET -> R.string.palette_violet
        AiPalette.OCEAN -> R.string.palette_ocean
        AiPalette.JADE -> R.string.palette_jade
        AiPalette.FOREST -> R.string.palette_forest
        AiPalette.SUNSET -> R.string.palette_sunset
        AiPalette.SAKURA -> R.string.palette_sakura
        AiPalette.GRAPHITE -> R.string.palette_graphite
    }
