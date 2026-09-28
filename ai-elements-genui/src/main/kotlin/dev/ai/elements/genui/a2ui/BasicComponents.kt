package dev.ai.elements.genui.a2ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.ai.elements.core.model.FilePart
import dev.ai.elements.genui.icons.GenUiIcons
import dev.ai.elements.ui.chat.FileImage
import dev.ai.elements.ui.markdown.MarkdownContent
import dev.ai.elements.ui.voice.AudioPlayer
import dev.ai.elements.ui.voice.rememberAudioPlayerState
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The A2UI Basic Catalog in Material 3, per the basic catalog implementation guide: invisible
 * containers add no spacing, leaves and outlined containers carry a uniform [LeafMargin], colours
 * come from the theme (`LocalContentColor`), cards are outlined so nesting stays readable.
 */
object BasicComponents {
    val LeafMargin: Dp = 6.dp

    val all: Map<String, A2uiComponent> = mapOf(
        "Text" to A2uiComponent { s, m -> TextComponent(s, m) },
        "Image" to A2uiComponent { s, m -> ImageComponent(s, m) },
        "Icon" to A2uiComponent { s, m -> IconComponent(s, m) },
        "Video" to A2uiComponent { s, m -> VideoComponent(s, m) },
        "AudioPlayer" to A2uiComponent { s, m -> AudioComponent(s, m) },
        "Row" to A2uiComponent { s, m -> RowComponent(s, m) },
        "Column" to A2uiComponent { s, m -> ColumnComponent(s, m) },
        "List" to A2uiComponent { s, m -> ListComponent(s, m) },
        "Card" to A2uiComponent { s, m -> OutlinedCard(m.padding(LeafMargin).fillWidth()) { Box(Modifier.padding(10.dp)) { s.Render(s.child()) } } },
        "Tabs" to A2uiComponent { s, m -> TabsComponent(s, m) },
        "Modal" to A2uiComponent { s, m -> ModalComponent(s, m) },
        "Divider" to A2uiComponent { s, m ->
            if (s.string("axis") == "vertical") VerticalDivider(m.height(24.dp).padding(horizontal = LeafMargin))
            else HorizontalDivider(m.padding(vertical = LeafMargin))
        },
        "Button" to A2uiComponent { s, m -> ButtonComponent(s, m) },
        "TextField" to A2uiComponent { s, m -> TextFieldComponent(s, m) },
        "CheckBox" to A2uiComponent { s, m -> CheckBoxComponent(s, m) },
        "ChoicePicker" to A2uiComponent { s, m -> ChoicePickerComponent(s, m) },
        "Slider" to A2uiComponent { s, m -> SliderComponent(s, m) },
        "DateTimeInput" to A2uiComponent { s, m -> DateTimeComponent(s, m) },
    )

    /** Basic Catalog icon names → Material icons. */
    val icons: Map<String, ImageVector> = mapOf(
        "accountCircle" to GenUiIcons.AccountCircle, "add" to GenUiIcons.Add, "arrowBack" to GenUiIcons.ArrowBack,
        "arrowForward" to GenUiIcons.ArrowForward, "attachFile" to GenUiIcons.AttachFile, "calendarToday" to GenUiIcons.CalendarToday,
        "call" to GenUiIcons.Call, "camera" to GenUiIcons.CameraAlt, "check" to GenUiIcons.Check, "close" to GenUiIcons.Close,
        "delete" to GenUiIcons.Delete, "download" to GenUiIcons.Download, "edit" to GenUiIcons.Edit, "event" to GenUiIcons.Event,
        "error" to GenUiIcons.ErrorOutline, "fastForward" to GenUiIcons.FastForward, "favorite" to GenUiIcons.Favorite,
        "favoriteOff" to GenUiIcons.FavoriteBorder, "folder" to GenUiIcons.Folder, "help" to GenUiIcons.HelpOutline,
        "home" to GenUiIcons.Home, "info" to GenUiIcons.Info, "locationOn" to GenUiIcons.LocationOn, "lock" to GenUiIcons.Lock,
        "lockOpen" to GenUiIcons.LockOpen, "mail" to GenUiIcons.Mail, "menu" to GenUiIcons.Menu, "moreVert" to GenUiIcons.MoreVert,
        "moreHoriz" to GenUiIcons.MoreHoriz, "notificationsOff" to GenUiIcons.NotificationsOff, "notifications" to GenUiIcons.Notifications,
        "pause" to GenUiIcons.Pause, "payment" to GenUiIcons.Payment, "person" to GenUiIcons.Person, "phone" to GenUiIcons.Phone,
        "photo" to GenUiIcons.Photo, "play" to GenUiIcons.PlayArrow, "print" to GenUiIcons.Print, "refresh" to GenUiIcons.Refresh,
        "rewind" to GenUiIcons.FastRewind, "search" to GenUiIcons.Search, "send" to GenUiIcons.Send,
        "settings" to GenUiIcons.Settings, "share" to GenUiIcons.Share, "shoppingCart" to GenUiIcons.ShoppingCart,
        "skipNext" to GenUiIcons.SkipNext, "skipPrevious" to GenUiIcons.SkipPrevious, "star" to GenUiIcons.Star,
        "starHalf" to GenUiIcons.StarHalf, "starOff" to GenUiIcons.StarBorder, "stop" to GenUiIcons.Stop, "upload" to GenUiIcons.Upload,
        "visibility" to GenUiIcons.Visibility, "visibilityOff" to GenUiIcons.VisibilityOff, "volumeDown" to GenUiIcons.VolumeDown,
        "volumeMute" to GenUiIcons.VolumeMute, "volumeOff" to GenUiIcons.VolumeOff,
        "volumeUp" to GenUiIcons.VolumeUp, "warning" to GenUiIcons.WarningAmber,
    )
}

