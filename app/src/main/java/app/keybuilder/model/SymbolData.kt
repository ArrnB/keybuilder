package app.keybuilder.model

/*
 * The catalogue shown by "Pick a symbol" in the key editor: characters you can put on a key
 * or among its long-press choices without having to find a way to type them first.
 * A plain list: add your own characters or whole groups freely.
 */

data class SymbolGroup(val name: String, val symbols: List<String>)

object SymbolData {
    val groups: List<SymbolGroup> = listOf(
        SymbolGroup("Letters", "a b c d e f g h i j k l m n o p q r s t u v w x y z".split(" ")),
        SymbolGroup("Numbers", "1 2 3 4 5 6 7 8 9 0 ½ ⅓ ¼ ¾ ⅔ ¹ ² ³ ⁰ №".split(" ")),
        SymbolGroup("Punctuation", ". , ? ! : ; ' \" … · • - – — _ ¿ ¡ ‘ ’ “ ” „ « » @ # & * / \\ | ~ ^ ` § ¶ † ‡".split(" ")),
        SymbolGroup("Brackets", "( ) [ ] { } < > ‹ ›".split(" ")),
        SymbolGroup("Maths", "+ − × ÷ = ≠ ≈ ± ≤ ≥ % ‰ √ ∞ π ∑ ∆ ∫ ° µ".split(" ")),
        SymbolGroup("Money", "₹ $ € £ ¥ ¢ ₩ ₽ ₺ ₪ ฿ ₫ ₦ ₱ ₿".split(" ")),
        SymbolGroup("Arrows", "← → ↑ ↓ ↔ ↕ ⇐ ⇒ ⇔ ↩ ↪".split(" ")),
        SymbolGroup("Greek", "α β γ δ ε θ λ μ σ φ ω Γ Δ Θ Λ Σ Φ Ψ Ω".split(" ")),
        SymbolGroup("Accents", "à á â ä æ ã å ç è é ê ë ì í î ï ñ ò ó ô ö õ ø œ ß ù ú û ü ÿ".split(" ")),
        SymbolGroup("Marks", "© ® ™ ✓ ✗ ★ ☆ ♥ ♦ ♣ ♠ ♪ ♫ ● ○ ■ □ ▲ ▼ ◆ ◇".split(" ")),
    )

    /** Faces made of ordinary characters. Each is typed whole. */
    val faces: SymbolGroup = SymbolGroup(
        "Faces",
        listOf(
            "¯\\_(ツ)_/¯", "(╯°□°)╯︵ ┻━┻", "┬─┬ノ( º _ ºノ)", "( ͡° ͜ʖ ͡°)", "ಠ_ಠ", "(ง'̀-'́)ง", "(づ｡◕‿‿◕｡)づ", "ʕ•ᴥ•ʔ",
            "(•_•)", "( •_•)>⌐■-■", "(⌐■_■)", "(¬‿¬)", "(ಥ﹏ಥ)", "(｡♥‿♥｡)", "\\(^o^)/", "(^_^)", "(>_<)", "(-_-)",
            "(o_O)", "(T_T)", "(^_^;)", "(*^▽^*)", "(◕‿◕)", "(ʘ‿ʘ)", "ヽ(´▽`)/", "(☞ﾟヮﾟ)☞", "☜(ﾟヮﾟ☜)", "(ノ◕ヮ◕)ノ*:･ﾟ✧",
            "♪┏(・o･)┛♪", "(´･ω･`)", "(=^･ω･^=)", "(；一_一)", "<3", ":)", ":(", ":D", ";)", ":P", ":/", ":'(", "XD", "^_^",
        ),
    )

    /**
     * What the symbols panel on the keyboard shows. Plain letters are left out (the keys
     * are right there), and the faces are added.
     */
    val panelGroups: List<SymbolGroup> = groups.filter { it.name != "Letters" } + faces
}
