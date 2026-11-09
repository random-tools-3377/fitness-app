@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package io.github.randomtools3377.fitness.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.randomtools3377.fitness.core.Accent
import io.github.randomtools3377.fitness.core.AppIconChoice
import io.github.randomtools3377.fitness.core.AppSettings
import io.github.randomtools3377.fitness.core.AppTabs
import io.github.randomtools3377.fitness.core.Backdrop
import io.github.randomtools3377.fitness.core.CardSize
import io.github.randomtools3377.fitness.core.Colors
import io.github.randomtools3377.fitness.core.FontChoice
import io.github.randomtools3377.fitness.core.Schemes
import io.github.randomtools3377.fitness.core.ThemeMode
import io.github.randomtools3377.fitness.core.ThemeSettings
import io.github.randomtools3377.fitness.core.WidgetStyle

private val Rainbow = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)

private fun tabTitle(name: String) = Tab.entries.firstOrNull { it.name == name }?.title ?: name

/**
 * Settings → Look (0.10.0; 0.17.0 adds pure black, ready-made themes, any colour, card size, fonts,
 * the tabs, a background, app icons and the widget's style, as in the checklist app).
 */
@Composable
fun ThemeSection(vm: MainViewModel, settings: AppSettings, open: (Route) -> Unit) {
    val t = settings.theme
    Section("Look") {
        Text("Theme", style = MaterialTheme.typography.labelLarge)
        Choices(ThemeMode.entries, t.mode, { it.title }, { "theme:${it.name}" }) { m -> vm.changeTheme { it.copy(mode = m) } }
        Hint("Phone follows the phone's own dark mode. Black is dark on a pure black background, which saves power on many screens.")

        Text("Colour", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (canMatchWallpaper) {
                Swatch("Wallpaper", t.seed == null, Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), inside = "🖼") {
                    vm.changeTheme { it.copy(accent = Accent.WALLPAPER, color = null) }
                }
            }
            Accent.entries.filter { it != Accent.WALLPAPER }.forEach { a ->
                Swatch(a.title, t.color == null && t.accent == a, Modifier.background(Color(a.seed), CircleShape)) {
                    vm.changeTheme { it.copy(accent = a, color = null) }
                }
            }
            t.color?.let { own -> Swatch("Yours", true, Modifier.background(Color(own), CircleShape)) { open(Route.AppColour) } }
            Swatch("Any colour…", false, Modifier.background(Brush.sweepGradient(Rainbow), CircleShape)) { open(Route.AppColour) }
        }
        Hint("The whole app takes its colours from the one you pick, and keeps text readable.")

        Text("Text size", style = MaterialTheme.typography.labelLarge)
        Choices(listOf(0.9f, 1f, 1.15f, 1.3f), t.textScale, { when (it) { 0.9f -> "Smaller"; 1f -> "As the phone"; 1.15f -> "Larger"; else -> "Largest" } }, { "text-size:$it" }) { v ->
            vm.changeTheme { it.copy(textScale = v) }
        }
        Text("Card size", style = MaterialTheme.typography.labelLarge)
        Choices(CardSize.entries, t.cardSize, { it.title }, { "card-size:${it.name}" }) { v -> vm.changeTheme { it.copy(cardSize = v) } }
        Text("Font", style = MaterialTheme.typography.labelLarge)
        Choices(FontChoice.entries, t.font, { it.title }, { "font:${it.name}" }) { v -> vm.changeTheme { it.copy(font = v) } }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Colour-blind-friendly charts")
                Text("Blue and orange instead of colours that can be hard to tell apart.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = t.colourBlind, onCheckedChange = { v -> vm.changeTheme { it.copy(colourBlind = v) } }, modifier = Modifier.testTag("colour-blind"))
        }

        BackdropSetting(vm, t)
        TabsSetting(vm, t)
        AppIconSetting(t.icon) { vm.setAppIcon(it) }

        Text("Widget", style = MaterialTheme.typography.labelLarge)
        Choices(WidgetStyle.entries, t.widgetStyle, { it.title }, { "widget-style:${it.name}" }) { v -> vm.changeTheme { it.copy(widgetStyle = v) } }
        Hint(if (t.widgetStyle == WidgetStyle.SEE_THROUGH) "White text on a see-through background, over your wallpaper." else "In the app's colours, light or dark with the phone.")
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** What is behind the diary: nothing, a gradient, or the user's own photo, dimmed so text stays readable. */
@Composable
private fun BackdropSetting(vm: MainViewModel, t: ThemeSettings) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.pickBackground(uri) }
    fun pickPhoto() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    Text("Behind the diary", style = MaterialTheme.typography.labelLarge)
    Choices(Backdrop.entries, t.backdrop, { it.title }, { "backdrop:${it.name}" }) { choice ->
        if (choice == Backdrop.PHOTO && t.photoStamp.isEmpty()) pickPhoto() else vm.changeTheme { it.copy(backdrop = choice) }
    }
    if (t.backdrop == Backdrop.PHOTO) {
        TextButton(onClick = { pickPhoto() }, modifier = Modifier.testTag("pick-photo")) { Text("Pick another photo") }
        Text("Dimmed ${t.dim}%", style = MaterialTheme.typography.bodyMedium)
        var dim by remember(t.dim) { mutableStateOf(t.dim.toFloat()) }
        Slider(
            value = dim,
            onValueChange = { dim = it },
            onValueChangeFinished = { vm.changeTheme { it.copy(dim = dim.toInt()) } },
            valueRange = ThemeSettings.MIN_DIM.toFloat()..ThemeSettings.MAX_DIM.toFloat(),
            steps = (ThemeSettings.MAX_DIM - ThemeSettings.MIN_DIM) / 10 - 1,
            modifier = Modifier.testTag("backdrop-dim"),
        )
        Hint("The photo is faded toward the background colour so text on it stays readable. It stays on the phone and isn't in the backup file.")
    }
}

