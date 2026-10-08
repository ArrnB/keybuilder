package app.keybuilder.suggest

/*
 * Fitting two or three suggestions into the strip so that they can be told apart.
 *
 * Simply cutting long words off at the end fails exactly when it matters: "Carbonate",
 * "Carbonated" and "Carbonara" all become "Carbon...". So: longer words get more of the
 * width, the letters shrink (down to a limit) until everything fits, and a word that still
 * does not fit loses its MIDDLE, keeping the start and the end: "Carb…ated".
 */

/** How the strip should draw its suggestions: one letter size for all, and for each word its share of the width and its text. */
data class StripFit(val fontSp: Float, val labels: List<String>, val weights: List<Float>)

/** [text] in at most [maxChars] characters, with the middle left out if it is longer. */
fun fitWord(text: String, maxChars: Int): String {
    val room = maxChars.coerceAtLeast(3)
    if (text.length <= room) return text
    val head = (room - 1 + 1) / 2
    val tail = room - 1 - head
    return text.take(head) + "…" + text.takeLast(tail)
}

/**
 * [widthDp] is the room for all the words together. [fixedWidth] says the font's letters
 * are all equally wide (typewriter fonts), which makes words wider than in other fonts.
 */
fun fitSuggestions(
    words: List<String>,
    widthDp: Float,
    fixedWidth: Boolean,
    maxSp: Float = 15f,
    minSp: Float = 10.5f,
    paddingDp: Float = 10f,
): StripFit {
    if (words.isEmpty()) return StripFit(maxSp, emptyList(), emptyList())
    // A letter is roughly this fraction of the font size wide.
    val letter = if (fixedWidth) 0.62f else 0.54f
    // Very short words still get a comfortable place to tap.
    val weights = words.map { it.length.coerceAtLeast(4).toFloat() }
    val usable = (widthDp - paddingDp * words.size).coerceAtLeast(1f)
    val total = weights.sum()
    val font = (usable / (total * letter)).coerceIn(minSp, maxSp)
    val labels = words.mapIndexed { i, word ->
        val cell = usable * weights[i] / total
        fitWord(word, (cell / (letter * font)).toInt())
    }
    return StripFit(font, labels, weights)
}
