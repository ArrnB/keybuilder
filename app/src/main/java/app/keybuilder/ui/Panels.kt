package app.keybuilder.ui

import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.keybuilder.engine.KeyboardEngine
import app.keybuilder.engine.ShiftState
import app.keybuilder.model.ClipEntry
import app.keybuilder.model.ClipHistory
import app.keybuilder.model.EmojiCategory
import app.keybuilder.model.EmojiData
import app.keybuilder.model.GifItem
import app.keybuilder.model.Giphy
import app.keybuilder.model.KeyAction
import app.keybuilder.model.TOOLBAR_HEIGHT_DP
import app.keybuilder.model.Theme
import app.keybuilder.store.TypingSettings
import coil.ImageLoader
import coil.compose.AsyncImage
import java.io.File
import kotlin.math.abs

/*
 * The panels that take the place of the keys: clipboard, GIF and emoji.
 *
 * They only draw what they are given and report taps. Reading the clipboard, asking GIPHY
 * and sending things to the app being typed in all happen in the keyboard service.
 * They use plain Compose (no Material), because the keyboard window has no app theme;
 * colours come from the keyboard's own theme so panels match the keys.
 */

/** Panel colours worked out from the keyboard theme. */
internal class PanelColors(theme: Theme) {
    /**
     * The keys' background colours, but always solid. A panel lies over the app you are
     * typing in, and on a see-through background its contents and the app's would run together.
     */
    val background = backgroundBrush(theme, opaque = true)

    /** The colour at the top of that background, for the thin search bar above the keys. */
    val backgroundTop = backgroundTopColor(theme)
    val text = theme.textColor.toColorOrNull() ?: Color.White
    val accent = theme.accentColor.toColorOrNull() ?: Color(0xFF4C8DFF)
    val accentText = theme.accentTextColor.toColorOrNull() ?: Color.White

    // Tints of the text colour: readable on the background of any theme.
    val chip = text.copy(alpha = 0.12f)
    val chipStrong = text.copy(alpha = 0.24f)
    val faint = text.copy(alpha = 0.65f)
}

/**
 * How tall a panel is: [baseDp] is the height of the keyboard it replaces, [extraDp] is how
 * much taller the user has dragged it, up to [maxExtraDp].
 */
data class PanelSize(val baseDp: Float, val extraDp: Float = 0f, val maxExtraDp: Float = 0f)

/** Height of the drag handle at the top of every panel. */
private const val HANDLE_HEIGHT_DP = 16f

/** What the drag handle needs to remember between touch events. */
private class DragMemory {
    var startRawY = 0f
    var startExtra = 0f
    var moved = false
}

/**
 * The little bar at the top of a panel. Drag it up to make the panel taller and down to
 * shrink it again; a tap jumps between normal and tallest.
 *
 * It reads the finger's position on the SCREEN (rawY) rather than inside the panel, because
 * the panel itself moves while it is being resized.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ResizeHandle(colors: PanelColors, size: PanelSize, onResize: (Float) -> Unit) {
    val density = LocalDensity.current.density
    val current by rememberUpdatedState(size)
    val report by rememberUpdatedState(onResize)
    val drag = remember { DragMemory() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(HANDLE_HEIGHT_DP.dp)
            .pointerInteropFilter { event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        drag.startRawY = event.rawY
                        drag.startExtra = current.extraDp
                        drag.moved = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val pulledUp = (drag.startRawY - event.rawY) / density
                        if (abs(pulledUp) > 6f) drag.moved = true
                        if (drag.moved) {
                            // step = 0: follow the finger exactly. Only the panel is redrawn, so this is cheap.
                            report(KeyboardEngine.resizePanel(drag.startExtra, pulledUp, current.maxExtraDp, step = 0f))
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!drag.moved) report(if (current.extraDp > 0f) 0f else current.maxExtraDp)
                    }
                }
                true
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(44.dp).height(4.dp).background(colors.faint, RoundedCornerShape(50)))
    }
}

/** The shared shape of every panel: a drag handle, a top line with "ABC" to go back, then the content. */
@Composable
private fun PanelFrame(
    theme: Theme,
    size: PanelSize,
    onResize: (Float) -> Unit,
    title: String,
    onClose: () -> Unit,
    onBackspace: (() -> Unit)? = null,
    actionText: String? = null,
    onAction: () -> Unit = {},
    content: @Composable ColumnScope.(PanelColors) -> Unit,
) {
    val colors = remember(theme) { PanelColors(theme) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height((size.baseDp + size.extraDp).dp)
            .background(colors.background)
            .padding(horizontal = 8.dp),
    ) {
        ResizeHandle(colors, size, onResize)
        Row(
            modifier = Modifier.fillMaxWidth().height(TOOLBAR_HEIGHT_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PanelChip("ABC", colors, onClick = onClose, strong = true)
            PanelText(title, colors.text, 15f, Modifier.weight(1f))
            if (actionText != null) PanelChip(actionText, colors, onClick = onAction)
            if (onBackspace != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(colors.chip)
                        .clickable(onClick = onBackspace)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    KeyIcon(KeyAction.Backspace, ShiftState.OFF, colors.text, sizeDp = 20f)
                }
            }
        }
        content(colors)
    }
}

