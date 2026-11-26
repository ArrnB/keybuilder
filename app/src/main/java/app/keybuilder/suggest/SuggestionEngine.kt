package app.keybuilder.suggest

import kotlin.math.ln

/*
 * Suggestions and autocorrect.
 *
 * Nothing clever or hidden happens here, and no AI model is involved:
 *
 *   - Suggestions are the commonest words that start with what you have typed, plus words
 *     that what you typed could be a slip for. Words you use yourself, and words you often
 *     type after the previous word, are pushed up the list.
 *   - Autocorrect only steps in when it is fairly sure: the word is not a word anyone
 *     knows (or is a rare one sitting one slip away from a very common one), and a common
 *     English word is close by. It never touches a Hinglish word and never fires inside a
 *     Hinglish sentence, because Hinglish has no fixed spelling to correct towards.
 *   - Undo a correction once (backspace right after it) and that word is left alone for good.
 *
 * It adapts to the person typing, using nothing but what they typed on this phone:
 *   - words typed twice become "yours": suggested, never corrected, and slips are fixed towards them
 *   - the word you usually type after another is offered as the next word
 *   - a replacement you pick twice ("tmrw", then tapping "tomorrow") is made for you from then on
 *   - if most of what you type is Hinglish, an unknown word that starts a sentence is taken
 *     to be Hinglish too and left alone
 *
 * No Android code here. The keyboard service reads the text before the cursor, hands it
 * over as a TypingContext, and types whatever comes back.
 */

/** Where the cursor is, in terms a word list can work with. */
data class TypingContext(
    /** The letters right before the cursor, exactly as typed. Empty between words. */
    val word: String,
    /** Earlier words of the same sentence, nearest first, lower case. "" marks something that is not a plain word. */
    val previous: List<String> = emptyList(),
    /** True when [word] is the first word of its sentence, where a capital means nothing. */
    val sentenceStart: Boolean = true,
    /** False when the word is glued to digits or to marks like @ / . (an e-mail, a link, code). */
    val plain: Boolean = true,
) {
    /** The word just before this one, if it is a plain word. */
    val before: String? get() = previous.firstOrNull()?.takeIf { it.isNotEmpty() }

    companion object {
        /** How much text before the cursor is worth reading. */
        const val LOOK_BACK = 64

        private const val GLUE = "@#/\\._:=&%+~<>$^|`"

        fun isWordChar(c: Char): Boolean = c.isLetter() || c == '\'' || c == '’'

        /** Works out the context from the text just before the cursor. */
        fun from(textBeforeCursor: String): TypingContext {
            val text = textBeforeCursor
            var start = text.length
            while (start > 0 && isWordChar(text[start - 1])) start--
            var word = text.substring(start).replace('’', '\'')
            // A quote mark in front of a word is not part of it.
            while (word.startsWith("'")) {
                word = word.substring(1)
                start++
            }
            val edge = if (start > 0) text[start - 1] else null
            val plain = edge == null || !(edge.isDigit() || edge in GLUE)

            val previous = ArrayList<String>()
            var sentenceStart = false
            var i = start
            while (previous.size < 3) {
                // Step back over spaces and marks; a full stop or a new line ends the sentence.
                var ended = false
                while (i > 0) {
                    val c = text[i - 1]
                    if (c == '\n' || c == '.' || c == '!' || c == '?') {
                        ended = true
                        break
                    }
                    if (isWordChar(c) || c.isDigit()) break
                    i--
                }
                if (ended || i == 0) {
                    if (previous.isEmpty()) sentenceStart = true
                    break
                }
                val end = i
                while (i > 0 && (isWordChar(text[i - 1]) || text[i - 1].isDigit())) i--
                val token = text.substring(i, end).replace('’', '\'').trim('\'')
                previous.add(if (token.isNotEmpty() && token.all { it.isLetter() || it == '\'' }) token.lowercase() else "")
            }
            return TypingContext(word, previous, sentenceStart, plain)
        }
    }
}

enum class SuggestionKind {
    /** An ordinary suggestion. */
    WORD,

    /** What autocorrect will put in when the word is finished. */
    CORRECTION,

    /** What was typed, offered so it can be kept as it is. */
    TYPED,

