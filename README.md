# KeyBuilder (working name)

An Android keyboard you design yourself. The keyboard is **data**: a layout file says which
keys exist, what they do and how they look, and the app draws whatever the file says.

**Phase 3** adds the layout editor and several saved keyboards. Phase 1 was the working
keyboard, phase 2 the style editor.

## Run it

1. Open this folder in Android Studio (File > Open) and wait for the first sync. It downloads
   Gradle and the libraries, so it needs internet and takes a few minutes.
2. Press Run with a phone or emulator connected (Android 8.0 or newer).
3. In the app: **Open keyboard settings** and switch KeyBuilder on, then **Choose keyboard**.
   Android shows a warning for every third-party keyboard. This app asks for no permissions
   and has no internet access.
4. Type in the test box.

If Android Studio offers to upgrade the Android Gradle Plugin, either answer is fine.

## What works

Keyboard
- QWERTY letters, two symbol layers, space, enter, backspace (hold to repeat)
- Shift: tap for one capital, double tap for caps lock, automatic capital at the start of a sentence
- Enter does the right thing per text field (new line, search, send, next)

Layout editor (home screen > **Edit keys**)
- Tap a key in the preview to select it, then move it left, right, up or down
- Add a key before or after, copy a key, delete a key
- Change what is written on a key, and what it shows and types with shift
- Choose what a key does: type its letter, type a text of any length (a snippet), shift,
  backspace, enter, space, open a layer, or nothing
- Key width, and a key's look: normal, function, accent, or an invisible gap
- Rows: height, add, move up and down, delete
- Layers: add, rename, copy, delete, and choose which one opens first
- Undo, up to 50 steps; dragging a slider counts as one step
- Warnings when a layer cannot be reached or left, a labelled key does nothing, or there is no backspace

Several keyboards (home screen > **My keyboards**)
- Keep as many keyboards as you like; one is "in use"
- New (a copy of the built-in QWERTY), copy, rename, delete
- The Keys and Style screens always edit the keyboard that is in use

Style editor (home screen > **Edit style**)
- Eight ready-made looks; choosing one changes colours and key shape but keeps your sizes
- Every colour, with a picker: sliders, quick choices, or type a colour code
- Corner roundness, border thickness, key height, gap, letter size
- Letters: plain, serif or typewriter, and bold
- Single keys: tap a key in the preview and give it its own background, letter colour or size
- Reset puts the style back without touching your keys

Everything is saved automatically, and the real keyboard shows changes the next time it opens.

Not in yet: dragging keys with a finger (keys move with buttons for now), long-press
alternates, key pop-ups, vibration, number-field layouts, emoji, clipboard, GIFs, suggestions,
autocorrect, swipe typing.

## Where things are

```
app/src/main/
  assets/layouts/qwerty.json     the built-in keyboard, as data
  java/app/keybuilder/
    model/KeyboardModel.kt       what a keyboard is: layers, rows, keys, theme
    model/LayoutJson.kt          reads and writes layout files, with readable error messages
    model/LayoutEdits.kt         every change the layout editor can make, undo, warnings
    model/StyleEdits.kt          every change the style editor can make, plus the ready-made looks
    model/ColorMath.kt           colour arithmetic for the colour picker
    model/LayoutRepository.kt    the saved keyboards and which one is in use
    engine/KeyboardEngine.kt     the logic: shift, layers, what a tap types
    ui/KeyboardView.kt           draws a keyboard (shared by the keyboard and the app)
    ui/LayoutScreen.kt           the layout editor
    ui/StyleScreen.kt            the style editor
    ui/KeyboardsScreen.kt        the list of saved keyboards
    ui/ColorPickerDialog.kt      the colour picker
    ui/Widgets.kt                shared pieces: pills, rows, the text pop-up, auto-save
    ui/MainActivity.kt           home screen, navigation, and all saving
    ime/KeyBuilderIme.kt         the Android keyboard service
app/src/test/                    tests for the model and the engine
```

`model` (except LayoutRepository) and `engine` contain no Android code, so their tests run on
your computer in a second: right-click `app/src/test` > Run tests, or `./gradlew test`.

The screens hold no data of their own. `MainActivity` holds the keyboards, each screen shows
one and hands back a changed copy, and the rules for every change live in `LayoutEdits.kt`
and `StyleEdits.kt` where they are tested. Because every edit makes a whole new copy, undo is
just "go back to the previous copy".

**Where your keyboards are saved:** one file per keyboard in a `keyboards` folder in the app's
private storage, plus a setting for which one is in use. The keyboard saved by phase 2 is moved
there automatically the first time this version runs. `qwerty.json` is only the starting point
for new keyboards.

## The layout file

