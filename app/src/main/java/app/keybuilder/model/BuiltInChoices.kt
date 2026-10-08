package app.keybuilder.model

/*
 * The long-press choices a key gets when you have not given it any of your own:
 * accented versions of letters and close relatives of punctuation marks.
 * A plain table: add, remove or reorder freely. The first entries are the ones offered
 * (a key shows at most MAX_ALTERNATES).
 */
object BuiltInChoices {

    private val table: Map<String, List<String>> = mapOf(
        "a" to "à á â ä æ ã å ā",
        "c" to "ç ć č",
        "d" to "ð",
        "e" to "è é ê ë ē ė ę",
        "g" to "ğ",
        "i" to "î ï í ī į ì",
        "l" to "ł",
        "n" to "ñ ń",
        "o" to "ô ö ò ó œ ø ō õ",
        "s" to "ß ś š",
        "t" to "þ",
        "u" to "û ü ù ú ū",
        "y" to "ÿ ý",
        "z" to "ž ź ż",
        "." to "? ! , : ; ' …",
        "," to "; ' \" ( )",
        "?" to "¿ !",
        "!" to "¡ ?",
        "'" to "\" ‘ ’ `",
        "\"" to "“ ” „ « »",
        "-" to "_ – — ~",
        "/" to "\\ |",
        "(" to "[ { <",
        ")" to "] } >",
        "+" to "±",
        "=" to "≠ ≈",
        "<" to "≤ « ‹",
        ">" to "≥ » ›",
        "*" to "× ★ † •",
        "%" to "‰",
        "&" to "§",
        "#" to "№",
        "₹" to "$ € £ ¥ ¢",
        "$" to "₹ € £ ¥ ¢",
        "0" to "° ⁰",
        "1" to "¹ ½ ⅓ ¼",
        "2" to "² ⅔",
        "3" to "³ ¾",
    ).mapValues { it.value.split(" ") }

    /** The built-in choices for a key with this label. Capital letters get the same as small ones. */
    fun forLabel(label: String): List<String> = table[label] ?: table[label.lowercase()] ?: emptyList()

    /** Every label that has built-in choices (used by tests). */
    val labels: Set<String> get() = table.keys
}
