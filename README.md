# KeyBuilder (working name)

An Android keyboard you design yourself. The keyboard is **data**: a layout file says which
keys exist, what they do and how they look, and the app draws whatever the file says.

**Phase 2** adds a style editor inside the app. Phase 1 was the working keyboard.

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

Style editor (home screen > **Change the style**)
- Eight ready-made looks; choosing one changes colours and key shape but keeps your sizes
- Every colour, with a picker: sliders, quick choices, or type a colour code
- Corner roundness, border thickness, key height, gap, letter size
- Letters: plain, serif or typewriter, and bold
- Single keys: tap a key in the preview and give it its own background, letter colour or size
- A live preview at the bottom of the screen, drawn by the same code as the real keyboard
- Saved automatically; the real keyboard shows the change the next time it opens
- Reset puts the style back without touching your keys

Not in yet: changing which keys exist (phase 3), long-press alternates, key pop-ups,
vibration, number-field layouts, emoji, clipboard, GIFs, suggestions, autocorrect, swipe typing.

## Where things are

```
app/src/main/
  assets/layouts/qwerty.json     the built-in keyboard, as data
  java/app/keybuilder/
    model/KeyboardModel.kt       what a keyboard is: layers, rows, keys, theme
    model/LayoutJson.kt          reads and writes layout files, with readable error messages
    model/StyleEdits.kt          every change the style editor can make, plus the ready-made looks
    model/ColorMath.kt           colour arithmetic for the colour picker
    model/LayoutRepository.kt    loads and saves the active keyboard
    engine/KeyboardEngine.kt     the logic: shift, layers, what a tap types
    ui/KeyboardView.kt           draws a keyboard (shared by the keyboard and the app)
    ui/StyleScreen.kt            the style editor
    ui/ColorPickerDialog.kt      the colour picker
    ui/Widgets.kt                small shared pieces: pills, colour rows, slider rows
    ui/MainActivity.kt           home screen and navigation
    ime/KeyBuilderIme.kt         the Android keyboard service
app/src/test/                    tests for the model and the engine
```

`model` (except LayoutRepository) and `engine` contain no Android code, so their tests run on
your computer in a second: right-click `app/src/test` > Run tests, or `./gradlew test`.

The screens hold no data of their own. `MainActivity` holds the one keyboard being edited,
each screen shows it and hands back a changed copy, and the rules for every change live in
`StyleEdits.kt` where they are tested.

**Where your changes are saved:** the first change writes `active_layout.json` in the app's
private storage. From then on that file is the keyboard, and `qwerty.json` is only the starting
point. To start over completely, clear the app's storage (or use Reset for just the style).

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

Phase 1 has been built and run. For phase 2, the model, the layout reader, the engine,
the style rules and the colour arithmetic were compiled and their 42 tests passed. The new
screens (style editor, colour picker) and the changes to the keyboard service were written
without access to the Android build tools, so **phase 2 has not been built or run yet**.
If the build stops, send the error text.

## Roadmap

3. **Layout editor:** add, delete, move and resize keys and rows; extra layers; several saved keyboards
4. **Special keys:** text snippets, long-press alternates, arrows, copy and paste, and a
   "panel" key type that opens emoji, clipboard or GIFs, so those can sit wherever you like
5. **Emoji panel:** categories, recents, skin tones. AndroidX ships a ready-made picker
   (`androidx.emoji2:emoji2-emojipicker`) that can be embedded, which makes this the quickest of the three
6. **Clipboard panel**
   - Text: keep a history of what was copied, with pinned items. Android only lets the current
     keyboard (or the app on screen) read the clipboard, so this works while KeyBuilder is selected.
     Entries marked sensitive (passwords) should be skipped
   - Images: copy the picture into the app's own storage when it is copied, then paste it with
     Android's "commit content" feature. That only works in apps that accept images in the text field
7. **GIF panel:** search and send GIFs. Sending uses the same "commit content" path as clipboard
   images, so it comes after them. Two things to decide first:
   - It needs internet access, which the app does not have today. Either the app gains the
     permission, or GIFs live in a separate optional add-on so the keyboard itself stays offline
   - It needs a GIF provider and an API key. Google closed the Tenor API to outside developers in
     2026; GIPHY and KLIPY are the usual alternatives
8. **Polish:** vibration, key pop-ups, landscape, light and dark variants

`targetSdk` is 34 for now. Targeting 35 forces edge-to-edge drawing, which needs extra
handling so the keyboard does not sit under the navigation bar.
