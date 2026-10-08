# KeyBuilder (working name)

An Android keyboard you design yourself. The keyboard is **data**: a layout file says which
keys exist, what they do and how they look, and the app draws whatever the file says.

**Phase 7** makes dragging a panel smooth, adds a sticker panel, and puts "Replace this key"
and "Swap with another" at the top of the key editor so any key can become any other key or
trade places with one.
Earlier phases: 1 the working keyboard, 2 the style editor, 3 the layout editor and several
saved keyboards, 4 the strip with clipboard, GIF and emoji panels, 5 long-press choices,
pop-ups, arrow and copy/paste keys, vibration and number boxes, 6 built-in long-press
choices, space-bar cursor steering, resizable panels, the settings gear and strip symbols.

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
- Long-press choices: hold a key, slide to a choice, lift to type it. Every keyboard has them
  without any setup: 1 to 0 on a top row of letters (shown small in the key's corner), accents
  on letters, related marks on punctuation. Choices you set yourself replace the built-in ones
- Hold the space bar, then slide: left and right move the cursor a character at a time, up and
  down a line at a time
- The pressed letter is shown enlarged above the key
- Arrow keys (they repeat while held) and copy, paste, cut and select-all keys, as key types
- A short vibration when a key is touched
- Number boxes (phone, amount, PIN, date) open the layer you choose
- Two spaces after a word type a full stop

Shortcut strip and panels
- A strip above the keys with **Clipboard**, **GIF**, **Stickers** and **Emoji** buttons and a
  settings gear at the right end; each opens a panel where the keys were, and **ABC** brings
  the keys back. The buttons can show small symbols instead of words, and scroll sideways if
  they do not all fit
- Every panel has a small bar at its top: drag it up to make the panel taller (to see more
  GIFs, stickers, emoji or clipboard items) and down to shrink it; a tap jumps between normal
  and tallest. The taller part lies over the app you are in rather than pushing it up
- Stickers: GIPHY's see-through pictures, with the same trending list, one-tap searches and
  typed search as GIFs, and the same key
- Settings (the gear): switch to another of your saved keyboards, turn vibration, pop-ups,
  double space, strip symbols and clipboard history on or off, open the app, or switch to
  another keyboard app, all without leaving the app you are typing in
- Clipboard: keeps the last 30 things you copy, text and pictures. Tap to paste. **Pin** an item
  and it moves to a Pinned section and stays until you unpin or remove it; Clear and the
  30-item limit never touch pinned items. Off until you turn it on. Anything a password
  manager marks as secret is skipped
- GIF: trending GIFs, one-tap searches, and typed search (tap the search box and the keys type
  into it), with "More GIFs" at the end of the list. Tap a GIF to send it; where an app takes
  text only, a link is typed instead
- Emoji: about 400 emoji in six groups plus the ones you used recently, with a backspace key
- Settings for all three: home screen > **Settings**. GIF search needs your own free
  GIPHY key, entered there
- A key can open a panel too ("Opens a panel" in the layout editor), and the strip can be hidden

Layout editor (home screen > **Edit keys**)
- Tap a key in the preview to select it, then move it left, right, up or down
- Add a key before or after, copy a key, delete a key
- Change what is written on a key, and what it shows and types with shift
- Choose what a key does: type its letter, type a text of any length (a snippet), shift,
  backspace, enter, space, open a layer, open a panel, move the cursor, copy or paste, or nothing
- **Replace this key:** the first button when a key is selected. It opens a picker with every
  key there is: special keys (shift, backspace, enter, space, arrows, copy and paste, panels,
  layers, a blank key, a gap) and a catalogue of letters, numbers, punctuation, brackets, maths,
  money, arrows, Greek, accents and marks. Nothing is checked: a symbol can go where a letter
  was, a letter where the space bar was. The place keeps its width
- **Swap with another:** tap a second key, on this layer or another, and the two trade places
- Adding a key opens the same picker, so you choose what the new key is
- Give a key its own long-press choices, typed or picked from the catalogue
- Key width, and a key's look: normal, function, accent, or an invisible gap
- Rows: height, add, move up and down, delete
- Layers: add, rename, copy, delete, choose which one opens first and which opens in number boxes
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
- Letters: plain, serif or typewriter, bold, and whether keys show their corner hint
- Single keys: tap a key in the preview and give it its own background, letter colour or size
- Reset puts the style back without touching your keys

Everything is saved automatically, and the real keyboard shows changes the next time it opens.

Typing settings (home screen > **Settings**): vibration, the letter pop-up, and the
double-space full stop can each be switched off.

Not in yet: dragging keys with a finger in the editor (keys move with buttons for now),
emoji search and skin tones, word suggestions and autocorrect.

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
    model/SymbolData.kt          the catalogue behind "Pick a letter or symbol"
    model/BuiltInChoices.kt      the long-press choices keys get without any setup
    model/LayoutRepository.kt    the saved keyboards and which one is in use
    engine/KeyboardEngine.kt     the logic: shift, layers, panels, what a tap types
    net/Http.kt                  the only code that goes online
    store/AppSettings.kt         the GIPHY key and the clipboard on/off setting
    store/ClipboardStore.kt      the clipboard history on disk
    ime/KeyBuilderIme.kt         the Android keyboard service
    ime/ClipboardController.kt   watches the phone's clipboard
    ime/GifController.kt         runs GIF searches and downloads
    ui/KeyboardView.kt           draws a keyboard and the strip (shared by the keyboard and the app)
    ui/Panels.kt                 the clipboard, GIF, emoji and settings panels, and their drag handle
    ui/LayoutScreen.kt           the layout editor
    ui/StyleScreen.kt            the style editor
    ui/KeyboardsScreen.kt        the list of saved keyboards
    ui/ExtrasScreen.kt           settings for the strip and the panels
    ui/ColorPickerDialog.kt      the colour picker
    ui/SymbolPickerDialog.kt     the letter and symbol catalogue
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
| `alt` | Long-press choices, for example `["1", "!"]` (up to 8) | none |

**Actions:** `"shift"`, `"backspace"`, `"enter"`, `"space"`, `"none"`,
`{ "type": "text", "text": "any text" }`, `{ "type": "layer", "to": "layerId" }` and
`{ "type": "panel", "to": "clipboard" }` (or `"gif"`, `"emoji"`),
`{ "type": "arrow", "to": "left" }` (or `"right"`, `"up"`, `"down"`) and
`{ "type": "command", "to": "copy" }` (or `"paste"`, `"cut"`, `"selectAll"`).

**The strip:** `"toolbar": false` at the top level hides the shortcut strip (it is shown by default).

**Number boxes:** `"numberLayer": "symbols"` at the top level names the layer that opens in
boxes that only take numbers. Leave it out to always open the start layer.

**A row** is a list of keys, or `{ "height": 1.5, "keys": [ ... ] }` for a taller or shorter row.

**Theme fields:** `background`, `keyColor`, `functionKeyColor`, `accentColor`, `keyPressedColor`,
`textColor`, `accentTextColor`, `borderColor` (colours as `#RRGGBB` or `#AARRGGBB`), and
`borderWidthDp`, `cornerRadiusDp`, `keyHeightDp`, `keyGapDp`, `fontSizeSp` (numbers),
`fontFamily` (`sans`, `serif` or `mono`), `bold` and `hints` (`true` or `false`; hints are
the small long-press characters in key corners).

Shift only applies on layers that contain a shift key.

A mistake in the file produces a message such as
`Layout problem in layer "letters", row 2, key 3: unknown action "shfit"`.

## Setting up GIF search

1. Create a free API key on GIPHY's developer site (choose "API", not "SDK").
2. In the app: **Settings** > **GIPHY key**, and paste it.
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
  off until you turn it on, and it can be erased from **Settings**.

## Adding your own characters

The catalogue in the key editor is the list in `model/SymbolData.kt`, and the built-in
long-press choices are the table in `model/BuiltInChoices.kt`. Both are plain lists: add a
character or a whole group and it appears in the app.

## How it was checked

Phases 1 to 6 have been built and run. For phase 7, everything without Android code was
compiled and its 114 tests passed, including replacing and swapping keys (every special key
in every kind of place, with saving and loading), the sticker addresses, and the arithmetic
that tells Android where a dragged panel's edges are. The Android parts were written without
access to the Android build tools, so **phase 7 has not been built or run yet**.

The smooth drag is the part to watch. Before, the keyboard's window was resized for every
finger movement, which is slow and made the app behind it rearrange itself each time. Now the
window is made as tall as a panel can get once, when a panel opens, and dragging only changes
how much of it is drawn; Android is told where the panel really is so the app underneath
stays put and stays touchable above the panel. That uses a part of Android that could not be
tried here. If, with a panel open, the app above it cannot be touched, jumps upward, or the
panel leaves an empty band, say which and it can be corrected.

## Roadmap

8. **Word suggestions and autocorrect:** under discussion; see the notes from the conversation
9. **Editor extras:** dragging keys with a finger
10. **Panel extras:** emoji search and skin tones
11. **Polish:** key sounds, landscape tuning, light and dark variants of a keyboard

Decided against: sharing keyboards as files, and swipe (glide) typing.

`targetSdk` is 34 for now. Targeting 35 forces edge-to-edge drawing, which needs extra
handling so the keyboard does not sit under the navigation bar.