    /** The full text of one of your own text shortcuts, which is about to replace what was typed. */
    SHORTCUT,
}

data class Suggestion(val text: String, val kind: SuggestionKind = SuggestionKind.WORD)

class SuggestionEngine(val lexicon: Lexicon, val user: UserWords = UserWords()) {

    /** Words to leave alone until the keyboard restarts. Used where nothing may be remembered. */
    private val keptForNow = HashSet<String>()

    private val scratch = Typo.Scratch()

    /**
     * The last look-alike search in the word list, kept because one keystroke needs it twice
     * (for the suggestions and for autocorrect) and the finished word needs it again. The
     * word list never changes, so this can only go stale when a different word is typed.
     */
    private var nearTyped: String? = null
    private var nearFound: List<NearWord> = emptyList()

    /**
     * Word-list words within [limit] of [lower]. Searches once, at the widest limit any caller
     * uses for this length, and narrows that down: a search with a wider limit finds every word
     * a narrower one would, in the same order and with the same costs.
     */
    private fun lexiconNear(lower: String, limit: Float): List<NearWord> {
        val widest = maxOf(offerLimit(lower.length), slipLimit(lower.length), SMALL_SLIP)
        if (limit > widest) return lexicon.near(lower, limit, scratch)
        if (nearTyped != lower) {
            nearFound = lexicon.near(lower, widest, scratch)
            nearTyped = lower
        }
        return if (limit >= widest) nearFound else nearFound.filter { it.cost <= limit + Typo.ROUNDING }
    }

    /** Whether this person types mostly Hinglish. Worked out from the learned words now and then. */
    private var leaning: Boolean? = null
    private var learnedSinceCheck = 0

    // ------------------------------------------------------------------ suggestions

    /**
     * What to show above the keys, best guesses first. With [autocorrect] on, a word that is
     * about to be corrected comes as: what you typed (tap to keep it), then the correction.
     */
    fun suggestions(context: TypingContext, autocorrect: Boolean, limit: Int = 3): List<Suggestion> {
        if (!context.plain || limit <= 0) return emptyList()
        val typed = context.word
        if (typed.isEmpty()) return nextWords(context, limit)
        val lower = typed.lowercase()
        if (!isSimple(lower)) return emptyList()

        val out = ArrayList<Suggestion>()
        val fix = if (autocorrect) correction(context) else null
        if (fix != null) {
            out.add(Suggestion(typed, SuggestionKind.TYPED))
            out.add(Suggestion(fix, SuggestionKind.CORRECTION))
        }
        for (candidate in ranked(context, lower)) {
            if (out.size >= limit) break
            val text = shaped(candidate, typed)
            if (text == typed || out.any { it.text == text }) continue
            out.add(Suggestion(text))
        }
        return out.take(limit)
    }

    /** Guesses for the next word, from what you usually type after the previous one. */
    private fun nextWords(context: TypingContext, limit: Int): List<Suggestion> {
        val previous = context.before ?: return emptyList()
        val out = ArrayList<String>()
        for (next in user.nextWords(previous, limit)) {
            if (lexicon.score(next.word) != 0) out.add(next.word)
        }
        for (next in STARTERS[previous].orEmpty()) {
            if (next !in out) out.add(next)
        }
        return out.take(limit).map { Suggestion(user.shape(it) ?: PROPER[it] ?: it) }
    }

    /** Every candidate for [lower], best first. */
    private fun ranked(context: TypingContext, lower: String): List<String> {
        val values = HashMap<String, Float>()
        fun offer(word: String, value: Float) {
            val old = values[word]
            if (old == null || value > old) values[word] = value
        }

        for (c in lexicon.completions(lower, 14)) {
            offer(c.word, c.score - EXTRA_LETTER * (c.word.length - lower.length))
        }
        for (c in user.completions(lower, 8)) {
            offer(c.word, userScore(c.score) - EXTRA_LETTER * (c.word.length - lower.length))
        }
        // Whatever you picked for this before comes first.
        for (meant in user.fixChoices(lower)) offer(meant, 900f + user.fixCount(lower, meant))
        // A solid word needs no "did you mean". Anything else gets look-alikes too.
        val solid = (lexicon.score(lower) ?: 0) >= RARE_BELOW || user.knows(lower)
        if (!solid && lower.length >= 3) {
            val limit = offerLimit(lower.length)
            for (n in lexiconNear(lower, limit)) offer(n.word, n.score - SLIP * n.cost)
            for (n in user.near(lower, limit, scratch)) offer(n.word, userScore(n.score) - SLIP * n.cost)
        }

        val hinglish = isHinglish(context)
        val previous = context.before
        return values.entries
            .map { (word, value) -> word to value + bonus(word, previous, hinglish) }
            .sortedWith(compareByDescending<Pair<String, Float>> { it.second }.thenBy { it.first })
            .map { it.first }
    }

