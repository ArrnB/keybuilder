# KeyBuilder (working name)

An Android keyboard you design yourself. The keyboard is **data**: a layout file says which
keys exist, what they do and how they look, and the app draws whatever the file says.

This is **phase 1**: a working keyboard loaded from a layout file. There is no editor yet;
you change the keyboard by editing the file.

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

- QWERTY letters, two symbol layers, space, enter, backspace (hold to repeat)
- Shift: tap for one capital, double tap for caps lock, automatic capital at the start of a sentence
- Enter does the right thing per text field (new line, search, send, next)
- Colours, corner radius, key height, gaps and font size all come from the layout file
- The preview on the setup screen is drawn by the same code as the real keyboard

Not in yet: long-press alternates, key pop-ups, vibration, number-field layouts, emoji,
suggestions, autocorrect, swipe typing.

## Where things are

```
app/src/main/
  assets/layouts/qwerty.json     the default keyboard, as data
  java/app/keybuilder/
    model/KeyboardModel.kt       what a keyboard is: layers, rows, keys, theme
    model/LayoutJson.kt          reads and writes layout files, with readable error messages
    model/LayoutRepository.kt    where layouts are loaded from
    engine/KeyboardEngine.kt     the logic: shift, layers, what a tap types
    ui/KeyboardView.kt           draws a keyboard (shared by the keyboard and the app)
    ime/KeyBuilderIme.kt         the Android keyboard service
    ui/MainActivity.kt           setup screen
app/src/test/                    tests for the model and the engine
```

`model` and `engine` contain no Android code, so their tests run on your computer in a
second: right-click `app/src/test` > Run tests, or `./gradlew test`.

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
`borderWidthDp`, `cornerRadiusDp`, `keyHeightDp`, `keyGapDp`, `fontSizeSp` (numbers).

Shift only applies on layers that contain a shift key.

A mistake in the file produces a message such as
`Layout problem in layer "letters", row 2, key 3: unknown action "shfit"`.

## How it was checked

The model, the layout reader and the engine were compiled and their 23 tests passed.
The Android parts (keyboard service, drawing, setup screen) were written without access to
the Android build tools, so **the full app has not been built or run on a device yet**. Expect
that the first build may need a small fix; send the error text if it does.

## Next phases

2. Styling screen: edit the theme and per-key styles with a live preview
3. Layout editor: add, delete, move and resize keys and rows; extra layers; saved layouts
4. Special keys: text snippets, long-press alternates, emoji, arrows, copy and paste
5. Polish: vibration, key pop-ups, landscape, light and dark variants

`targetSdk` is 34 for now. Targeting 35 forces edge-to-edge drawing, which needs extra
handling so the keyboard does not sit under the navigation bar.