Change `qwerty.json`, run the app again, and the keyboard changes.

```json
{
  "format": 1,
  "id": "qwerty",
  "name": "QWERTY",
  "startLayer": "letters",
  "theme": { "keyColor": "#343842", "cornerRadiusDp": 8 },
  "layers": [
    { "id": "letters", "rows": [
      ["q", "w", "e"],
      [ { "action": "shift", "width": 1.5, "look": "function" }, "z",
        { "label": "?123", "action": { "type": "layer", "to": "symbols" } } ]
    ] }
  ]
}
```

**A key** is either plain text (`"q"`, which types q) or an object with any of:

| Field | Meaning | Default |
|---|---|---|
| `label` | Text shown on the key | empty (shift, backspace and enter then get an icon) |
| `shiftLabel` | Text used while shift is on | the label in capitals |
| `action` | What a tap does, see below | type the label |
| `width` | Relative width within the row | 1 |
| `look` | `normal`, `function`, `accent` or `spacer` (an invisible gap) | `normal` |
| `style` | Per-key `background`, `textColor`, `fontSizeSp` | theme values |

**Actions:** `"shift"`, `"backspace"`, `"enter"`, `"space"`, `"none"`,
`{ "type": "text", "text": "any text" }` and `{ "type": "layer", "to": "layerId" }`.

**A row** is a list of keys, or `{ "height": 1.5, "keys": [ ... ] }` for a taller or shorter row.

**Theme fields:** `background`, `keyColor`, `functionKeyColor`, `accentColor`, `keyPressedColor`,
`textColor`, `accentTextColor`, `borderColor` (colours as `#RRGGBB` or `#AARRGGBB`), and
`borderWidthDp`, `cornerRadiusDp`, `keyHeightDp`, `keyGapDp`, `fontSizeSp` (numbers),
`fontFamily` (`sans`, `serif` or `mono`) and `bold` (`true` or `false`).

Shift only applies on layers that contain a shift key.

A mistake in the file produces a message such as
`Layout problem in layer "letters", row 2, key 3: unknown action "shfit"`.

## How it was checked

Phases 1 and 2 have been built and run. For phase 3, everything without Android code (the
layout reader, the engine, and the rules for layout edits, style edits, undo and warnings) was
compiled and its 72 tests passed. The new screens (layout editor, keyboards list), the storage
for several keyboards and the changes to the keyboard service were written without access to
the Android build tools, so **phase 3 has not been built or run yet**. If the build stops,
send the error text.

## Roadmap

4. **Special keys:** long-press alternates, arrows, copy and paste, dragging keys with a finger,
   and a "panel" key type that opens emoji, clipboard or GIFs, so those can sit wherever you like.
   (Text snippets arrived early: they are the "Types a text" choice in the layout editor.)
5. **Emoji panel:** categories, recents, skin tones. AndroidX ships a ready-made picker
   (`androidx.emoji2:emoji2-emojipicker`) that can be embedded, which makes this the quickest of the three
6. **Clipboard panel**
   - Text: keep a history of what was copied, with pinned items. Android only lets the current
     keyboard (or the app on screen) read the clipboard, so this works while KeyBuilder is selected.
     Entries marked sensitive (passwords) should be skipped
   - Images: copy the picture into the app's own storage when it is copied, then paste it with
     Android's "commit content" feature. That only works in apps that accept images in the text field
7. **GIF panel:** search and send GIFs, using **GIPHY**. Sending uses the same "commit content"
   path as clipboard images, so it comes after them.
   - Why GIPHY: it is the largest GIF library and the longest-established provider. Google closed
     the Tenor API to outside developers on 30 June 2026. KLIPY, the main alternative, says it
     has 10M+ items, is newer, and can show ads inside search results
   - Cost: a GIPHY beta key is free and allows 100 searches an hour, enough to build and use the
     feature yourself. Releasing the app to many people needs a production key, which GIPHY
     approves case by case and charges for
   - Reviews are mixed for both: developers find GIPHY's search weaker than Tenor's was for
     unusual searches and dislike the paid production key; KLIPY's search is liked but its
     pricing at scale is unclear. The code will keep the provider behind one small interface so
     it can be swapped
   - Rules both providers set: requests must come from the phone itself, GIFs may not be stored
     and re-served, and the panel must show the provider's name
   - Still to decide: GIF search needs internet access, which the app does not have today. Either
     the app gains the permission, or GIFs live in a separate optional add-on so the keyboard
     itself stays offline
8. **Polish:** vibration, key pop-ups, landscape, light and dark variants

`targetSdk` is 34 for now. Targeting 35 forces edge-to-edge drawing, which needs extra
handling so the keyboard does not sit under the navigation bar.
