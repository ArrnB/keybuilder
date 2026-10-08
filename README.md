# KeyBuilder (working name)

An Android keyboard you design yourself. The keyboard is **data**: a layout file says which
keys exist, what they do and how they look, and the app draws whatever the file says.

**Phase 9** makes panels solid again on a see-through keyboard, keeps the GIF library on
screen while you type a search, adds a frosted-glass blur, a vibration strength slider and
screenshots in the clipboard, resets a dragged panel when it closes, and lets the keyboard
learn your own corrections.
Earlier phases: 8 suggestions and autocorrect for English and Hinglish, the last-copied
button, see-through and gradient backgrounds and phone colours; 1 the working keyboard, 2 the style editor, 3 the layout editor and several
saved keyboards, 4 the strip with clipboard, GIF and emoji panels, 5 long-press choices,
pop-ups, arrow and copy/paste keys, vibration and number boxes, 6 built-in long-press
choices, space-bar cursor steering, resizable panels, the settings gear and strip symbols,
7 smooth panel dragging, stickers, and replacing or swapping any key.

## Run it

1. Open this folder in Android Studio (File > Open) and wait for the first sync. It downloads
   Gradle and the libraries, so it needs internet and takes a few minutes.
2. Press Run with a phone or emulator connected (Android 8.0 or newer).
3. In the app: **Open keyboard settings** and switch KeyBuilder on, then **Choose keyboard**.
   Android shows a warning for every third-party keyboard. The app uses internet access
   (only for the GIF and sticker panels) and vibration. It asks to see your photos only if
   you switch on "Add new screenshots to the clipboard".
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
- A short vibration when a key is touched, at a strength you set with a slider
- Number boxes (phone, amount, PIN, date) open the layer you choose
- Two spaces after a word type a full stop

Suggestions and autocorrect (English and Hinglish)
- While you type, the strip offers up to three words: completions of what you have typed and
  words it could be a slip for. Tap one to use it; a space is added for you
- After a word, the strip guesses the next one from what you usually type after it
- When you finish a word with a space or punctuation, an obvious slip is fixed by itself:
  `teh` becomes `the`, `dont` becomes `don't`, `recieve` becomes `receive`, a lone `i` becomes `I`.
  The word that will be put in is shown in bold beforehand, with what you typed in quotes
  beside it: tap the quoted word to keep your own spelling
- **Backspace straight after a fix puts your own word back**, and the keyboard never touches
  that word again
- Hinglish is suggested but never auto-changed. It has no single correct spelling (`nahi`,
  `nhi`, `nahin`), so "fixing" it would mostly break it. Inside a Hinglish sentence nothing is
  auto-changed at all
- Also left alone: names and anything else with a capital in mid-sentence, chat shorthand
  (`idk`, `hbd`), stretched words (`sooo`, `hahaha`), words with ordinary endings, and
  anything inside an e-mail address or a link
- **Learning:** words you type twice become your own: they are suggested, their slips are
  fixed towards them, and they are never corrected. It also learns which word you type after
  which, and how you capitalise names. All of it is one file on the phone
- **Your own corrections:** type `tmrw`, tap `tomorrow` in the suggestions, and the keyboard
  remembers. The first time it puts `tomorrow` first for `tmrw`; from the second time it makes
  the change for you. This works for Hinglish too (`nhi` to `nahi`, if that is what you pick),
  and backspace straight after undoes it and drops the habit
- **Which language you mostly type:** once most of the words it has learned from you are
  Hinglish, an unknown word at the very start of a sentence is taken to be Hinglish and left
  alone instead of being "fixed" into English
- **Never in password boxes.** There, and in boxes for e-mail addresses, links and numbers, in
  boxes that ask for no suggestions, the keyboard does not read the text at all. In a
  browser's private mode it suggests but learns nothing
- Each of the three (suggestions, automatic fixing, learning) has its own switch, and
  **Forget learned words** erases everything it picked up

Shortcut strip and panels
- **The last thing you copied** sits on the strip next to the gear. Tap it to paste; hold it to
  hide it until you copy something else. It works with the clipboard history off (a copied
  picture needs the history on), and a password copied from a password manager shows as dots
