package app.keybuilder.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.keybuilder.engine.ShiftState
import app.keybuilder.model.ClipEntry
import app.keybuilder.model.ClipHistory
import app.keybuilder.model.EmojiData
import app.keybuilder.model.GifItem
import app.keybuilder.model.Giphy
import app.keybuilder.model.KeyAction
import app.keybuilder.model.TOOLBAR_HEIGHT_DP
import app.keybuilder.model.Theme
import coil.ImageLoader
import coil.compose.AsyncImage
import java.io.File

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
    val background = theme.background.toColorOrNull() ?: Color(0xFF1B1D22)
    val text = theme.textColor.toColorOrNull() ?: Color.White
    val accent = theme.accentColor.toColorOrNull() ?: Color(0xFF4C8DFF)
    val accentText = theme.accentTextColor.toColorOrNull() ?: Color.White

    // Tints of the text colour: readable on the background of any theme.
    val chip = text.copy(alpha = 0.12f)
    val chipStrong = text.copy(alpha = 0.24f)
    val faint = text.copy(alpha = 0.65f)
}

/** The shared shape of every panel: a top line with "ABC" to go back, then the content. */
@Composable
private fun PanelFrame(
    theme: Theme,
    heightDp: Float,
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
            .height(heightDp.dp)
            .background(colors.background)
            .padding(horizontal = 8.dp),
    ) {
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
    heightDp: Float,
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
    PanelFrame(
        theme = theme,
        heightDp = heightDp,
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
                items(history.ordered) { entry ->
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
    heightDp: Float,
    gifs: List<GifItem>,
    loading: Boolean,
    message: String?,
    query: String,
    hasKey: Boolean,
    imageLoader: ImageLoader,
    onClose: () -> Unit,
    onSearchBox: () -> Unit,
    onQuickSearch: (String) -> Unit,
    onPick: (GifItem) -> Unit,
) {
    PanelFrame(theme = theme, heightDp = heightDp, title = "GIFs by GIPHY", onClose = onClose) { colors ->
        if (!hasKey) {
            PanelNote(
                colors,
                "GIF search needs a free GIPHY key. Open the KeyBuilder app, choose " +
                    "\"Clipboard and GIFs\", and paste your key there.",
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
                    text = if (query.isEmpty()) "Search GIPHY" else query,
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
                PanelNote(colors, if (message == null) "No GIFs to show." else "")
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
            .background(colors.background)
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

// ------------------------------------------------------------------ emoji

@Composable
fun EmojiPanel(
    theme: Theme,
    heightDp: Float,
    onClose: () -> Unit,
    onBackspace: () -> Unit,
    onPick: (String) -> Unit,
) {
    var category by remember { mutableStateOf(0) }
    PanelFrame(theme = theme, heightDp = heightDp, title = "Emoji", onClose = onClose, onBackspace = onBackspace) { colors ->
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            EmojiData.categories.forEachIndexed { index, group ->
                item {
                    PanelChip(group.name, colors, onClick = { category = index }, selected = index == category)
                }
            }
        }
        // key(...) starts each group scrolled to its top.
        key(category) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(44.dp),
                modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 6.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                items(EmojiData.categories[category].emoji) { emoji ->
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