private val LeafMargin: Dp get() = BasicComponents.LeafMargin

/** True inside a Row: nested containers size to their content instead of filling the width. */
private val LocalInRow = androidx.compose.runtime.staticCompositionLocalOf { false }

/** Components that share a Row's width when they have no `weight`. */
private val RowSharing = setOf("Card", "Column", "List")

/** Fill the width, except inside a Row, where siblings share it (a filling child would starve them). */
@Composable
private fun Modifier.fillWidth(): Modifier = if (LocalInRow.current) this else fillMaxWidth()

private val Heading = Regex("""^(#{1,6})\s+(.+)$""")

private val MarkdownMarkers = Regex("""(^|\n)\s*(#{1,6} |[-*] |\d+\. |> )|\*\*|__|`|\[[^]]+]\(""")

@Composable
private fun TextComponent(s: ComponentScope, m: Modifier) {
    val text = s.string("text").orEmpty()
    val modifier = m.padding(LeafMargin)
    val heading = Heading.matchEntire(text.trim())?.takeIf { '\n' !in text.trim() }
    when {
        heading != null -> Text(
            heading.groupValues[2].replace(Regex("[*_`]"), ""),
            style = when (heading.groupValues[1].length) {
                1 -> MaterialTheme.typography.headlineSmall
                2 -> MaterialTheme.typography.titleLarge
                3 -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.titleSmall
            },
            modifier = modifier,
        )
        s.string("variant") == "caption" -> Text(
            text.replace(Regex("[*_#`]"), ""),
            style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
            color = LocalContentColor.current.copy(alpha = 0.72f),
            modifier = modifier,
        )
        // Block Markdown fills the width, so inside a Row the text stays one plain run.
        !LocalInRow.current && MarkdownMarkers.containsMatchIn(text) -> MarkdownContent(text, modifier)
        LocalInRow.current -> Text(text.replace(Regex("[*_`]|^#+\\s*"), ""), style = MaterialTheme.typography.bodyLarge, modifier = modifier)
        else -> Text(text, style = MaterialTheme.typography.bodyLarge, modifier = modifier)
    }
}

@Composable
private fun ImageComponent(s: ComponentScope, m: Modifier) {
    val url = s.string("url") ?: return
    val scale = when (s.string("fit")) {
        "contain" -> ContentScale.Fit
        "fill" -> ContentScale.FillBounds
        "none" -> ContentScale.None
        "scaleDown" -> ContentScale.Inside
        else -> ContentScale.Crop
    }
    val shape = MaterialTheme.shapes.medium
    val inRow = LocalInRow.current
    val sized = when (s.string("variant")) {
        "icon" -> Modifier.size(24.dp)
        "avatar" -> Modifier.size(40.dp).clip(CircleShape)
        "smallFeature" -> Modifier.size(100.dp).clip(shape)
        "largeFeature" -> (if (inRow) Modifier.width(200.dp) else Modifier.fillMaxWidth()).heightIn(max = 400.dp).aspectRatio(16f / 9f).clip(shape)
        "header" -> (if (inRow) Modifier.width(200.dp) else Modifier.fillMaxWidth()).height(200.dp)
        else -> (if (inRow) Modifier.width(140.dp) else Modifier.fillMaxWidth().widthIn(max = 300.dp)).aspectRatio(4f / 3f).clip(shape)
    }
    FileImage(FilePart(s.id, "image/*", url), m.padding(LeafMargin).then(sized), contentScale = scale)
}