- While suggestions are showing they take the place of the panel buttons; the small arrow at
  the left brings the buttons back, and the last-copied button shrinks to a symbol
- A strip above the keys with **Clipboard**, **GIF**, **Stickers** and **Emoji** buttons and a
  settings gear at the right end; each opens a panel where the keys were, and **ABC** brings
  the keys back. The buttons can show small symbols instead of words, and scroll sideways if
  they do not all fit
- Every panel has a small bar at its top: drag it up to make the panel taller (to see more
  GIFs, stickers, emoji or clipboard items) and down to shrink it; a tap jumps between normal
  and tallest. The taller part lies over the app you are in rather than pushing it up. A
  panel goes back to its normal height whenever it closes or the keyboard is put away
- Panels are always solid, even when the keyboard itself is see-through, so their contents
  never run together with the app behind them
- Stickers: GIPHY's see-through pictures, with the same trending list, one-tap searches and
  typed search as GIFs, and the same key
- Settings (the gear): switch to another of your saved keyboards, turn vibration, pop-ups,
  double space, strip symbols and clipboard history on or off, open the app, or switch to
  another keyboard app, all without leaving the app you are typing in
- Clipboard: keeps the last 30 things you copy, text and pictures. Tap to paste. **Pin** an item
  and it moves to a Pinned section and stays until you unpin or remove it; Clear and the
  30-item limit never touch pinned items. Off until you turn it on. Anything a password
  manager marks as secret is skipped
- **Screenshots in the clipboard:** switch on "Add new screenshots to the clipboard" in
  Settings and every screenshot you take from then on appears in the clipboard panel and on
  the last-copied button. Android does not put screenshots on the clipboard itself, so the
  keyboard fetches them from the gallery, which is why Android asks you to let it see your
  photos (choose "Allow all")
- GIF: trending GIFs, one-tap searches, and typed search, with "More GIFs" at the end of the
  list. Tap the search box and the keys come up **over the lower part of the library**, which
  stays on screen above them (it grows taller if it has to). Search, or Cancel, and the keys
  step aside again. Stickers work the same way. Tap a GIF to send it; where an app takes text
  only, a link is typed instead
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
- Twelve ready-made looks, among them **Dusk** and **Aurora** (gradients), **Glass**
  (see-through) and **Frost** (see-through and blurred). Choosing one changes colours, background and key shape but keeps your sizes
- Every colour, with a picker: sliders, quick choices, or type a colour code
- **Background opacity** and **key opacity**, each from 0 to 100%. Below 100% the keyboard is
  see-through and whatever the app has behind it shows through and tints it
- **Blur what is behind:** with the background below 100%, a blur turns the keyboard into
  frosted glass: what is behind it on the screen becomes soft patches of its own colours.
  This needs Android 12 or later and a phone that allows one window to blur another; the
  style screen says whether yours does. Without it the keyboard is simply see-through
- **Gradient:** a second background colour, fading top to bottom, side to side or corner to
  corner. The panels use the same background as the keys
- **Match the phone:** the keyboard takes its colours from the phone instead of from you. On
  Android 12 and later these are the colours Android makes from your wallpaper; on any
  version it switches between light and dark with the phone. Your own colours are kept for
  switching back
- Corner roundness, border thickness, key height, gap, letter size
- Letters: plain, serif or typewriter, bold, and whether keys show their corner hint
- Single keys: tap a key in the preview and give it its own background, letter colour or size
- Reset puts the style back without touching your keys

Everything is saved automatically, and the real keyboard shows changes the next time it opens.

Typing settings (home screen > **Settings**): vibration, the letter pop-up, the double-space
full stop, suggestions, automatic fixing, learning and the last-copied button can each be
switched off. Most of them are also behind the gear on the keyboard itself.

Not in yet: dragging keys with a finger in the editor (keys move with buttons for now),
emoji search and skin tones.

### A keyboard that takes on what is behind it

Android does not let a keyboard read the screen of the app it is typing into, so it cannot
pick out an app's colours. What it can do is let the screen show through itself:

1. **Opacity below 100%** makes the keyboard see-through, so it is tinted by whatever is
   behind it.
