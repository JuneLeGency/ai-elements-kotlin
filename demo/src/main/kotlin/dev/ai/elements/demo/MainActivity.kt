package dev.ai.elements.demo

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ai.elements.demo.data.AppLocale
import dev.ai.elements.demo.data.DiagramSize
import dev.ai.elements.demo.data.ThemeMode
import dev.ai.elements.demo.ui.DemoApp
import dev.ai.elements.demo.ui.codeFamily
import dev.ai.elements.demo.ui.family
import dev.ai.elements.mermaid.NativeMermaidRenderer
import dev.ai.elements.ui.markdown.LocalMermaidRenderer
import dev.ai.elements.ui.markdown.LocalMermaidSizing
import dev.ai.elements.ui.markdown.MermaidRenderer
import dev.ai.elements.ui.markdown.MermaidSizing
import dev.ai.elements.ui.theme.AiElementsTheme

class MainActivity : ComponentActivity() {
    private val viewModel: ChatViewModel by viewModels()

    // Android 12 and below: apply the in-app language (13+ does it natively).
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLocale.wrap(newBase))

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
            AiElementsTheme(
                darkTheme = dark,
                dynamicColor = appearance.dynamicColor,
                palette = appearance.palette,
                contrast = appearance.contrast,
                fontFamily = appearance.font.family,
                codeFontFamily = appearance.font.codeFamily,
            ) {
                val renderer = if (appearance.nativeMermaid) NativeMermaidRenderer else MermaidRenderer.WebView
                val sizing = when (appearance.diagramSize) {
                    DiagramSize.SMALL -> MermaidSizing.Small
                    DiagramSize.MEDIUM -> MermaidSizing.Medium
                    DiagramSize.LARGE -> MermaidSizing.Large
                }
                // In-app text size multiplies the system font scale, so every sp in the app follows it.
                val density = LocalDensity.current
                val scaled = remember(density, appearance.textSize) {
                    Density(density.density, density.fontScale * appearance.textSize.scale)
                }
                CompositionLocalProvider(
                    LocalMermaidRenderer provides renderer,
                    LocalMermaidSizing provides sizing,
                    LocalDensity provides scaled,
                ) {
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