/** Which tabs show, and the one the app opens on. The diary always shows. */
@Composable
private fun TabsSetting(vm: MainViewModel, t: ThemeSettings) {
    Text("Tabs", style = MaterialTheme.typography.labelLarge)
    AppTabs.ALL.filter { it != AppTabs.DIARY }.forEach { name ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Checkbox(
                checked = name !in t.hiddenTabs,
                onCheckedChange = { shown -> vm.changeTheme { it.copy(hiddenTabs = if (shown) it.hiddenTabs - name else it.hiddenTabs + name) } },
                modifier = Modifier.testTag("tab:$name"),
            )
            Text(tabTitle(name), Modifier.weight(1f))
            if (name in t.hiddenTabs) TextButton(onClick = { vm.goToTab.value = name }, modifier = Modifier.testTag("go-tab:$name")) { Text("Open") }
        }
    }
    Hint("A hidden tab can still be opened from here. Tabs for parts switched off on the Features page don't show either way.")
    Text("Opens on", style = MaterialTheme.typography.labelLarge)
    val shown = AppTabs.ALL.filter { it !in t.hiddenTabs }
    Choices(shown, t.opensOn(shown), { tabTitle(it) }, { "first-tab:$it" }) { name -> vm.changeTheme { it.copy(firstTab = name) } }
}

@Composable
private fun AppIconSetting(current: AppIconChoice, onPick: (AppIconChoice) -> Unit) {
    Text("App icon", style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AppIconChoice.entries.forEach { icon ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable { onPick(icon) }
                    .semantics { selected = icon == current }
                    .testTag("app-icon:${icon.name}")
                    .padding(4.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(52.dp)
                        .then(if (icon == current) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(16.dp)).padding(4.dp) else Modifier)
                        .background(Color(icon.color), RoundedCornerShape(12.dp)),
                ) { if (icon == current) Text("✓", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                Text(icon.title, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    Hint("Some home screens take a moment to show the new icon, or move it to the app list.")
}

@Composable
private fun Swatch(label: String, selected: Boolean, fill: Modifier, inside: String? = null, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clickable(onClick = onClick)
            .semantics { this.selected = selected }
            .testTag("colour:$label")
            .padding(vertical = 4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape).padding(5.dp) else Modifier.padding(5.dp))
                .then(fill),
        ) { if (inside != null) Text(inside, fontSize = 18.sp) }
        Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 1)
    }
}

/**
 * Any colour (copied from the checklist app): a colour square (more colourful to the right, darker
 * towards the bottom), a hue slider and a hex code box, plus the colours picked lately.
 */