2. **Blur what is behind** on top of that gives frosted glass: instead of sharp shapes you
   see soft patches of the colours behind the keyboard. Android does the blurring; the
   keyboard only asks for it, and not every phone agrees (see the style screen).
3. **Match the phone** is a different thing: fixed colours taken from the wallpaper palette
   and light or dark mode.

One thing to expect: most apps shrink themselves to end where the keyboard begins, so all
that is behind the keyboard is the app's plain background, and the effect is a flat tint.
It is at its best over a home screen, a photo, a map, or any app that draws to the bottom
edge of the screen.

### How good is the autocorrect?

It was measured rather than guessed. Against a list of 32,000 common English misspellings it
fixes 58% correctly by itself, changes 1% into the wrong word, and leaves the rest alone; for
95% the right word is among the three suggestions. Of the 28,000 commonest English words it
wrongly changes under 0.2%. Its weak spot is names and rare words typed without a capital that
no list knows: about 6% of those get changed, which is what backspace-to-undo and learning are
for. The settings were chosen to keep wrong changes low at the cost of fixing fewer slips.

Nothing was measured for Hinglish, because there is no list of Hinglish misspellings to
measure against; that is also why Hinglish is only ever suggested.

Slips are judged by the usual QWERTY arrangement (a neighbouring key counts as a likely
slip). On a keyboard you have rearranged heavily, that part is less accurate.

## Where things are

```
app/src/main/
  assets/layouts/qwerty.json     the built-in keyboard, as data
  assets/words/words.txt         the word list: 51,000 words, each with a score and a language
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
    suggest/SuggestionEngine.kt  suggestions, autocorrect and learning: every rule is here
    suggest/Typo.kt              how likely one word is a slip for another
    suggest/Lexicon.kt           the built-in word list in memory
    suggest/UserWords.kt         what the keyboard has learned from you, and its file format
    suggest/FieldRules.kt        which text boxes allow what (the password-box rule)
    net/Http.kt                  the only code that goes online
    store/AppSettings.kt         the GIPHY key and the on/off settings
    store/ClipboardStore.kt      the clipboard history on disk
    store/WordStore.kt           loads the word list, saves the learned words
    ime/KeyBuilderIme.kt         the Android keyboard service
    ime/SuggestController.kt     reads the words before the cursor and types fixes and suggestions
    ime/ClipboardController.kt   watches the phone's clipboard
    ime/GifController.kt         runs GIF searches and downloads
    ui/KeyboardView.kt           draws a keyboard and the strip (shared by the keyboard and the app)
    ui/PhoneColors.kt            reads the phone's colours, paints gradient backgrounds
    ui/Panels.kt                 the clipboard, GIF, emoji and settings panels, and their drag handle
    ui/LayoutScreen.kt           the layout editor
    ui/StyleScreen.kt            the style editor
    ui/KeyboardsScreen.kt        the list of saved keyboards
    ui/ExtrasScreen.kt           settings for the strip and the panels
    ui/ColorPickerDialog.kt      the colour picker
    ui/SymbolPickerDialog.kt     the letter and symbol catalogue
    ui/Widgets.kt                shared pieces: pills, rows, the text pop-up, auto-save
    ui/MainActivity.kt           home screen, navigation, and all saving
app/src/test/                    tests for the model, the engine, suggestions and the panel logic
tools/build_words.py             makes words.txt (only needed if you want a different list)
```

`model` (except LayoutRepository), `engine`, `suggest` and `net` contain no Android code, so their
tests run on your computer in a second: right-click `app/src/test` > Run tests, or `./gradlew test`.

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
the small long-press characters in key corners). For the background: `backgroundOpacity` and
`keyOpacity` (0 to 1), `backgroundBlur` (0 to 40), `backgroundEnd` (a second colour, which makes the background a
gradient), `gradient` (`down`, `across` or `diagonal`), and `colors` (`own`, or `phone` to
take the colours from the phone).

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
- Suggestions and autocorrect work entirely on the phone, from a word list inside the app.
  There is no AI service and no server involved. What the keyboard learns is one text file,
  `learned_words.txt`, in the app's private storage; you can read its format in
  `suggest/UserWords.kt` and erase it from **Settings**. The code that learns has no access
  to the code that goes online.
