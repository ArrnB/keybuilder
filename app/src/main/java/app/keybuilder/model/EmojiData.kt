package app.keybuilder.model

/*
 * The emoji offered by the emoji panel, by group. A plain list: add, remove or reorder freely.
 * Some entries carry an invisible mark (U+FE0F) that tells the phone to draw the coloured
 * emoji rather than a black-and-white symbol, so copy entries whole when editing.
 */

data class EmojiCategory(val name: String, val emoji: List<String>)

/** Puts [emoji] at the front of the recently used list, without repeats, keeping at most [limit]. */
fun addRecentEmoji(recent: List<String>, emoji: String, limit: Int = 32): List<String> =
    (listOf(emoji) + recent.filter { it != emoji }).take(limit)

object EmojiData {
    val categories: List<EmojiCategory> = listOf(
        EmojiCategory("Smileys", "😀 😃 😄 😁 😆 😅 😂 🤣 😊 😇 🙂 🙃 😉 😌 😍 🥰 😘 😗 😙 😚 😋 😛 😝 😜 🤪 🤨 🧐 🤓 😎 🤩 🥳 😏 😒 😞 😔 😟 😕 🙁 😣 😖 😫 😩 🥺 😢 😭 😤 😠 😡 🤬 🤯 😳 🥵 🥶 😱 😨 😰 😥 😓 🤗 🤔 🤭 🤫 🤥 😶 😐 😑 😬 🙄 😯 😦 😧 😮 😲 🥱 😴 🤤 😪 😵 🤐 🥴 🤢 🤮 🤧 😷 🤒 🤕 🤑 🤠 😈 👿 👻 💀 👽 🤖 💩".split(" ")),
        EmojiCategory("People", "👍 👎 👌 ✌️ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ ✋ 🤚 🖐️ 🖖 👋 🤝 🙏 👏 🙌 👐 🤲 💪 ✍️ 🤳 💅 👀 👂 👃 🧠 👶 🧒 👦 👧 🧑 👨 👩 🧓 👴 👵 🙅 🙆 💁 🙋 🙇 🤦 🤷 🚶 🏃 💃 🕺".split(" ")),
        EmojiCategory("Hearts", "❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 💟 💯 💢 💥 💫 💦 💨 💤 ✨ ⭐ 🌟 🔥 ✅ ❌ ❓ ❗ ⚠️ 🚫 ♻️ ➕ ➖ ➗ ✖️ 💲 🔴 🟠 🟡 🟢 🔵 🟣 ⚫ ⚪".split(" ")),
        EmojiCategory("Nature", "🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🙈 🙉 🙊 🐔 🐧 🐦 🐤 🦆 🦅 🦉 🐴 🦄 🐝 🦋 🐌 🐞 🐢 🐍 🐙 🐠 🐬 🐳 🐘 🐪 🐕 🐈 🌵 🎄 🌲 🌳 🌴 🌱 🌿 🍀 🍁 🍂 🌹 🌺 🌸 🌼 🌻 🌞 🌝 🌚 🌙 ☀️ ⛅ ☁️ 🌧️ ❄️ ☃️ 🌈 🌊".split(" ")),
        EmojiCategory("Food", "🍏 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🍒 🍑 🥭 🍍 🥥 🥝 🍅 🥑 🌽 🥕 🥔 🍞 🧀 🥚 🍳 🥞 🍗 🍖 🌭 🍔 🍟 🍕 🥪 🌮 🌯 🍝 🍜 🍲 🍛 🍣 🍱 🍚 🍙 🍦 🍰 🎂 🍮 🍭 🍬 🍫 🍿 🍩 🍪 🥛 ☕ 🍵 🥤 🍺 🍻 🥂 🍷 🍸 🍹".split(" ")),
        EmojiCategory("Things", "⚽ 🏀 🏈 ⚾ 🎾 🏐 🏏 🏓 🏸 🥊 🎯 🎮 🎲 🎸 🎹 🎤 🎧 🎬 🎨 🏆 🥇 🎉 🎊 🎁 🎈 🚗 🚕 🚌 🚲 🏍️ ✈️ 🚀 🚂 🚢 ⛵ 🏠 🏢 🏥 🏫 ⛰️ 🏖️ 🗺️ ⌚ 📱 💻 ⌨️ 🖨️ 📷 📺 💡 🔋 🔌 💰 💳 ✉️ 📦 📅 📌 📎 ✂️ 🔒 🔑 🔨 💊 🧹 🛒 📚 ✏️ 📝 🔍 ⏰ 🔔 🎵".split(" ")),
    )
}
