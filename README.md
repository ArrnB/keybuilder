# KeyBuilder (working name)

An Android keyboard you design yourself. The keyboard is **data**: a layout file says which
keys exist, what they do and how they look, and the app draws whatever the file says.

**Phase 4** adds the shortcut strip and three panels: clipboard history, GIF search and
emoji. Phase 1 was the working keyboard, phase 2 the style editor, phase 3 the layout
editor and several saved keyboards.

## Run it

1. Open this folder in Android Studio (File > Open) and wait for the first sync. It downloads
   Gradle and the libraries, so it needs internet and takes a few minutes.
2. Press Run with a phone or emulator connected (Android 8.0 or newer).
3. In the app: **Open keyboard settings** and switch KeyBuilder on, then **Choose keyboard**.
   Android shows a warning for every third-party keyboard. The app's only permission is
   internet access, used only by the GIF panel.
4. Type in the test box.

If Android Studio offers to upgrade the Android Gradle Plugin, either answer is fine.

## What works

Keyboard
- QWERTY letters, two symbol layers, space, enter, backspace (hold to repeat)
- Shift: tap for one capital, double tap for caps lock, automatic capital at the start of a sentence
- Enter does the right thing per text field (new line, search, send, next)

Shortcut strip and panels
- A strip above the keys with **Clipboard**, **GIF** and **Emoji** buttons; each opens a panel
  where the keys were, and **ABC** brings the keys back
- Clipboard: keeps the last 30 things you copy, text and pictures. Tap to paste, pin to keep,
  remove, clear. Off until you turn it on. Anything a password manager marks as secret is skipped
- GIF: trending GIFs, one-tap searches, and typed search (tap the search box and the keys type
  into it). Tap a GIF to send it; where an app takes text only, a link is typed instead
- Emoji: about 400 emoji in six groups, with a backspace key
- Settings for all three: home screen > **Clipboard and GIFs**. GIF search needs your own free
  GIPHY key, entered there
- A key can open a panel too ("Opens a panel" in the layout editor), and the strip can be hidden

Layout editor (home screen > **Edit keys**)
- Tap a key in the preview to select it, then move it left, right, up or down
- Add a key before or after, copy a key, delete a key
- Change what is written on a key, and what it shows and types with shift
- Choose what a key does: type its letter, type a text of any length (a snippet), shift,
  backspace, enter, space, open a layer, open a panel, or nothing
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
alternates, key pop-ups, vibration, number-field layouts, emoji search and skin tones,
suggestions, autocorrect, swipe typing.

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
    model/ClipHistory.kt         the clipboard history and its rules
    model/Giphy.kt               the addresses asked of GIPHY and how its answers are read
    model/EmojiData.kt           the emoji list
    model/LayoutRepository.kt    the saved keyboards and which one is in use
    engine/KeyboardEngine.kt     the logic: shift, layers, panels, what a tap types
    net/Http.kt                  the only code that goes online
    store/AppSettings.kt         the GIPHY key and the clipboard on/off setting
    store/ClipboardStore.kt      the clipboard history on disk
    ime/KeyBuilderIme.kt         the Android keyboard service
    ime/ClipboardController.kt   watches the phone's clipboard
    ime/GifController.kt         runs GIF searches and downloads
    ui/KeyboardView.kt           draws a keyboard and the strip (shared by the keyboard and the app)
    ui/Panels.kt                 the clipboard, GIF and emoji panels
    ui/LayoutScreen.kt           the layout editor
    ui/StyleScreen.kt            the style editor
    ui/KeyboardsScreen.kt        the list of saved keyboards
    ui/ExtrasScreen.kt           settings for the strip and the panels
    ui/ColorPickerDialog.kt      the colour picker
    ui/Widgets.kt                shared pieces: pills, rows, the text pop-up, auto-save
    ui/MainActivity.kt           home screen, navigation, and all saving
app/src/test/                    tests for the model, the engine and the panel logic
```

`model` (except LayoutRepository), `engine` and `net` contain no Android code, so their tests run on
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
`{ "type": "text", "text": "any text" }`, `{ "type": "layer", "to": "layerId" }` and
`{ "type": "panel", "to": "clipboard" }` (or `"gif"`, `"emoji"`).

**The strip:** `"toolbar": false` at the top level hides the shortcut strip (it is shown by default).

**A row** is a list of keys, or `{ "height": 1.5, "keys": [ ... ] }` for a taller or shorter row.

**Theme fields:** `background`, `keyColor`, `functionKeyColor`, `accentColor`, `keyPressedColor`,
`textColor`, `accentTextColor`, `borderColor` (colours as `#RRGGBB` or `#AARRGGBB`), and
`borderWidthDp`, `cornerRadiusDp`, `keyHeightDp`, `keyGapDp`, `fontSizeSp` (numbers),
`fontFamily` (`sans`, `serif` or `mono`) and `bold` (`true` or `false`).

Shift only applies on layers that contain a shift key.

A mistake in the file produces a message such as
`Layout problem in layer "letters", row 2, key 3: unknown action "shfit"`.

## Setting up GIF search

1. Create a free API key on GIPHY's developer site (choose "API", not "SDK").
2. In the app: **Clipboard and GIFs** > **GIPHY key**, and paste it.
3. Open the keyboard in a chat app and tap **GIF**.

A free key allows 100 searches an hour, which is plenty for your own use. Releasing the app
to many people needs a production key, which GIPHY approves case by case and charges for.
The key is stored on the phone and is not part of the source code, so the project can be
shared without sharing your key. The test box on the home screen takes text only, so there
a GIF arrives as a link; try a chat app to see the GIF itself.

Why GIPHY: it is the largest GIF library and the longest-established provider (Google closed
the Tenor API to outside developers on 30 June 2026). To switch provider later, replace
`model/Giphy.kt`: a search address, a trending address and a function that reads the answer.

## Privacy

- What you type in other apps never leaves the phone.
- The only things sent over the internet are the search words you type **in the GIF panel**
  (to GIPHY, with your key) and the request for the GIF you tap. All of it is in `net/Http.kt`.
- The clipboard history is kept in the app's private storage and is never sent anywhere. It is
  off until you turn it on, and it can be erased from **Clipboard and GIFs**.

## How it was checked

Phases 1 to 3 have been built and run. For phase 4, everything without Android code was
compiled and its 87 tests passed: the engine, the layout and style rules, the clipboard
history rules, the reading of GIPHY's answers (against a sample answer, not the live service),
and the emoji list. The network helper was run against a local test server. The Android parts
(the strip and panels, watching the clipboard, sending pictures to other apps, the settings
screen) were written without access to the Android build tools, and GIF search could not be
tried against GIPHY itself, so **phase 4 has not been built or run yet**. It also adds two
libraries (Coil, for showing pictures and GIFs), so the first sync downloads them. If the
build stops or something misbehaves on the phone, send the error text or a screenshot.

## Roadmap

5. **More special keys:** long-press alternates, arrows, copy and paste, dragging keys with a finger
6. **Panel extras:** emoji search, recently used emoji and skin tones; more GIFs when you scroll
   to the end; stickers
7. **Polish:** vibration, key pop-ups, number-field layouts, landscape, light and dark variants

`targetSdk` is 34 for now. Targeting 35 forces edge-to-edge drawing, which needs extra
handling so the keyboard does not sit under the navigation bar.
