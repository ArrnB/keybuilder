package app.keybuilder.suggest

/*
 * What the keyboard has learned from your own typing. It lives in one small text file in the
 * app's private storage on the phone and is never sent anywhere.
 *
 * It remembers four things:
 *   - how often you have typed each word (your own words get suggested, and are not "corrected")
 *   - which word you tend to type after which (for guessing the next word)
 *   - words you told it to leave alone, by undoing a correction
 *   - how you write a word when it is not all lower case ("Aryan", "NASA")
 *
 * No Android code here, so the rules are unit tested.
 */
class UserWords {

    private val counts = HashMap<String, Int>()
    private val pairs = HashMap<String, HashMap<String, Int>>()
    private val kept = HashSet<String>()
    private val shapes = HashMap<String, String>()

    /** True when something was learned since the last save. */
    var changed = false
        private set

    val wordCount: Int get() = counts.size

    fun count(word: String): Int = counts[word] ?: 0

    fun isKept(word: String): Boolean = word in kept

    /** A word you have typed often enough (or kept on purpose) that it counts as a real word for you. */
    fun knows(word: String): Boolean = word in kept || count(word) >= KNOWN_AFTER

    /** How you write [word], if not in plain lower case. */
    fun shape(word: String): String? = shapes[word]

    fun pairCount(previous: String, next: String): Int = pairs[previous]?.get(next) ?: 0

    /** Notes that [word] was typed, after [previous] if there was a word before it. Lower case, please. */
    fun learn(word: String, previous: String? = null, shape: String? = null) {
        if (word.isEmpty() || word.length > MAX_WORD_LENGTH) return
        counts[word] = (count(word) + 1).coerceAtMost(MAX_COUNT)
        if (shape != null && shape != word && shape.lowercase() == word) shapes[word] = shape
        if (!previous.isNullOrEmpty() && previous.length <= MAX_WORD_LENGTH) {
            val after = pairs.getOrPut(previous) { HashMap() }
            after[word] = ((after[word] ?: 0) + 1).coerceAtMost(MAX_COUNT)
        }
        changed = true
        if (counts.size > MAX_WORDS + SLACK) trimWords()
        if (pairs.size > MAX_PAIR_STARTS + SLACK) trimPairs()
    }

    /** Takes back one [learn], used when a correction is undone. */
    fun unlearn(word: String, previous: String? = null) {
        val left = count(word) - 1
        if (left <= 0) {
            counts.remove(word)
            shapes.remove(word)
        } else {
            counts[word] = left
        }
        if (!previous.isNullOrEmpty()) {
            val after = pairs[previous]
            if (after != null) {
                val pairLeft = (after[word] ?: 0) - 1
                if (pairLeft <= 0) after.remove(word) else after[word] = pairLeft
                if (after.isEmpty()) pairs.remove(previous)
            }
        }
        changed = true
    }

    /** From now on [word] is left exactly as typed. */
    fun keep(word: String) {
        if (word.isEmpty() || word.length > MAX_WORD_LENGTH) return
        if (kept.add(word)) changed = true
    }

    /** Forgets one word completely. */
    fun forget(word: String) {
        val had = counts.remove(word) != null
        val wasKept = kept.remove(word)
        shapes.remove(word)
        pairs.remove(word)
        for (after in pairs.values) after.remove(word)
        if (had || wasKept) changed = true
    }

    /** The words you most often type after [previous], most frequent first. */
    fun nextWords(previous: String, limit: Int, atLeast: Int = 2): List<WordScore> =
        pairs[previous].orEmpty().entries
            .filter { it.value >= atLeast }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(limit)
            .map { WordScore(it.key, it.value) }

    /** Your own words that start with [prefix], with how often you typed them. */
    fun completions(prefix: String, limit: Int): List<WordScore> {
        if (prefix.isEmpty()) return emptyList()
        return counts.entries
            .filter { it.key.length > prefix.length && it.key.startsWith(prefix) && knows(it.key) }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(limit)
            .map { WordScore(it.key, it.value) }
    }