@Composable
private fun IconComponent(s: ComponentScope, m: Modifier) {
    val raw = s.raw("name")
    val vector = if (raw is JsonObject && raw["svgPath"] != null) {
        val path = s.context.string(raw["svgPath"]) ?: return
        remember(path) {
            runCatching {
                ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
                    .addPath(PathParser().parsePathString(path).toNodes(), fill = SolidColor(androidx.compose.ui.graphics.Color.Black)).build()
            }.getOrNull()
        }
    } else BasicComponents.icons[s.string("name")]
    if (vector != null) Icon(vector, contentDescription = null, modifier = m.padding(LeafMargin).size(24.dp))
}

@Composable
private fun VideoComponent(s: ComponentScope, m: Modifier) {
    val url = s.string("url") ?: return
    val uri = LocalUriHandler.current
    // No bundled media player: show the poster and open the video in the system player.
    Box(m.padding(LeafMargin).fillWidth().widthIn(min = 160.dp).aspectRatio(16f / 9f).clip(MaterialTheme.shapes.medium).clickable { uri.openUri(url) }, contentAlignment = Alignment.Center) {
        s.string("posterUrl")?.let { FileImage(FilePart("${s.id}-poster", "image/*", it), Modifier.matchParentSize()) }
        Icon(GenUiIcons.PlayArrow, contentDescription = "Play video", modifier = Modifier.size(48.dp))
    }
}

@Composable
private fun AudioComponent(s: ComponentScope, m: Modifier) {
    val url = s.string("url") ?: return
    AudioPlayer(rememberAudioPlayerState(url), m.padding(LeafMargin), title = s.string("description"))
}

@Composable
private fun RowComponent(s: ComponentScope, m: Modifier) {
    val justify = s.string("justify")
    Row(
        if (LocalInRow.current) m else m.fillMaxWidth(),
        horizontalArrangement = when (justify) {
            "center" -> Arrangement.Center
            "end" -> Arrangement.End
            "spaceAround" -> Arrangement.SpaceAround
            "spaceBetween" -> Arrangement.SpaceBetween
            "spaceEvenly" -> Arrangement.SpaceEvenly
            else -> Arrangement.Start
        },
        verticalAlignment = when (s.string("align")) {
            "start" -> Alignment.Top
            "end" -> Alignment.Bottom
            else -> Alignment.CenterVertically
        },
    ) {
        androidx.compose.runtime.CompositionLocalProvider(LocalInRow provides true) {
            s.children().forEach { child ->
                // Containers without a weight share the row (their content fills its width, and one
                // would otherwise take the whole row); leaves keep their own width.
                val weight = child.weight ?: 1f.takeIf { (child.component["component"] as? kotlinx.serialization.json.JsonPrimitive)?.content in RowSharing }
                s.Render(child, weight?.let { Modifier.weight(it) } ?: Modifier)
            }
        }
    }
}

@Composable
private fun ColumnComponent(s: ComponentScope, m: Modifier) {
    Column(
        m,
        verticalArrangement = when (s.string("justify")) {
            "center" -> Arrangement.Center
            "end" -> Arrangement.Bottom
            "spaceAround" -> Arrangement.SpaceAround
            "spaceBetween" -> Arrangement.SpaceBetween
            "spaceEvenly" -> Arrangement.SpaceEvenly
            else -> Arrangement.Top
        },
        horizontalAlignment = when (s.string("align")) {
            "center" -> Alignment.CenterHorizontally
            "end" -> Alignment.End
            else -> Alignment.Start
        },
    ) {
        androidx.compose.runtime.CompositionLocalProvider(LocalInRow provides false) {
            s.children().forEach { child -> s.Render(child, child.weight?.let { Modifier.weight(it) } ?: Modifier) }
        }
    }
}