    /** Extra points for your own words, for words that follow the previous one, and for the sentence's language. */
    private fun bonus(word: String, previous: String?, hinglish: Boolean): Float {
        var extra = 0f
        val typedBefore = user.count(word)
        if (typedBefore > 0) extra += (USED_BEFORE * log2(typedBefore + 1f)).coerceAtMost(120f)
        if (previous != null) {
            val together = user.pairCount(previous, word)
            if (together > 0) extra += (80f + 40f * log2(together + 1f)).coerceAtMost(250f)
            else if (STARTERS[previous]?.contains(word) == true) extra += 120f
        }
        val language = lexicon.language(word)
        if (hinglish && language == 'e') extra -= 60f
        if (hinglish && language == 'h') extra += 60f
        return extra
    }

    // ------------------------------------------------------------------ autocorrect

    /**
     * What the word before the cursor should be changed to now that it is finished,
     * or null to leave it alone (which is the answer most of the time).
     */
    fun correction(context: TypingContext): String? {
        if (!context.plain) return null
        val typed = context.word
        val lower = typed.lowercase()
        if (lower.isEmpty() || !lower.all { it in 'a'..'z' }) return null
        if (user.knows(lower) || lower in keptForNow) return null
        // Your own habit comes before every other rule: you picked this replacement yourself, more than once.
        user.fix(lower)?.let { mine -> return shaped(mine, typed).takeIf { it != typed } }
        if (isHinglish(context)) return null

        // A few everyday fixes that need no guessing: i -> I, dont -> don't.
        ALWAYS[lower]?.let { fixed -> return shaped(fixed, typed).takeIf { it != typed } }
        if (lower.length < 3) return null

        // Capitals in the middle of a sentence are deliberate: a name, or shouting.
        val capitals = typed.count { it.isUpperCase() }
        if (capitals > 1 || (capitals == 1 && !context.sentenceStart)) return null
        // So are stretched words and laughs: "sooo", "hahaha", "hmm".
        if (isStretched(lower)) return null
        // A known word with an ordinary ending ("anthems", "defusing") is a word, even if no list has it.
        if (!lexicon.contains(lower) && isBuiltOnKnownWord(lower)) return null
        // With nothing before it to go on, an unknown word from someone who mostly types Hinglish is left alone.
        if (context.previous.isEmpty() && !lexicon.contains(lower) && leansHinglish()) return null

        val choices = fixes(context, lower)
        val best = choices.firstOrNull() ?: return null
        // Two words about equally likely ("helo": help or hello?) is not sure enough to change anything.
        val runnerUp = choices.getOrNull(1)
        if (runnerUp != null && best.second - runnerUp.second < CLEAR_LEAD) return null
        // The bigger the slip, the commoner the word must be ("tomor" is not "tumor"). Short words
        // have many neighbours, so there the guess has to be a strong one ("hbd" is not "had").
        if (best.second < (if (lower.length <= 4) SHORT_WORD_FLOOR else FLOOR)) return null
        return shaped(best.first, typed).takeIf { it != typed }
    }