    /** Your own words that [typed] could be a slip for. */
    fun near(typed: String, limit: Float, scratch: Typo.Scratch = Typo.Scratch()): List<NearWord> {
        val found = ArrayList<NearWord>()
        for ((word, count) in counts) {
            if (word == typed || !knows(word)) continue
            if (kotlin.math.abs(word.length - typed.length) > 2) continue
            val cost = Typo.cost(typed, word, limit, scratch)
            if (cost != Typo.TOO_FAR) found.add(NearWord(word, count, cost))
        }
        return found
    }

    /** Marks everything as saved. Call after writing [toText] to the file. */
    fun saved() {
        changed = false
    }

    // ------------------------------------------------------------------ the file

    /**
     * One thing per line, parts separated by tabs:
     *   w  word  count  [how you write it]
     *   k  word                    (leave this word alone)
     *   p  word  next  count       (next was typed after word)
     */
    fun toText(): String = buildString {
        append(HEADER).append('\n')
        for (word in counts.keys.sorted()) {
            append("w\t").append(word).append('\t').append(counts[word])
            shapes[word]?.let { append('\t').append(it) }
            append('\n')
        }
        for (word in kept.sorted()) append("k\t").append(word).append('\n')
        for (previous in pairs.keys.sorted()) {
            val after = pairs[previous] ?: continue
            for (next in after.keys.sorted()) {
                append("p\t").append(previous).append('\t').append(next).append('\t').append(after[next]).append('\n')
            }
        }
    }

    private fun trimWords() {
        // Drop the words typed least. Words typed once are usually slips anyway.
        val keepers = counts.entries.sortedByDescending { it.value }.take(MAX_WORDS).map { it.key }.toHashSet()
        counts.keys.retainAll(keepers)
        shapes.keys.retainAll(keepers)
    }

    private fun trimPairs() {
        val keepers = pairs.entries.sortedByDescending { it.value.values.sum() }.take(MAX_PAIR_STARTS).map { it.key }.toHashSet()
        pairs.keys.retainAll(keepers)
        for (after in pairs.values) {
            if (after.size > MAX_NEXT_PER_WORD) {
                val best = after.entries.sortedByDescending { it.value }.take(MAX_NEXT_PER_WORD).map { it.key }.toHashSet()
                after.keys.retainAll(best)
            }
        }
    }

    companion object {
        /** A new word counts as yours once you have typed it this many times. */
        const val KNOWN_AFTER = 2

        const val MAX_WORD_LENGTH = 24
        const val MAX_COUNT = 9999
        const val MAX_WORDS = 4000
        const val MAX_PAIR_STARTS = 3000
        const val MAX_NEXT_PER_WORD = 12
        private const val SLACK = 200
        private const val HEADER = "# KeyBuilder learned words. Stays on this phone."

        /** Reads what [toText] wrote. Lines that do not fit are skipped, so a damaged file loses little. */
        fun parse(text: String): UserWords {
            val user = UserWords()
            for (line in text.lineSequence()) {
                if (line.isEmpty() || line.startsWith("#")) continue
                val parts = line.split('\t')
                when (parts[0]) {
                    "w" -> {
                        val word = parts.getOrNull(1).orEmpty()
                        val count = parts.getOrNull(2)?.toIntOrNull() ?: continue
                        if (word.isEmpty() || word.length > MAX_WORD_LENGTH || count <= 0) continue
                        user.counts[word] = count.coerceAtMost(MAX_COUNT)
                        val shape = parts.getOrNull(3)
                        if (shape != null && shape != word && shape.lowercase() == word) user.shapes[word] = shape
                    }
                    "k" -> parts.getOrNull(1)?.takeIf { it.isNotEmpty() && it.length <= MAX_WORD_LENGTH }?.let { user.kept.add(it) }
                    "p" -> {
                        val previous = parts.getOrNull(1).orEmpty()
                        val next = parts.getOrNull(2).orEmpty()
                        val count = parts.getOrNull(3)?.toIntOrNull() ?: continue
                        if (previous.isEmpty() || next.isEmpty() || count <= 0) continue
                        user.pairs.getOrPut(previous) { HashMap() }[next] = count.coerceAtMost(MAX_COUNT)
                    }
                }
            }
            return user
        }
    }
}
