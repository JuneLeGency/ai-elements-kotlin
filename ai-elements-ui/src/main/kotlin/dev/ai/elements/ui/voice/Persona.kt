package dev.ai.elements.ui.voice

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import dev.ai.elements.ui.R
import kotlin.math.floor

/** What a voice agent is doing; drives [Persona]'s animation. */
enum class PersonaState { IDLE, LISTENING, THINKING, SPEAKING, ASLEEP }

/**
 * An animated agent presence for voice UIs (AI Elements `<Persona>`), drawn
 * with Material 3 Expressive shapes instead of a Rive file: it breathes when
 * idle, ripples while listening, morphs through shapes while thinking, pulses
 * with [level] (0–1, e.g. output loudness) while speaking, and dims asleep.
 * State changes morph from the previous shape.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Persona(
    state: PersonaState,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    level: () -> Float = { 0f },
) {
    val colors = MaterialTheme.colorScheme
    val thinkingShapes = remember { listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Sunny, MaterialShapes.Pentagon, MaterialShapes.Clover4Leaf) }
    val restShape = shapeFor(state)

    // Morph from the previous state's shape to this one.
    var previous by remember { mutableStateOf(restShape) }
    val change = remember { Animatable(1f) }
    val stateMorph = remember(previous, restShape) { Morph(previous, restShape) }
    LaunchedEffect(state) {
        change.snapTo(0f)
        change.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 300f))
        previous = restShape
    }

    val infinite = rememberInfiniteTransition(label = "persona")
    val spin by infinite.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(if (state == PersonaState.THINKING) 2_800 else 24_000, easing = LinearEasing)),
        label = "spin",
    )
    val breathe by infinite.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(if (state == PersonaState.ASLEEP) 4_200 else 2_400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe",
    )
    val cycle by infinite.animateFloat(0f, thinkingShapes.size.toFloat(), infiniteRepeatable(tween(thinkingShapes.size * 650, easing = LinearEasing)), label = "cycle")
    val ripple by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(1_600, easing = LinearEasing)), label = "ripple")
    val thinkingMorphs = remember { thinkingShapes.indices.map { Morph(thinkingShapes[it], thinkingShapes[(it + 1) % thinkingShapes.size]) } }
    val speakingMorph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie12Sided) }
    val androidPath = remember { android.graphics.Path() }

    val brush = if (state == PersonaState.ASLEEP) {
        Brush.linearGradient(listOf(colors.surfaceVariant, colors.outlineVariant))
    } else {
        Brush.sweepGradient(listOf(colors.primary, colors.tertiary, colors.secondary, colors.primary))
    }
    val description = stringResource(
        when (state) {
            PersonaState.IDLE -> R.string.ai_persona_idle
            PersonaState.LISTENING -> R.string.ai_persona_listening
            PersonaState.THINKING -> R.string.ai_persona_thinking
            PersonaState.SPEAKING -> R.string.ai_persona_speaking
            PersonaState.ASLEEP -> R.string.ai_persona_asleep
        },
    )

    Canvas(modifier.size(size).semantics { contentDescription = description }.testTag("persona")) {
        val loud = level().coerceIn(0f, 1f)
        val scale = when (state) {
            PersonaState.IDLE -> 0.9f + 0.04f * breathe
            PersonaState.LISTENING -> 0.84f + 0.1f * loud
            PersonaState.THINKING -> 0.86f
            PersonaState.SPEAKING -> 0.84f + 0.12f * loud
            PersonaState.ASLEEP -> 0.78f + 0.03f * breathe
        }
        if (state == PersonaState.LISTENING) {
            // Two staggered rings drifting outwards.
            for (offset in listOf(0f, 0.5f)) {
                val t = (ripple + offset) % 1f
                drawCircle(colors.primary.copy(alpha = 0.35f * (1f - t)), radius = this.size.minDimension / 2 * (0.8f + 0.2f * t), style = Stroke(width = 2.dp.toPx()))
            }
        }
        val path = when (state) {
            PersonaState.THINKING -> {
                val i = floor(cycle).toInt() % thinkingMorphs.size
                thinkingMorphs[i].toPath(cycle - floor(cycle), androidPath)
            }
            PersonaState.SPEAKING -> speakingMorph.toPath(loud, androidPath)
            else -> stateMorph.toPath(change.value.coerceIn(0f, 1f), androidPath)
        }.asComposePath()
        val w = this.size.width
        val h = this.size.height
        withTransform({
            rotate(spin, pivot = center)
            scale(scale, scale, pivot = center)
            scale(w, h, pivot = Offset.Zero)
        }) {
            drawPath(path, brush)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun shapeFor(state: PersonaState): RoundedPolygon = when (state) {
    PersonaState.IDLE -> MaterialShapes.Cookie9Sided
    PersonaState.LISTENING -> MaterialShapes.SoftBurst
    PersonaState.THINKING -> MaterialShapes.Cookie9Sided
    PersonaState.SPEAKING -> MaterialShapes.Circle
    PersonaState.ASLEEP -> MaterialShapes.Circle
}
