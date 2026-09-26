package dev.ai.elements.demo.ui

import androidx.annotation.FontRes
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import dev.ai.elements.demo.R
import dev.ai.elements.demo.data.AppFont

/**
 * One variable font file serving every weight the type scale uses (Android 8+;
 * Android 7 draws the default instance). Glyphs the font lacks — CJK in Geist
 * or Inter — fall back to the system fonts.
 */
@OptIn(ExperimentalTextApi::class)
private fun variable(@FontRes id: Int) = FontFamily(
    listOf(300, 400, 500, 600, 700).map { weight ->
        Font(id, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))
    },
)

private val Geist by lazy { variable(R.font.geist) }
private val GeistMono by lazy { variable(R.font.geist_mono) }
private val Inter by lazy { variable(R.font.inter) }

/** Screen-optimised Chinese (and Latin) Kai; one weight, bold is synthesised. */
private val WenKai by lazy { FontFamily(Font(R.font.wenkai_gb_screen_subset)) }

/** UI font; null keeps the platform default (Roboto + the system CJK font). */
val AppFont.family: FontFamily?
    get() = when (this) {
        AppFont.SYSTEM -> null
        AppFont.GEIST -> Geist
        AppFont.INTER -> Inter
        AppFont.WENKAI -> WenKai
    }

/** Code font: Geist Mono pairs with Geist; the others keep the platform monospace. */
val AppFont.codeFamily: FontFamily
    get() = if (this == AppFont.GEIST) GeistMono else FontFamily.Monospace
