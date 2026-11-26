package app.keybuilder.suggest

/*
 * "How likely is it that someone meant WORD when they typed TYPED?"
 *
 * The answer is a cost: 0 means identical, and every slip adds to it. Slips that happen all
 * the time are cheap, unlikely ones are dear:
 *
 *   two letters swapped (teh -> the)                         0.7
 *   a neighbouring key hit instead (thr -> the)              0.75
 *   a letter doubled, or a double typed once (hapy -> happy) 0.5
 *   any other letter missing or extra                        0.9
 *   any other wrong letter                                   1.0
 *   a different first letter                                 0.3 on top (first letters are rarely wrong)
 *
 * Neighbouring keys are taken from the usual QWERTY arrangement.
 */
object Typo {

    const val SWAP = 0.7f
    const val NEIGHBOUR = 0.75f
    const val DOUBLE = 0.5f
    const val MISSING_OR_EXTRA = 0.9f
    const val WRONG = 1.0f
    const val FIRST_LETTER = 0.3f

    /** Returned when the cost is above the limit that was asked for. */
    const val TOO_FAR = Float.MAX_VALUE

    /** Slack allowed when comparing a cost with a limit, so float rounding never decides. */
    const val ROUNDING = 0.0001f

    private val ROWS = listOf("qwertyuiop" to 0f, "asdfghjkl" to 0.5f, "zxcvbnm" to 1.5f)

    /** neighbours[a][b] is true when the keys for the two letters touch. */
    private val neighbours = Array(26) { BooleanArray(26) }

    init {
        ROWS.forEachIndexed { rowA, (lettersA, shiftA) ->
            ROWS.forEachIndexed { rowB, (lettersB, shiftB) ->
                if (kotlin.math.abs(rowA - rowB) <= 1) {
                    lettersA.forEachIndexed { i, a ->
                        lettersB.forEachIndexed { j, b ->
                            val apart = kotlin.math.abs((i + shiftA) - (j + shiftB))
                            if (a != b && apart <= 1.1f) neighbours[a - 'a'][b - 'a'] = true
                        }
                    }
                }
            }
        }
    }

    fun areNeighbours(a: Char, b: Char): Boolean =
        a in 'a'..'z' && b in 'a'..'z' && neighbours[a - 'a'][b - 'a']

    /** Working space for [cost], so checking thousands of words does not create thousands of arrays. */
    class Scratch(size: Int = 40) {
        var twoBack = FloatArray(size)
        var oneBack = FloatArray(size)
        var current = FloatArray(size)
    }

    /**
     * The cost of turning [typed] into [word], or TOO_FAR if it is more than [limit].
     * Both must be lower case. Gives up early on words that are clearly too different.
     */
    fun cost(typed: String, word: String, limit: Float, scratch: Scratch = Scratch()): Float {
        val n = typed.length
        val m = word.length
        if (n == 0 || m == 0) return TOO_FAR
        if (kotlin.math.abs(n - m) * DOUBLE > limit) return TOO_FAR
        if (scratch.current.size <= m) {
            scratch.twoBack = FloatArray(m + 8)
            scratch.oneBack = FloatArray(m + 8)
            scratch.current = FloatArray(m + 8)
        }
        val firstLetter = if (typed[0] != word[0]) FIRST_LETTER else 0f
        val budget = limit - firstLetter
        if (budget < 0f) return TOO_FAR

        var twoBack = scratch.twoBack
        var oneBack = scratch.oneBack
        var current = scratch.current

        // Row 0: nothing typed yet, so every letter of the word so far is "missing".
        oneBack[0] = 0f
        for (j in 1..m) oneBack[j] = oneBack[j - 1] + missingCost(word, j - 1)

        for (i in 1..n) {
            val t = typed[i - 1]
            // The same for every letter of the word, so worked out once per typed letter.
            val extraHere = extraCost(typed, i - 1)
            current[0] = oneBack[0] + extraHere
            var best = current[0]
            for (j in 1..m) {
                val w = word[j - 1]
                var value = if (t == w) {
                    oneBack[j - 1]
                } else {
                    oneBack[j - 1] + if (areNeighbours(t, w)) NEIGHBOUR else WRONG
                }
                val extra = oneBack[j] + extraHere          // a letter that should not be there
                if (extra < value) value = extra
                val missing = current[j - 1] + missingCost(word, j - 1)   // a letter that was left out
                if (missing < value) value = missing
                if (i > 1 && j > 1 && t == word[j - 2] && typed[i - 2] == w && t != w) {
                    val swapped = twoBack[j - 2] + SWAP
                    if (swapped < value) value = swapped
                }
                current[j] = value
                if (value < best) best = value
            }
            // A swap skips a row, and costs at most this much less than passing through it.
            if (best > budget + (WRONG - SWAP)) return TOO_FAR
            val spare = twoBack
            twoBack = oneBack
            oneBack = current
            current = spare
        }
        scratch.twoBack = twoBack
        scratch.oneBack = oneBack
        scratch.current = current
        val total = oneBack[m] + firstLetter
        return if (total <= limit + ROUNDING) total else TOO_FAR
    }

    /**
     * Leaving out the second of two equal letters ("hapy") is the cheap kind of missing letter.
     * Only the second one counts, so leaving out both is not cheap twice over.
     */
    private fun missingCost(word: String, index: Int): Float =
        if (index > 0 && word[index - 1] == word[index]) DOUBLE else MISSING_OR_EXTRA

    /** Typing a letter twice ("thhe") is the cheap kind of extra letter. Again only the second one counts. */
    private fun extraCost(typed: String, index: Int): Float =
        if (index > 0 && typed[index - 1] == typed[index]) DOUBLE else MISSING_OR_EXTRA
}