    /** The words [lower] might be changed into, with how good each is, best first. Empty means hands off. */
    internal fun fixes(context: TypingContext, lower: String): List<Pair<String, Float>> {
        val known = lexicon.score(lower)
        val allowed: List<NearWord>
        if (known != null) {
            // A real word. Only a rare English one, one small slip from a far commoner one, is changed.
            if (known == 0 || known >= RARE_BELOW || lexicon.language(lower) != 'e') return emptyList()
            allowed = lexiconNear(lower, SMALL_SLIP)
                .filter { !lexicon.isHinglishOnly(it.word) && it.score >= VERY_COMMON && it.score - known >= MUCH_COMMONER }
        } else {
            val limit = slipLimit(lower.length)
            val mine = user.near(lower, limit, scratch).map { NearWord(it.word, userScore(it.score).toInt(), it.cost) }
            val all = lexiconNear(lower, limit) + mine
            val overall = all.maxByOrNull { value(it, context) } ?: return emptyList()
            // If the likeliest word is Hinglish, this may well be Hinglish spelt another way. Hands off.
            if (lexicon.isHinglishOnly(overall.word)) return emptyList()
            val needed = if (lower.length <= 4) COMMON_SHORT else COMMON
            allowed = all.filter { !lexicon.isHinglishOnly(it.word) && it.score >= needed }
        }
        // A word can turn up twice, once from the list and once from your own words. Count it once.
        val best = HashMap<String, Float>()
        for (near in allowed) {
            val worth = value(near, context)
            if (worth > (best[near.word] ?: Float.NEGATIVE_INFINITY)) best[near.word] = worth
        }
        return best.entries
            .map { it.key to it.value }
            .sortedWith(compareByDescending<Pair<String, Float>> { it.second }.thenBy { it.first })
    }

    private fun value(near: NearWord, context: TypingContext): Float =
        near.score - SLIP * near.cost + bonus(near.word, context.before, hinglish = false)

    /** A sentence counts as Hinglish as soon as one of its last few words is Hinglish and not also English. */
    private fun isHinglish(context: TypingContext): Boolean = context.previous.any { lexicon.isHinglishOnly(it) }

    /**
     * True once there is enough to go on (LEAN_AFTER typed words that belong to one language
     * only) and most of them are Hinglish.
     */
    fun leansHinglish(): Boolean {
        leaning?.let { return it }
        var hinglish = 0
        var english = 0
        user.forEachWord { word, count ->
            when (lexicon.language(word)) {
                'h' -> hinglish += count
                'e' -> english += count
            }
        }
        val result = hinglish + english >= LEAN_AFTER && hinglish >= LEAN_SHARE * (hinglish + english)
        leaning = result
        learnedSinceCheck = 0
        return result
    }

    // ------------------------------------------------------------------ learning

    /** Notes that [word] was typed at [context]. Call once per finished word, where learning is allowed. */
    fun learn(context: TypingContext, word: String) {
        if (!context.plain) return
        val clean = word.replace('’', '\'').trim('\'')
        val lower = clean.lowercase()
        if (lower.length < 2 && lower != "i" && lower != "a") return
        if (!isSimple(lower) || lower.length > UserWords.MAX_WORD_LENGTH) return
        if (lexicon.score(lower) == 0) return // rude words are never corrected, and never learned or offered
        // A capital at the start of a sentence says nothing about the word; anywhere else it does.
        val deliberate = clean != lower && (!context.sentenceStart || clean.count { it.isUpperCase() } > 1)
        user.learn(lower, context.before, shape = clean.takeIf { deliberate })
        if (++learnedSinceCheck >= 20) leaning = null // look again at which language you mostly type
    }

    /**
     * A suggestion was tapped while [context]'s word was being typed. If that replaced the word
     * rather than finishing it ("tmrw" -> "tomorrow", not "tom" -> "tomorrow"), it is remembered
     * as your own correction; picked twice, it becomes automatic.
     */
    fun learnChoice(context: TypingContext, chosen: String) {
        if (!context.plain) return
        val typed = context.word.lowercase()
        val meant = chosen.lowercase()
        if (typed.length < 2 || !typed.all { it in 'a'..'z' } || !isSimple(meant)) return
        if (meant == typed || meant.startsWith(typed)) return
        if (lexicon.score(meant) == 0) return
        user.learnFix(typed, meant)
    }

    /**
     * A correction was undone: [original] is what was typed and should have been left alone.
     * With [remember] false (private boxes) this only lasts until the keyboard restarts.
     */
    fun correctionUndone(context: TypingContext, original: String, corrected: String, remember: Boolean) {
        val lower = original.lowercase()
        if (!remember) {
            keptForNow.add(lower)
            return
        }
        user.unlearn(corrected.lowercase(), context.before)
        user.forgetFix(lower) // if the correction was a habit of yours, it is not one any more
        user.keep(lower)
        learn(context, original)
    }

