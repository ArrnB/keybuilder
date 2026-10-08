package app.keybuilder.suggest

/*
 * The built-in word list: about 36,000 English and Hinglish words, each with a score for
 * how common it is. It is read from assets/words/words.txt (made by tools/build_words.py).
 *
 * No Android code here. Everything is kept in plain arrays, which is the compact way to
 * hold this many words in a phone's memory.
 */

/** A word from a list, with how common it is (roughly 100 = very rare, 700+ = "the"). */
data class WordScore(val word: String, val score: Int)

/** A word that is close to what was typed. [cost] says how close, see Typo. */
data class NearWord(val word: String, val score: Int, val cost: Float)

class Lexicon private constructor(
    private val words: Array<String>,
    private val scores: IntArray,
    private val languages: CharArray,
) {
    private val index = HashMap<String, Int>(words.size * 2)

    /** For each word, one bit per letter it contains. Lets most words be ruled out at a glance. */
    private val letters = IntArray(words.size)

    /** Word numbers grouped by word length. */
    private val byLength: Array<IntArray>

    init {
        val groups = HashMap<Int, ArrayList<Int>>()
        for (i in words.indices) {
            index[words[i]] = i
            letters[i] = letterBits(words[i])
            groups.getOrPut(words[i].length) { ArrayList() }.add(i)
        }
        val longest = groups.keys.maxOrNull() ?: 0
        byLength = Array(longest + 1) { length -> groups[length]?.toIntArray() ?: IntArray(0) }
    }

    val size: Int get() = words.size

    /** How common [word] (lower case) is, or null if it is not in the list. 0 = known but never offered. */
    fun score(word: String): Int? = index[word]?.let { scores[it] }

    fun contains(word: String): Boolean = index.containsKey(word)

    /** 'e' English, 'h' Hinglish, 'b' both, or null if the word is not in the list. */
    fun language(word: String): Char? = index[word]?.let { languages[it] }

    /** True for words that are Hinglish and not also English. These tell us a sentence is Hinglish. */
    fun isHinglishOnly(word: String): Boolean = language(word) == 'h'

    /** The most common words that start with [prefix] and are longer than it. */
    fun completions(prefix: String, limit: Int): List<WordScore> {
        if (prefix.isEmpty() || limit <= 0) return emptyList()
        var low = 0
        var high = words.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (words[middle] < prefix) low = middle + 1 else high = middle
        }
        val best = ArrayList<WordScore>(limit + 1)
        var i = low
        while (i < words.size && words[i].startsWith(prefix)) {
            val score = scores[i]
            if (score > 0 && words[i].length > prefix.length && (best.size < limit || score > best.last().score)) {
                // Keep the list sorted, best first, and never longer than the limit.
                var at = best.size
                while (at > 0 && best[at - 1].score < score) at--
                best.add(at, WordScore(words[i], score))
                if (best.size > limit) best.removeAt(best.size - 1)
            }
            i++
        }
        return best
    }

    /** Words that [typed] could be a slip for: everything within [limit] cost, except [typed] itself. */
    fun near(typed: String, limit: Float, scratch: Typo.Scratch = Typo.Scratch()): List<NearWord> {
        val found = ArrayList<NearWord>()
        if (typed.isEmpty()) return found
        val bits = letterBits(typed)
        // One wrong letter changes two bits; a swap or a doubled letter changes none.
        val maxBits = if (limit <= 1.0f) 2 else 4
        val reach = (limit / Typo.DOUBLE).toInt().coerceAtMost(2)
        val from = (typed.length - reach).coerceAtLeast(1)
        val to = (typed.length + reach).coerceAtMost(byLength.size - 1)
        for (length in from..to) {
            for (i in byLength[length]) {
                if (scores[i] == 0) continue
                if (Integer.bitCount(bits xor letters[i]) > maxBits) continue
                val word = words[i]
                if (word == typed) continue
                val cost = Typo.cost(typed, word, limit, scratch)
                if (cost != Typo.TOO_FAR) found.add(NearWord(word, scores[i], cost))
            }
        }
        return found
    }

    companion object {
        val EMPTY = Lexicon(emptyArray(), IntArray(0), CharArray(0))

        /** Reads lines of "word TAB score TAB language". Lines that do not fit are skipped. */
        fun parse(lines: Sequence<String>): Lexicon {
            class Entry(val word: String, val score: Int, val language: Char)

            val seen = HashMap<String, Entry>()
            for (line in lines) {
                val parts = line.split('\t')
                if (parts.size < 2) continue
                val word = parts[0].trim()
                val score = parts[1].trim().toIntOrNull() ?: continue
                if (word.isEmpty()) continue
                val language = parts.getOrNull(2)?.trim()?.firstOrNull()?.takeIf { it in "ehb" } ?: 'e'
                seen[word] = Entry(word, score.coerceIn(0, 999), language)
            }
            val sorted = seen.values.sortedBy { it.word }
            return Lexicon(
                words = Array(sorted.size) { sorted[it].word },
                scores = IntArray(sorted.size) { sorted[it].score },
                languages = CharArray(sorted.size) { sorted[it].language },
            )
        }

        fun letterBits(word: String): Int {
            var bits = 0
            for (c in word) if (c in 'a'..'z') bits = bits or (1 shl (c - 'a'))
            return bits
        }
    }
}
