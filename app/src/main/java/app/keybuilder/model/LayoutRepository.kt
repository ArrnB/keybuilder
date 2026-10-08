package app.keybuilder.model

import android.content.Context

/**
 * Where layouts come from. For now there is one, shipped inside the app.
 * When the editor arrives (phase 3) this is where saved layouts will be read and written.
 */
object LayoutRepository {

    private const val DEFAULT_LAYOUT = "layouts/qwerty.json"

    fun loadDefault(context: Context): KeyboardDef =
        context.assets.open(DEFAULT_LAYOUT).bufferedReader().use { LayoutJson.parse(it.readText()) }
}