    /** "Leave this word as I typed it", from tapping the typed word in the suggestions. */
    fun keep(word: String, remember: Boolean) {
        val lower = word.lowercase()
        if (remember) user.keep(lower) else keptForNow.add(lower)
    }

    // ------------------------------------------------------------------ helpers

    /** Writes [word] the way [typed] was started: Capitalised, ALL CAPS, or as the word normally is. */
    fun shaped(word: String, typed: String): String {
        val base = user.shape(word) ?: PROPER[word] ?: word
        val capitals = typed.count { it.isUpperCase() }
        return when {
            typed.length > 1 && capitals == typed.count { it.isLetter() } -> base.uppercase()
            typed.firstOrNull()?.isUpperCase() == true -> base.replaceFirstChar { it.uppercase() }
            else -> base
        }
    }

    /** True if [lower] is a word from the list with a common ending added: -s, -ed, -ing, -ly and so on. */
    internal fun isBuiltOnKnownWord(lower: String): Boolean {
        for (ending in ENDINGS) {
            if (!lower.endsWith(ending)) continue
            val stem = lower.dropLast(ending.length)
            if (stem.length < 4) continue
            if (isStem(stem)) return true
            if (isStem(stem + "e")) return true                                        // making -> make
            if (stem.endsWith("i") && isStem(stem.dropLast(1) + "y")) return true      // carries -> carry
            val n = stem.length
            if (stem[n - 1] == stem[n - 2] && isStem(stem.dropLast(1))) return true    // stopped -> stop
        }
        return false
    }

    private fun isStem(word: String): Boolean = (lexicon.score(word) ?: 0) > 0

    /** Three of the same letter in a row, or a word made of only one or two different letters. */
    private fun isStretched(lower: String): Boolean {
        for (i in 2 until lower.length) {
            if (lower[i] == lower[i - 1] && lower[i] == lower[i - 2]) return true
        }
        return lower.toSet().size <= 2
    }

    private fun isSimple(lower: String): Boolean =
        lower.isNotEmpty() && lower.all { it in 'a'..'z' || it == '\'' } && lower.any { it != '\'' }

    /** Turns "typed this many times" into a score comparable with the word list's. */
    private fun userScore(count: Int): Float = (380f + 60f * log2(count + 1f)).coerceAtMost(700f)

    private fun log2(x: Float): Float = (ln(x.toDouble()) / ln(2.0)).toFloat()