- In password boxes the keyboard does not read, suggest, correct or learn. The rule is
  `suggest/FieldRules.kt` and it has its own tests.
- The last-copied button reads the phone's clipboard when you copy something or open the
  keyboard, and shows it. It stores nothing.
- "Add new screenshots to the clipboard" is off until you switch it on. It needs Android's
  permission to see your photos. With it the keyboard asks the gallery for pictures added
  since it last looked, keeps only those in a Screenshots folder or named like a screenshot,
  and copies them into its clipboard history on the phone. It opens no other picture and
  uploads nothing. (If you ever publish the app, note that Google Play restricts this
  permission to apps that need broad photo access and asks for a justification.)
- The only things sent over the internet are the search words you type **in the GIF panel**
  (to GIPHY, with your key) and the request for the GIF you tap. All of it is in `net/Http.kt`.
- The clipboard history is kept in the app's private storage and is never sent anywhere. It is
  off until you turn it on, and it can be erased from **Settings**.

## The word list

`assets/words/words.txt` has one word per line with a score (how common it is) and a language:
English, Hinglish or both. It is made by `tools/build_words.py` from three sources:

- English words and their frequencies from the **wordfreq** project (Robyn Speer and others).
  Rare words are only included if they are also in Webster's Second International word list
  ("web2", public domain), which keeps misspellings out.
- A hand-written list of about 800 everyday Hinglish words and short forms.
- wordfreq's Hindi list, turned into English letters by rules in the script. These spellings
  are guesses, so they score lower; your own spellings are learned as you type.

**Licence note:** wordfreq's data is published under Creative Commons Attribution-ShareAlike
4.0, and `words.txt` is derived from it. If you publish the app, credit wordfreq and keep the
word list file under that same licence. That concerns the list, not your code. Check the
terms yourself before releasing; this note is not legal advice.

## Adding your own characters

The catalogue in the key editor is the list in `model/SymbolData.kt`, and the built-in
long-press choices are the table in `model/BuiltInChoices.kt`. Both are plain lists: add a
character or a whole group and it appears in the app.

## How it was checked

Phases 1 to 8 have been built and run on a phone.

For phase 9, everything without Android code was compiled with warnings treated as errors
and its 181 tests passed (166 before this phase). The new tests cover your own corrections,
the language leaning, the blur setting, the vibration strengths, the panel height while
searching, and telling screenshots from other pictures. Writing them turned up a fault in
phase 8, now fixed: once you had typed a word twice, slips for that word stopped being
corrected.

The Android parts were written without access to the Android build tools, so **phase 9 has
not been built or run yet**. The parts that talk to Android without drawing anything (the
clipboard, the gallery query for screenshots, vibration, settings) were type-checked against
small stand-ins for the Android classes; the drawing code was only checked for syntax.

Things that could only be reasoned about, not tried, and are worth watching:

- **Blur.** It uses Android's "background blur" for windows. Whether a phone allows it is up
  to its maker, and it has never been tried on a keyboard window here. If the style screen
  says the phone allows it and nothing blurs, or the blur covers more than the keyboard,
  say so.
- **Screenshots.** Found by folder and file name ("Screenshots", "Screenshot_..."), which is
  how Samsung, Pixel and most phones save them. A phone that names them differently needs
  its name added to `isScreenshot` in `model/ClipHistory.kt`.
- **Searching over the library.** The keys are drawn over the panel inside one window. Check
  that a tap between keys never sends a GIF from underneath, and that the library above the
  keys still scrolls.
- **Vibration strength.** The numbers behind the slider (5 to 35 milliseconds, soft to full)
  are a first guess. If the whole range feels too weak or too strong, they are two lines in
  `KeyboardEngine.buzzFor`.

## Roadmap

9. **Suggestion extras:** removing a single learned word from the strip, emoji suggestions,
   slips judged by your own key arrangement instead of QWERTY
10. **Editor extras:** dragging keys with a finger
11. **Panel extras:** emoji search and skin tones
12. **Polish:** key sounds, landscape tuning

Decided against: sharing keyboards as files, and swipe (glide) typing.

`targetSdk` is 34 for now. Targeting 35 forces edge-to-edge drawing, which needs extra
handling so the keyboard does not sit under the navigation bar.