@Composable
private fun PanelText(text: String, color: Color, sizeSp: Float, modifier: Modifier = Modifier, maxLines: Int = 1) {
    BasicText(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(color = color, fontSize = sizeSp.sp),
    )
}

@Composable
private fun PanelChip(
    text: String,
    colors: PanelColors,
    onClick: () -> Unit,
    strong: Boolean = false,
    selected: Boolean = false,
) {
    val background = when {
        selected -> colors.accent
        strong -> colors.chipStrong
        else -> colors.chip
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        PanelText(text, if (selected) colors.accentText else colors.text, 13f)
    }
}

/** A few lines of explanation in the middle of a panel, with an optional button. */
@Composable
private fun ColumnScope.PanelNote(
    colors: PanelColors,
    text: String,
    buttonText: String? = null,
    onButton: () -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PanelText(text, colors.faint, 14f, maxLines = 5)
        if (buttonText != null) PanelChip(buttonText, colors, onClick = onButton, selected = true)
    }
}

// ------------------------------------------------------------------ clipboard

@Composable
fun ClipboardPanel(
    theme: Theme,
    size: PanelSize,
    onResize: (Float) -> Unit,
    history: ClipHistory,
    enabled: Boolean,
    message: String?,
    imageLoader: ImageLoader,
    imageFileOf: (ClipEntry) -> File?,
    onClose: () -> Unit,
    onTurnOn: () -> Unit,
    onPaste: (ClipEntry) -> Unit,
    onPin: (Long) -> Unit,
    onRemove: (Long) -> Unit,
    onClear: () -> Unit,
) {
    val canClear = enabled && history.entries.any { !it.pinned }
    val pinned = history.entries.filter { it.pinned }
    val recent = history.entries.filter { !it.pinned }
    PanelFrame(
        theme = theme,
        size = size,
        onResize = onResize,
        title = "Clipboard",
        onClose = onClose,
        actionText = if (canClear) "Clear" else null,
        onAction = onClear,
    ) { colors ->
        if (message != null) PanelText(message, colors.faint, 12f, Modifier.padding(bottom = 4.dp), maxLines = 2)
        if (!enabled) {
            PanelNote(
                colors = colors,
                text = "Clipboard history is off. When it is on, the keyboard keeps the last " +
                    "${ClipHistory.MAX_UNPINNED} things you copy, on this phone only.",
                buttonText = "Turn on",
                onButton = onTurnOn,
            )
        } else if (history.entries.isEmpty()) {
            PanelNote(colors, "Nothing here yet. Copy some text or a picture and it shows up in this list.")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                // Pinned items come first and stay until you unpin or remove them: Clear and the
                // 30-item limit only touch the recent ones.
                if (pinned.isNotEmpty()) {
                    item { PanelText("Pinned: kept until you remove them", colors.faint, 12f) }
                    items(pinned) { entry ->
                        ClipRow(entry, colors, imageLoader, imageFileOf(entry), onPaste, onPin, onRemove)
                    }
                    if (recent.isNotEmpty()) item { PanelText("Recent", colors.faint, 12f) }
                }
                items(recent) { entry ->
                    ClipRow(entry, colors, imageLoader, imageFileOf(entry), onPaste, onPin, onRemove)
                }
            }
        }
    }
}

