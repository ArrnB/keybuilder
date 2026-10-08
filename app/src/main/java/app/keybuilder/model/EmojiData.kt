package app.keybuilder.model

/*
 * The emoji offered by the emoji panel, by group. A plain list: add, remove or reorder freely.
 * Some entries carry an invisible mark (U+FE0F) that tells the phone to draw the coloured
 * emoji rather than a black-and-white symbol, so copy entries whole when editing.
 */

data class EmojiCategory(val name: String, val emoji: List<String>)

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