@Composable
private fun ListComponent(s: ComponentScope, m: Modifier) {
    if (s.string("direction") == "horizontal") {
        Row(m.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.Top) {
            s.children().forEach { s.Render(it, Modifier.widthIn(max = 280.dp)) }
        }
    } else {
        // Inside a chat the list grows with its items; cap it so a long list scrolls in place.
        Column(m.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) { s.children().forEach { s.Render(it) } }
    }
}

@Composable
private fun TabsComponent(s: ComponentScope, m: Modifier) {
    val tabs = (s.raw("tabs") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
    if (tabs.isEmpty()) return
    var selected by rememberSaveable(s.id) { mutableIntStateOf(0) }
    Column(m.fillWidth()) {
        PrimaryTabRow(selectedTabIndex = selected.coerceIn(tabs.indices)) {
            tabs.forEachIndexed { i, tab ->
                Tab(selected = i == selected, onClick = { selected = i }, text = { Text(s.context.string(tab["title"]).orEmpty()) })
            }
        }
        val current = tabs[selected.coerceIn(tabs.indices)]
        s.Render((current["child"] as? JsonPrimitive)?.content?.let { s.ref(it) })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModalComponent(s: ComponentScope, m: Modifier) {
    var open by rememberSaveable(s.id) { mutableStateOf(false) }
    Box(m.clickable { open = true }) { s.Render(s.child("trigger")) }
    if (open) {
        ModalBottomSheet(onDismissRequest = { open = false }) {
            Box(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) { s.Render(s.child("content")) }
        }
    }
}

@Composable
private fun ButtonComponent(s: ComponentScope, m: Modifier) {
    val invalid = s.failedChecks().isNotEmpty()
    val modifier = m.padding(LeafMargin)
    val content: @Composable () -> Unit = { s.Render(s.child()) }
    when (s.string("variant")) {
        "primary" -> Button(onClick = { s.dispatch() }, enabled = !invalid, modifier = modifier) { content() }
        "borderless" -> TextButton(onClick = { s.dispatch() }, enabled = !invalid, modifier = modifier) { content() }
        else -> OutlinedButton(onClick = { s.dispatch() }, enabled = !invalid, modifier = modifier) { content() }
    }
}

@Composable
private fun TextFieldComponent(s: ComponentScope, m: Modifier) {
    val variant = s.string("variant")
    val errors = s.failedChecks()
    val value = s.string("value").orEmpty()
    var touched by remember(s.id) { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = {
            touched = true
            s.write("value", if (variant == "number") it.toDoubleOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(it) else JsonPrimitive(it))
        },
        label = s.string("label")?.let { { Text(it) } },
        placeholder = s.string("placeholder")?.let { { Text(it) } },
        singleLine = variant != "longText",
        minLines = if (variant == "longText") 3 else 1,
        visualTransformation = if (variant == "obscured") PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = when (variant) {
            "number" -> KeyboardOptions(keyboardType = KeyboardType.Number)
            "obscured" -> KeyboardOptions(keyboardType = KeyboardType.Password)
            else -> KeyboardOptions.Default
        },
        isError = touched && errors.isNotEmpty(),
        supportingText = if (touched && errors.isNotEmpty()) ({ Text(errors.first()) }) else null,
        modifier = m.padding(LeafMargin).fillWidth(),
    )
}

@Composable
private fun CheckBoxComponent(s: ComponentScope, m: Modifier) {
    val checked = s.boolean("value") ?: false
    Row(m.padding(LeafMargin).clickable { s.write("value", JsonPrimitive(!checked)) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = { s.write("value", JsonPrimitive(it)) })
        Text(s.string("label").orEmpty(), style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoicePickerComponent(s: ComponentScope, m: Modifier) {
    val multiple = s.string("variant") != "mutuallyExclusive"
    val selected = s.stringList("value")
    val options = (s.raw("options") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        .map { (s.context.string(it["label"]).orEmpty()) to (it["value"] as? JsonPrimitive)?.content.orEmpty() }
    var filter by remember(s.id) { mutableStateOf("") }
    val shown = options.filter { filter.isBlank() || it.first.contains(filter, ignoreCase = true) }
    fun toggle(value: String) {
        val next = if (multiple) { if (value in selected) selected - value else selected + value } else listOf(value)
        s.write("value", JsonArray(next.map(::JsonPrimitive)))
    }
    Column(m.padding(LeafMargin)) {
        s.string("label")?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
        if (s.boolean("filterable") == true) {
            OutlinedTextField(filter, { filter = it }, singleLine = true, placeholder = { Text("Filter") }, modifier = Modifier.fillMaxWidth())
        }
        if (s.string("displayStyle") == "chips") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                shown.forEach { (label, value) -> FilterChip(selected = value in selected, onClick = { toggle(value) }, label = { Text(label) }) }
            }
        } else {
            shown.forEach { (label, value) ->
                Row(Modifier.fillMaxWidth().clickable { toggle(value) }, verticalAlignment = Alignment.CenterVertically) {
                    if (multiple) Checkbox(checked = value in selected, onCheckedChange = { toggle(value) })
                    else RadioButton(selected = value in selected, onClick = { toggle(value) })
                    Text(label)
                }
            }
        }
    }
}

@Composable
private fun SliderComponent(s: ComponentScope, m: Modifier) {
    val min = (s.number("min") ?: 0.0).toFloat()
    val max = (s.number("max") ?: 100.0).toFloat()
    val value = (s.number("value") ?: min.toDouble()).toFloat().coerceIn(min, max)
    Column(m.padding(LeafMargin).fillWidth().widthIn(min = 160.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            s.string("label")?.let { Text(it, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f)) }
            Text(if (value % 1f == 0f) value.toInt().toString() else "%.2f".format(value), style = MaterialTheme.typography.labelLarge)
        }
        Slider(
            value = value,
            onValueChange = { s.write("value", JsonPrimitive(it.toDouble())) },
            valueRange = min..max,
            steps = ((s.number("steps") ?: 0.0).toInt() - 1).coerceAtLeast(0),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeComponent(s: ComponentScope, m: Modifier) {
    val enableDate = s.boolean("enableDate") ?: true
    val enableTime = s.boolean("enableTime") ?: false
    val value = s.string("value").orEmpty()
    val zone = ZoneId.systemDefault()
    val current = runCatching { Instant.parse(value).atZone(zone).toLocalDateTime() }.getOrNull()
        ?: runCatching { java.time.LocalDateTime.parse(value) }.getOrNull()
        ?: runCatching { LocalDate.parse(value).atStartOfDay() }.getOrNull()
    var picking by remember(s.id) { mutableStateOf<String?>(null) }
    val shown = when {
        current == null -> s.string("label") ?: "Choose"
        enableDate && enableTime -> "${current.toLocalDate()} ${current.toLocalTime().withSecond(0).withNano(0)}"
        enableTime -> current.toLocalTime().withSecond(0).withNano(0).toString()
        else -> current.toLocalDate().toString()
    }
    fun store(date: LocalDate?, time: LocalTime?) {
        val iso = when {
            enableDate && enableTime -> (date ?: LocalDate.now()).atTime(time ?: LocalTime.MIDNIGHT).atZone(zone).toOffsetDateTime().toString()
            enableTime -> (time ?: LocalTime.MIDNIGHT).toString()
            else -> (date ?: LocalDate.now()).toString()
        }
        s.write("value", JsonPrimitive(iso))
    }
    Column(m.padding(LeafMargin)) {
        s.string("label")?.takeIf { current != null }?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
        OutlinedButton(onClick = { picking = if (enableDate) "date" else "time" }, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
            Icon(if (enableDate) GenUiIcons.CalendarToday else GenUiIcons.Event, null, Modifier.size(18.dp))
            Text(shown, Modifier.padding(start = 8.dp))
        }
    }
    when (picking) {
        "date" -> {
            val state = rememberDatePickerState(current?.toLocalDate()?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli())
            DatePickerDialog(
                onDismissRequest = { picking = null },
                confirmButton = {
                    TextButton(onClick = {
                        val date = state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                        store(date, current?.toLocalTime())
                        picking = if (enableTime) "time" else null
                    }) { Text("OK") }
                },
            ) { DatePicker(state) }
        }
        "time" -> {
            val state = rememberTimePickerState(current?.hour ?: 9, current?.minute ?: 0)
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { picking = null },
                confirmButton = { TextButton(onClick = { store(current?.toLocalDate(), LocalTime.of(state.hour, state.minute)); picking = null }) { Text("OK") } },
                text = { TimePicker(state) },
            )
        }
    }
}

private fun JsonArray?.orEmpty() = this ?: JsonArray(emptyList())