@Composable
private fun ClipRow(
    entry: ClipEntry,
    colors: PanelColors,
    imageLoader: ImageLoader,
    imageFile: File?,
    onPaste: (ClipEntry) -> Unit,
    onPin: (Long) -> Unit,
    onRemove: (Long) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.chip)
            .clickable { onPaste(entry) }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (imageFile != null) {
            AsyncImage(
                model = imageFile,
                contentDescription = "Copied picture",
                imageLoader = imageLoader,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(6.dp)),
            )
            PanelText("Picture", colors.text, 14f, Modifier.weight(1f))
        } else {
            PanelText(entry.text.orEmpty(), colors.text, 14f, Modifier.weight(1f), maxLines = 2)
        }
        PanelChip(if (entry.pinned) "Unpin" else "Pin", colors, onClick = { onPin(entry.id) }, selected = entry.pinned)
        PanelChip("Remove", colors, onClick = { onRemove(entry.id) })
    }
}

// ------------------------------------------------------------------ GIFs

@Composable
fun GifPanel(
    theme: Theme,
    size: PanelSize,
    onResize: (Float) -> Unit,
    title: String,
    searchHint: String,
    gifs: List<GifItem>,
    loading: Boolean,
    message: String?,
    query: String,
    hasKey: Boolean,
    imageLoader: ImageLoader,
    canLoadMore: Boolean,
    loadingMore: Boolean,
    onClose: () -> Unit,
    onSearchBox: () -> Unit,
    onQuickSearch: (String) -> Unit,
    onMore: () -> Unit,
    onPick: (GifItem) -> Unit,
) {
    PanelFrame(theme = theme, size = size, onResize = onResize, title = title, onClose = onClose) { colors ->
        if (!hasKey) {
            PanelNote(
                colors,
                "GIFs and stickers need a free GIPHY key. Open the KeyBuilder app, choose " +
                    "Settings, and paste your key there.",
            )
        } else {
            // Looks like a search box. Tapping it brings the keys back so you can type the words.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(colors.chip)
                    .clickable(onClick = onSearchBox)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                PanelText(
                    text = if (query.isEmpty()) searchHint else query,
                    color = if (query.isEmpty()) colors.faint else colors.text,
                    sizeSp = 14f,
                )
            }
            LazyRow(
                modifier = Modifier.padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    PanelChip("Trending", colors, onClick = { onQuickSearch("") }, selected = query.isEmpty())
                }
                items(Giphy.QUICK_SEARCHES) { word ->
                    PanelChip(word, colors, onClick = { onQuickSearch(word) }, selected = query == word)
                }
            }

            if (message != null) PanelText(message, colors.faint, 12f, Modifier.padding(bottom = 4.dp), maxLines = 2)
            if (loading) {
                PanelNote(colors, "Loading")
            } else if (gifs.isEmpty()) {
                PanelNote(colors, if (message == null) "Nothing to show." else "")
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(bottom = 8.dp),
                ) {
                    items(gifs) { gif ->
                        AsyncImage(
                            model = gif.previewUrl,
                            contentDescription = gif.title,
                            imageLoader = imageLoader,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(84.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.chip)
                                .clickable { onPick(gif) },
                        )
                    }
                    if (canLoadMore) {
                        // One cell across the whole width at the end of the list.
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                PanelChip(
                                    text = if (loadingMore) "Loading" else "Show more",
                                    colors = colors,
                                    onClick = onMore,
                                    strong = true,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Shown above the keys while search words are being typed. It takes the place of the
 * shortcut strip, so the keyboard keeps its height.
 */
@Composable
fun GifSearchBar(theme: Theme, query: String, onCancel: () -> Unit, onSearch: () -> Unit) {
    val colors = remember(theme) { PanelColors(theme) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TOOLBAR_HEIGHT_DP.dp)
            .background(colors.backgroundTop)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PanelChip("Cancel", colors, onClick = onCancel)
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(50))
                .background(colors.chip)
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            PanelText(
                text = if (query.isEmpty()) "Type what to search for" else "$query|",
                color = if (query.isEmpty()) colors.faint else colors.text,
                sizeSp = 14f,
            )
        }
        PanelChip("Search", colors, onClick = onSearch, selected = true)
    }
}

// ------------------------------------------------------------------ settings

/**
 * The panel behind the gear: switch between your saved keyboards and change the everyday
 * settings without leaving the app you are typing in.
 */
@Composable
fun SettingsPanel(
    theme: Theme,
    size: PanelSize,
    onResize: (Float) -> Unit,
    keyboards: List<Pair<String, String>>,
    activeId: String,
    settings: TypingSettings,
    clipboardOn: Boolean,
    onClose: () -> Unit,
    onUseKeyboard: (String) -> Unit,
    onSettings: (TypingSettings) -> Unit,
    onClipboardOn: (Boolean) -> Unit,
    onOpenApp: () -> Unit,
    onOtherKeyboards: () -> Unit,
) {
    PanelFrame(theme = theme, size = size, onResize = onResize, title = "Settings", onClose = onClose) { colors ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            item { PanelText("Keyboard: tap one to switch to it", colors.faint, 12f) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(keyboards) { entry ->
                        PanelChip(
                            text = entry.second,
                            colors = colors,
                            onClick = { onUseKeyboard(entry.first) },
                            selected = entry.first == activeId,
                        )
                    }
                }
            }
            item {
                PanelToggle(colors, "Vibrate when a key is touched", settings.vibrate) {
                    onSettings(settings.copy(vibrate = it))
                }
            }
            if (settings.vibrate) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        PanelText("Vibration strength: ${settings.vibrateStrength}%", colors.text, 14f, Modifier.weight(1f))
                        PanelChip("Weaker", colors, onClick = {
                            onSettings(settings.copy(vibrateStrength = (settings.vibrateStrength - 10).coerceIn(5, 100)))
                        })
                        PanelChip("Stronger", colors, onClick = {
                            onSettings(settings.copy(vibrateStrength = (settings.vibrateStrength + 10).coerceIn(5, 100)))
                        })
                    }
                }
            }
            item {
                PanelToggle(colors, "Show the letter above a pressed key", settings.popups) {
                    onSettings(settings.copy(popups = it))
                }
            }
            item {
                PanelToggle(colors, "Two spaces type a full stop", settings.doubleSpace) {
                    onSettings(settings.copy(doubleSpace = it))
                }
            }
            item {
                PanelToggle(colors, "Symbols instead of words on the top bar", settings.stripIcons) {
                    onSettings(settings.copy(stripIcons = it))
                }
            }
            item {
                PanelToggle(colors, "Word suggestions", settings.suggestions) {
                    onSettings(settings.copy(suggestions = it))
                }
            }
            item {
                PanelToggle(colors, "Fix slips automatically (backspace undoes one)", settings.autocorrect) {
                    onSettings(settings.copy(autocorrect = it))
                }
            }
            item {
                PanelToggle(colors, "Last copied thing next to the gear", settings.quickClip) {
                    onSettings(settings.copy(quickClip = it))
                }
            }
            item { PanelToggle(colors, "Clipboard history", clipboardOn, onClipboardOn) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PanelChip("Open the KeyBuilder app", colors, onClick = onOpenApp, strong = true)
                    PanelChip("Other keyboards", colors, onClick = onOtherKeyboards, strong = true)
                }
            }
        }
    }
}

