package dev.ai.elements.demo.ui

import android.os.SystemClock
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager

/**
 * In touch mode, don't let focus be moved *into* this subtree unless a finger
 * just went down inside it. Automatic moves — the window gaining focus,
 * `ThreePaneScaffold` focusing the pane it navigated to — otherwise land on
 * the first text field and raise the soft keyboard unasked. Tapping a field
 * here still focuses it, and in keyboard mode (Tab, D-pad) or with TalkBack
 * (whose double-tap is an accessibility click, not a touch) focus enters as usual.
 */
@Composable
fun Modifier.noAutoFocusInTouchMode(): Modifier {
    val inputModeManager = LocalInputModeManager.current
    val accessibility = LocalContext.current.getSystemService(AccessibilityManager::class.java)
    val lastDownAt = remember { LongArray(1) }
    return pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            lastDownAt[0] = SystemClock.uptimeMillis()
        }
    }.focusProperties {
        onEnter = {
            val touchedHere = SystemClock.uptimeMillis() - lastDownAt[0] < TAP_WINDOW_MS
            val talkBack = accessibility?.isTouchExplorationEnabled == true
            if (inputModeManager.inputMode == InputMode.Touch && !touchedHere && !talkBack) cancelFocusChange()
        }
    }.focusGroup()
}

/** A tap focuses on release; long enough for a slow tap, short enough to exclude navigation. */
private const val TAP_WINDOW_MS = 1_000L
