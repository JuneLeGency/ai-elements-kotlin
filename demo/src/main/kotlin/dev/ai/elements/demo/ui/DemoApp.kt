package dev.ai.elements.demo.ui

import androidx.annotation.StringRes
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.IconButton
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import dev.ai.elements.demo.ChatViewModel
import dev.ai.elements.demo.R

enum class Destination(@StringRes val label: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    CHAT(R.string.nav_chat, Icons.AutoMirrored.Outlined.Chat, Icons.AutoMirrored.Filled.Chat),
    COMPONENTS(R.string.nav_components, Icons.Outlined.Widgets, Icons.Filled.Widgets),
    SETTINGS(R.string.nav_settings, Icons.Outlined.Settings, Icons.Filled.Settings),
}

/** Size buckets used across screens (M3 window size classes). */
enum class WidthClass { COMPACT, MEDIUM, EXPANDED }

/**
 * Top level: a navigation rail on tablets / unfolded foldables. Phones have no
 * navigation bar — like the mainstream chat apps, the chat gets the whole screen and
 * Components / Settings open from its drawer, with Back returning to the chat.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalLayoutApi::class)
@Composable
fun DemoApp(viewModel: ChatViewModel) {
    var destination by rememberSaveable { mutableStateOf(Destination.CHAT) }
    // Expose testTags as resource ids for UI Automator / adb-driven checks.
    BoxWithConstraints(
        Modifier
            .semantics { testTagsAsResourceId = true }
            .noAutoFocusInTouchMode(),
    ) {
        // Phones in landscape are wide but short: never give them the three-pane layout.
        val compactHeight = maxHeight < 480.dp
        val widthClass = when {
            maxWidth < 600.dp -> WidthClass.COMPACT
            maxWidth < 840.dp || compactHeight -> WidthClass.MEDIUM
            else -> WidthClass.EXPANDED
        }
        // List + detail side by side only when Expanded (M3 canonical list-detail): at Medium
        // (e.g. a tablet in portrait, ~800dp) a 360dp list would leave the chat ~350dp wide.
        val twoPane = widthClass == WidthClass.EXPANDED
        val phone = widthClass == WidthClass.COMPACT
        BackHandler(enabled = phone && destination != Destination.CHAT) { destination = Destination.CHAT }
        val back = if (phone) ({ destination = Destination.CHAT }) else null
        NavigationSuiteScaffold(
            layoutType = if (phone) NavigationSuiteType.None else NavigationSuiteType.WideNavigationRailCollapsed,
            navigationSuiteItems = {
                Destination.entries.forEach { dest ->
                    item(
                        selected = dest == destination,
                        onClick = { destination = dest },
                        icon = { Icon(if (dest == destination) dest.selectedIcon else dest.icon, null) },
                        label = { Text(stringResource(dest.label)) },
                        modifier = Modifier.testTag("nav-${dest.name.lowercase()}"),
                    )
                }
            },
        ) {
            Box {
                when (destination) {
                    Destination.CHAT -> ChatScreen(
                        viewModel, widthClass, twoPane, compactHeight,
                        onOpenSettings = { destination = Destination.SETTINGS },
                        onOpenComponents = { destination = Destination.COMPONENTS },
                    )
                    Destination.COMPONENTS -> GalleryScreen(onBack = back)
                    Destination.SETTINGS -> SettingsScreen(viewModel, twoPane, onBack = back)
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

/** The top app bar's back arrow (phones, where Components and Settings are reached from the chat). */
@Composable
internal fun BackArrow(onBack: () -> Unit) {
    IconButton(onClick = onBack, modifier = Modifier.testTag("back")) {
        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
    }
}
