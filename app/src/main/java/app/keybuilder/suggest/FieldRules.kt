package app.keybuilder.suggest

/**
 * What the keyboard may do in the text box being typed in.
 *
 * This is where "never in password boxes" is decided. When everything here is false the
 * keyboard does not even look at the text in the box. Where only [shortcuts] is true
 * (e-mail and link boxes, boxes that ask for no suggestions) it looks at the last few
 * characters, and only if you have made text shortcuts, to see whether you typed one.
 *
 * Apps describe a text box with two numbers, its "input type" and its "IME options"
 * (Android's InputType and EditorInfo). The numbers below are Android's own; they are
 * copied here so this rule has no Android code in it and can be unit tested.
 */
data class FieldRules(
    /** Show suggestions above the keys. */
    val suggest: Boolean,
    /** Fix slips automatically. */
    val correct: Boolean,
    /** Remember the words typed here. */
    val learn: Boolean,
    /** Turn your own text shortcuts into their full text. */
    val shortcuts: Boolean = false,
) {
    /** True when nothing at all is allowed, so the box must not be read. */
    val nothing: Boolean get() = !suggest && !correct && !learn && !shortcuts

    companion object {
        val NOTHING = FieldRules(suggest = false, correct = false, learn = false)
        val EVERYTHING = FieldRules(suggest = true, correct = true, learn = true, shortcuts = true)

        /** Your own shortcuts and nothing else: an "@@" for your address belongs in an e-mail box. */
        val SHORTCUTS_ONLY = FieldRules(suggest = false, correct = false, learn = false, shortcuts = true)

        const val MASK_CLASS = 0x0000000f
        const val MASK_VARIATION = 0x00000ff0
        const val CLASS_TEXT = 0x00000001

        const val VARIATION_URI = 0x10
        const val VARIATION_EMAIL = 0x20
        const val VARIATION_PERSON_NAME = 0x60
        const val VARIATION_POSTAL_ADDRESS = 0x70
        const val VARIATION_PASSWORD = 0x80
        const val VARIATION_VISIBLE_PASSWORD = 0x90
        const val VARIATION_FILTER = 0xb0
        const val VARIATION_WEB_EMAIL = 0xd0
        const val VARIATION_WEB_PASSWORD = 0xe0

        /** The app shows its own completions (a search box with a drop-down, say). */
        const val FLAG_AUTO_COMPLETE = 0x00010000

        /** The app asks for no suggestions: user names, codes, and many password-like boxes. */
        const val FLAG_NO_SUGGESTIONS = 0x00080000

        /** Private or incognito mode: the app asks keyboards not to remember anything. */
        const val OPTION_NO_PERSONALIZED_LEARNING = 0x01000000

        fun forField(inputType: Int, imeOptions: Int): FieldRules {
            // Numbers, phone numbers, dates, and boxes that say nothing about themselves.
            if (inputType and MASK_CLASS != CLASS_TEXT) return NOTHING
            when (inputType and MASK_VARIATION) {
                VARIATION_PASSWORD, VARIATION_VISIBLE_PASSWORD, VARIATION_WEB_PASSWORD -> return NOTHING
                VARIATION_URI, VARIATION_EMAIL, VARIATION_WEB_EMAIL, VARIATION_FILTER -> return SHORTCUTS_ONLY
            }
            if (inputType and FLAG_NO_SUGGESTIONS != 0) return SHORTCUTS_ONLY
            val names = when (inputType and MASK_VARIATION) {
                VARIATION_PERSON_NAME, VARIATION_POSTAL_ADDRESS -> true
                else -> false
            }
            val ownCompletions = inputType and FLAG_AUTO_COMPLETE != 0
            val private = imeOptions and OPTION_NO_PERSONALIZED_LEARNING != 0
            return FieldRules(
                suggest = true,
                // Names and addresses are full of words no list knows; "fixing" them does harm.
                correct = !names && !ownCompletions,
                learn = !private,
                shortcuts = true,
            )
        }
    }
}
