package dev.ai.elements.demo.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.VerticalDragHandle
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.PaneExpansionState
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldScope
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A list-detail navigator whose layout follows the window: two panes side by
 * side when [twoPane], otherwise one pane at a time with (predictive) back
 * navigation between them. [listWidth] is the list pane's preferred width.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun <T> rememberListDetailNavigator(twoPane: Boolean, listWidth: Dp = 360.dp): ThreePaneScaffoldNavigator<T> {
    val directive = calculatePaneScaffoldDirective(currentWindowAdaptiveInfo()).copy(
        maxHorizontalPartitions = if (twoPane) 2 else 1,
        horizontalPartitionSpacerSize = if (twoPane) 24.dp else 0.dp,
        defaultPanePreferredWidth = listWidth,
    )
    return rememberListDetailPaneScaffoldNavigator<T>(scaffoldDirective = directive)
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
val ThreePaneScaffoldNavigator<*>.isListVisible: Boolean
    get() = scaffoldValue[ListDetailPaneScaffoldRole.List] != PaneAdaptedValue.Hidden

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
val ThreePaneScaffoldNavigator<*>.isDetailVisible: Boolean
    get() = scaffoldValue[ListDetailPaneScaffoldRole.Detail] != PaneAdaptedValue.Hidden

/** The M3 drag handle between panes; drag to resize, with a 48dp touch target. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ThreePaneScaffoldScope.PaneDragHandle(state: PaneExpansionState) {
    val interactionSource = remember { MutableInteractionSource() }
    VerticalDragHandle(
        modifier = Modifier.paneExpansionDraggable(state, LocalMinimumInteractiveComponentSize.current, interactionSource),
        interactionSource = interactionSource,
    )
}