@Composable
fun ColorPicker(color: Int, recent: List<Int>, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    // Hue, saturation and value are kept apart, so dragging to grey or black keeps the hue.
    var hsv by remember { mutableStateOf(Colors.toHsv(color)) }
    var hexText by remember { mutableStateOf(Colors.hex(color)) }
    if (Colors.fromHsv(hsv.first, hsv.second, hsv.third) != color && Colors.parseHex(hexText) != color) {
        hsv = Colors.toHsv(color)
        hexText = Colors.hex(color)
    }

    fun pick(h: Float, s: Float, v: Float) {
        hsv = Triple(h, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))
        val picked = Colors.fromHsv(hsv.first, hsv.second, hsv.third)
        hexText = Colors.hex(picked)
        onChange(picked)
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.horizontalGradient(listOf(Color.White, Color(Colors.fromHsv(hsv.first, 1f, 1f)))))
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                .pointerInput(Unit) { detectTapGestures { p -> pick(hsv.first, p.x / size.width, 1f - p.y / size.height) } }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ -> pick(hsv.first, change.position.x / size.width, 1f - change.position.y / size.height) }
                }
                .semantics { contentDescription = "Colour square: more colourful to the right, darker towards the bottom" }
                .testTag("colour-square"),
        ) {
            Box(
                Modifier
                    .offset(x = maxWidth * hsv.second - 13.dp, y = maxHeight * (1f - hsv.third) - 13.dp)
                    .size(26.dp)
                    .border(3.dp, Color.White, CircleShape)
                    .background(Color(color), CircleShape),
            )
        }
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.horizontalGradient(Rainbow))
                .pointerInput(Unit) { detectTapGestures { p -> pick(360f * (p.x / size.width).coerceIn(0f, 1f), hsv.second, hsv.third) } }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ -> pick(360f * (change.position.x / size.width).coerceIn(0f, 0.999f), hsv.second, hsv.third) }
                }
                .semantics { contentDescription = "Hue slider" }
                .testTag("hue-slider"),
        ) {
            Box(
                Modifier
                    .offset(x = maxWidth * (hsv.first / 360f) - 14.dp)
                    .size(28.dp)
                    .border(3.dp, Color.White, CircleShape)
                    .background(Color(Colors.fromHsv(hsv.first, 1f, 1f)), CircleShape),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).background(Color(color), RoundedCornerShape(16.dp)))
            Spacer(Modifier.width(12.dp))
            OutlinedTextField(
                value = hexText,
                onValueChange = { text ->
                    hexText = text.take(7)
                    Colors.parseHex(text)?.let { picked ->
                        hsv = Colors.toHsv(picked)
                        onChange(picked)
                    }
                },
                label = { Text("Hex code") },
                singleLine = true,
                isError = Colors.parseHex(hexText) == null,
                modifier = Modifier.weight(1f).testTag("field:hex"),
            )
        }
        if (recent.isNotEmpty()) {
            Text("Recent colours", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                recent.forEach { past ->
                    Box(
                        Modifier
                            .size(40.dp)
                            .background(Color(past), CircleShape)
                            .clickable {
                                hsv = Colors.toHsv(past)
                                hexText = Colors.hex(past)
                                onChange(past)
                            }
                            .semantics { contentDescription = "Recent colour ${Colors.hex(past)}" }
                            .testTag("recent:${Colors.hex(past)}"),
                    )
                }
            }
        }
    }
}

/** The colour picker for the app's colour, with a preview of how the app will look. */
@Composable
fun AppColourScreen(vm: MainViewModel, theme: ThemeSettings, modifier: Modifier, back: () -> Unit) {
    var color by rememberSaveable { mutableStateOf(theme.seed ?: Accent.TERRACOTTA.seed) }
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("App colour") },
            navigationIcon = { IconButton(onClick = back, modifier = Modifier.testTag("back")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            actions = {
                TextButton(onClick = { vm.changeTheme { it.picked(color) }; back() }, modifier = Modifier.testTag("use-colour")) { Text("Use it") }
            },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ColorPicker(color, theme.recent, onChange = { color = it })
            Text("How it looks", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            LookPreview(color)
            Hint("The whole app takes its colours from the one you pick, and keeps text readable.")
        }
    }
}

@Composable
private fun LookPreview(seed: Int) {
    val s = Schemes.of(seed, LocalBrightness.current)
    Column(
        Modifier.fillMaxWidth().background(Color(s.background), RoundedCornerShape(16.dp)).padding(12.dp).testTag("look-preview"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(s.primaryContainer))) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Left today", color = Color(s.onPrimaryContainer), style = MaterialTheme.typography.labelLarge)
                    Text("640 kcal", color = Color(s.onPrimaryContainer), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
                Text("Water 1.5 l", color = Color(s.onPrimaryContainer), fontWeight = FontWeight.Bold)
            }
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(s.surfaceContainerLow))) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Breakfast", color = Color(s.onSurface), style = MaterialTheme.typography.titleMedium)
                    Text("Porridge, 250 g · 320 kcal", color = Color(s.onSurfaceVariant), style = MaterialTheme.typography.bodyMedium)
                }
                Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = Color(s.primary), contentColor = Color(s.onPrimary))) { Text("Add") }
            }
        }
    }
}