/** One setting: its name, then On and Off. */
@Composable
private fun PanelToggle(colors: PanelColors, label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PanelText(label, colors.text, 14f, Modifier.weight(1f), maxLines = 2)
        PanelChip("On", colors, onClick = { onChange(true) }, selected = on)
        PanelChip("Off", colors, onClick = { onChange(false) }, selected = !on)
    }
}

// ------------------------------------------------------------------ emoji

@Composable
fun EmojiPanel(
    theme: Theme,
    size: PanelSize,
    onResize: (Float) -> Unit,
    recent: List<String>,
    onClose: () -> Unit,
    onBackspace: () -> Unit,
    onPick: (String) -> Unit,
) {
    // The recent list as it was when the panel opened, so it does not reshuffle under your finger.
    val shownRecent = remember { recent }
    val groups = remember { listOf(EmojiCategory("Recent", shownRecent)) + EmojiData.categories }
    // Start on Recent when there is something in it, otherwise on the first real group.
    var category by remember { mutableStateOf(if (shownRecent.isEmpty()) 1 else 0) }

    PanelFrame(
        theme = theme, size = size, onResize = onResize, title = "Emoji", onClose = onClose, onBackspace = onBackspace,
    ) { colors ->
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            groups.forEachIndexed { index, group ->
                item {
                    PanelChip(group.name, colors, onClick = { category = index }, selected = index == category)
                }
            }
        }
        if (groups[category].emoji.isEmpty()) {
            PanelNote(colors, "The emoji you use show up here.")
        } else key(category) { // key(...) starts each group scrolled to its top
            LazyVerticalGrid(
                columns = GridCells.Adaptive(44.dp),
                modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 6.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                items(groups[category].emoji) { emoji ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onPick(emoji) },
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(text = emoji, style = TextStyle(fontSize = 24.sp))
                    }
                }
            }
        }
    }
}