    companion object {
        /** How many one-language words must be learned before guessing which language you mostly type, and the share needed. */
        const val LEAN_AFTER = 40
        const val LEAN_SHARE = 0.6f

        /** A word-list word scoring below this is rare enough to be a slip for something else. */
        const val RARE_BELOW = 400
        const val VERY_COMMON = 500
        const val MUCH_COMMONER = 280

        /** For real words only the smallest slips count: two letters swapped, or a letter doubled. */
        const val SMALL_SLIP = Typo.SWAP

        val ENDINGS = listOf("'s", "ings", "ing", "ers", "est", "ed", "es", "er", "ly", "s", "d", "ness", "less", "ful", "ment", "ments", "able")

        /** How far ahead of the next best guess a correction must be before it is made without asking. */
        const val CLEAR_LEAD = 80f

        const val SHORT_WORD_FLOOR = 360f
        const val FLOOR = 100f

        /** How common a word must be before an unknown word is changed into it. */
        const val COMMON = 380
        const val COMMON_SHORT = 450

        /** Points lost per unit of typo cost, per letter still to type, and gained per doubling of your own use. */
        const val SLIP = 300f
        const val EXTRA_LETTER = 12f
        const val USED_BEFORE = 20f

        /**
         * How big a slip autocorrect will fix by itself: one, or in a long word one and a small one.
         * Measured against a list of 32,000 common misspellings, allowing more than this fixed few
         * extra words and tripled the number changed into the WRONG word.
         */
        fun slipLimit(length: Int): Float = if (length <= 7) 1.0f else 1.5f

        /** Suggestions are only offered, never forced, so they can reach further. */
        fun offerLimit(length: Int): Float = when {
            length <= 4 -> 1.0f
            length <= 7 -> 1.5f
            else -> 2.0f
        }

        /** Fixed without guessing. Only spellings that are never what someone meant are here. */
        val ALWAYS: Map<String, String> = mapOf(
            "i" to "I", "im" to "I'm", "ive" to "I've",
            "dont" to "don't", "doesnt" to "doesn't", "didnt" to "didn't", "cant" to "can't",
            "couldnt" to "couldn't", "shouldnt" to "shouldn't", "wouldnt" to "wouldn't",
            "isnt" to "isn't", "arent" to "aren't", "wasnt" to "wasn't", "werent" to "weren't",
            "hasnt" to "hasn't", "havent" to "haven't", "hadnt" to "hadn't", "wont" to "won't",
            "thats" to "that's", "whats" to "what's", "youre" to "you're", "theyre" to "they're",
            "youve" to "you've", "weve" to "we've", "theyve" to "they've", "youll" to "you'll",
            "theyll" to "they'll", "shes" to "she's", "theres" to "there's", "heres" to "here's",
            "aint" to "ain't",
        )

        /** Words that always start with a capital. The word list itself is all lower case. */
        val PROPER: Map<String, String> = listOf(
            "I", "I'm", "I've", "I'll", "I'd",
            "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday",
            "January", "February", "April", "June", "July", "August", "September", "October", "November", "December",
            "India", "Indian", "English", "Hindi", "Delhi", "Mumbai", "Google", "WhatsApp", "Instagram", "YouTube",
        ).associateBy { it.lowercase() }

        /**
         * A small head start for next-word guesses, before the keyboard has learned yours.
         * Your own habits take over as soon as there are any.
         */
        val STARTERS: Map<String, List<String>> = mapOf(
            "i" to listOf("am", "have", "will"),
            "i'm" to listOf("not", "going", "sorry"),
            "you" to listOf("are", "can", "have"),
            "we" to listOf("are", "can", "will"),
            "they" to listOf("are", "have", "will"),
            "it" to listOf("is", "was", "will"),
            "how" to listOf("are", "is", "much"),
            "what" to listOf("is", "are", "do"),
            "where" to listOf("are", "is", "do"),
            "are" to listOf("you", "we", "they"),
            "do" to listOf("you", "not", "it"),
            "can" to listOf("you", "we", "i"),
            "thank" to listOf("you"),
            "thanks" to listOf("for", "a"),
            "good" to listOf("morning", "night", "luck"),
            "happy" to listOf("birthday", "to"),
            "see" to listOf("you", "the"),
            "to" to listOf("the", "be", "do"),
            "of" to listOf("the", "a", "my"),
            "in" to listOf("the", "a", "my"),
            "on" to listOf("the", "my", "a"),
            "for" to listOf("the", "a", "you"),
            "at" to listOf("the", "home", "least"),
            "want" to listOf("to", "a", "the"),
            "going" to listOf("to", "on", "out"),
            "kya" to listOf("hai", "kar", "hua"),
            "kaise" to listOf("ho", "hai", "hain"),
            "kaha" to listOf("ho", "hai", "pe"),
            "kahan" to listOf("ho", "hai", "pe"),
            "kab" to listOf("tak", "aa", "hai"),
            "kyu" to listOf("nahi", "ki", "kya"),
            "nahi" to listOf("hai", "ho", "pata"),
            "kar" to listOf("raha", "rahi", "do"),
            "ho" to listOf("gaya", "raha", "jayega"),
            "theek" to listOf("hai", "ho"),
            "thik" to listOf("hai", "ho"),
            "acha" to listOf("theek", "hai", "to"),
            "main" to listOf("theek", "bhi", "nahi"),
            "mai" to listOf("theek", "bhi", "nahi"),
            "tum" to listOf("kya", "kaha", "kaise"),
            "aap" to listOf("kaise", "kya", "kaha"),
            "kuch" to listOf("nahi", "bhi", "to"),
            "pata" to listOf("nahi", "hai", "chala"),
            "aa" to listOf("raha", "rahi", "jao"),
            "ja" to listOf("raha", "rahi", "rahe"),
        )
    }
}
