package dev.ai.elements.demo.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import dev.ai.elements.demo.ChatViewModel

enum class Destination(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    CHAT("Chat", Icons.AutoMirrored.Outlined.Chat, Icons.AutoMirrored.Filled.Chat),
    COMPONENTS("Components", Icons.Outlined.Widgets, Icons.Filled.Widgets),
    SETTINGS("Settings", Icons.Outlined.Settings, Icons.Filled.Settings),
}

/** Size buckets used across screens (M3 window size classes). */
enum class WidthClass { COMPACT, MEDIUM, EXPANDED }

/**
 * Top level: an adaptive navigation suite — a short navigation bar on phones,
 * a navigation rail on tablets / unfolded foldables.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalLayoutApi::class)
@Composable
fun DemoApp(viewModel: ChatViewModel) {
    var destination by rememberSaveable { mutableStateOf(Destination.CHAT) }
    // Expose testTags as resource ids for UI Automator / adb-driven checks.
    BoxWithConstraints(Modifier.semantics { testTagsAsResourceId = true }) {
        // Phones in landscape are wide but short: never give them the three-pane layout.
        val compactHeight = maxHeight < 480.dp
        // Two panes (list + detail) need room for both; short landscape phones don't get them.
        val twoPane = maxWidth >= 720.dp && !compactHeight
        val widthClass = when {
            maxWidth < 600.dp -> WidthClass.COMPACT
            maxWidth < 840.dp || compactHeight -> WidthClass.MEDIUM
            else -> WidthClass.EXPANDED
        }
        // Phones: a bottom bar, hidden while typing so the composer sits right on the IME.
        val bottomBar = widthClass == WidthClass.COMPACT && !WindowInsets.isImeVisible
        NavigationSuiteScaffold(
            layoutType = when {
                bottomBar -> NavigationSuiteType.ShortNavigationBarCompact
                widthClass == WidthClass.COMPACT -> NavigationSuiteType.None
                else -> NavigationSuiteType.WideNavigationRailCollapsed
            },
            navigationSuiteItems = {
                Destination.entries.forEach { dest ->
                    item(
                        selected = dest == destination,
                        onClick = { destination = dest },
                        icon = { Icon(if (dest == destination) dest.selectedIcon else dest.icon, null) },
                        label = { Text(dest.label) },
                        modifier = Modifier.testTag("nav-${dest.name.lowercase()}"),
                    )
                }
            },
        ) {
            // The bottom bar already pads for the system navigation bar; stop screens adding it again.
            val handled = if (bottomBar) WindowInsets.navigationBars.only(WindowInsetsSides.Bottom) else WindowInsets(0)
            Box(Modifier.consumeWindowInsets(handled)) {
                when (destination) {
                    Destination.CHAT -> ChatScreen(viewModel, widthClass, twoPane, compactHeight, onOpenSettings = { destination = Destination.SETTINGS })
                    Destination.COMPONENTS -> GalleryScreen()
                    Destination.SETTINGS -> SettingsScreen(viewModel, twoPane)
                }
            }
        }
    }
}

/**
 * Screens sit on the navigation suite's background, which is painted once for
 * the whole window; inner Scaffolds and app bars stay transparent so the same
 * pixels aren't filled again (overdraw is expensive on QHD+ 120Hz displays).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun transparentAppBarColors() = TopAppBarDefaults.topAppBarColors(
    containerColor = Color.Transparent,
    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
)
