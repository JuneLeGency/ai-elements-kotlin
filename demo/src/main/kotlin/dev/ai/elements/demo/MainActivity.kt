package dev.ai.elements.demo

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ai.elements.demo.data.ThemeMode
import dev.ai.elements.demo.ui.DemoApp
import androidx.compose.runtime.CompositionLocalProvider
import dev.ai.elements.ui.markdown.LocalMermaidRenderer
import dev.ai.elements.ui.markdown.LocalMermaidSizing
import dev.ai.elements.ui.markdown.MermaidRenderer
import dev.ai.elements.ui.markdown.MermaidSizing
import dev.ai.elements.demo.data.DiagramSize
import dev.ai.elements.ui.theme.AiElementsTheme

class MainActivity : ComponentActivity() {
    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val appearance by viewModel.settings.appearance.collectAsStateWithLifecycle()
            val dark = when (appearance.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // System bar icons follow the in-app theme, not just the system night mode.
            DisposableEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            AiElementsTheme(darkTheme = dark, dynamicColor = appearance.dynamicColor) {
                val renderer = if (appearance.nativeMermaid) MermaidRenderer.Native else MermaidRenderer.WebView
                val sizing = when (appearance.diagramSize) {
                    DiagramSize.SMALL -> MermaidSizing.Small
                    DiagramSize.MEDIUM -> MermaidSizing.Medium
                    DiagramSize.LARGE -> MermaidSizing.Large
                }
                CompositionLocalProvider(LocalMermaidRenderer provides renderer, LocalMermaidSizing provides sizing) {
                    DemoApp(viewModel)
                }
            }
            // The window background only covers startup; once Compose has drawn,
            // it paints every pixel itself, so drop the extra full-screen layer.
            LaunchedEffect(Unit) {
                withFrameNanos { }
                window.setBackgroundDrawable(null)
            }
        }
    }
}
