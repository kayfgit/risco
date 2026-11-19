# risco

A one-handed Android launcher for people who'd rather not look for things. Every feature is free,
nothing runs in the background, and there are no icons unless you ask: just names, under your thumb.

Press the alphabet rail, slide to a letter, slide off to an app, let go. That's one stroke.

## Use

| | |
|---|---|
| Open an app | Press either rail, slide to its letter, slide off towards the list, slide to the app, let go |
| Change your mind | Let go on the rail or past the far end of the list, or slide back onto the rail for another letter |
| Search | Swipe up, type, Enter. Matches accents, word starts and initials (`gm` finds Google Maps) |
| Notifications | Swipe down |
| An app's shortcuts (New message, New tab…) | Hold the app on home or in search |
| Add to home, hide, uninstall | Hold an app on home or in search |
| Add a website or chat to home | Use the app's own "Add to home screen", then confirm. Hold it to remove it |
| Rename an app or shortcut | Hold it, Rename. Search still finds it by its old name |
| Wallpaper, widgets, icons | Hold empty space on home |
| Move, resize or remove a widget | Hold it, then drag it, drag a corner, or tap Remove. Tap elsewhere when done |

Both edges are rails, one for each thumb, but only the one you used last is drawn. Press the other
edge and it takes over. The list opens beside whichever rail you pressed. Letters never move, so
your thumb learns where they are. A letter with no apps hands over to the closest one that has some.

The letters come from the phone's languages, plus A to Z: a Russian phone gets А to Я above A to
Z, a Japanese one the kana rows. They depend only on the languages, never on which apps are
installed. For a shorter rail, `:alphabet` picks one alphabet alone, and names in other scripts
are spelled in it to find their letter ("Камера" goes under K on `:alphabet english`).

risco is in English, German, Portuguese, Spanish, French, Russian and Japanese, and follows the
phone unless `:language` gives it its own. Commands are typed in English in every language.

Shortcuts, from an app or added to home, need risco to be the default launcher (`:default`):
Android only shares them with the home app.

### With TalkBack or Switch Access

Each letter on the drawn rail is a button: touch or swipe to it to hear "G, 10 apps", and
double-tap for a menu of its apps. Search, notifications, and wallpaper and widgets are in the
actions menu of every letter and pinned app, since swipes and holds on home go to TalkBack.
A widget's actions menu moves, resizes and removes it a step at a time.

## Commands

Type these in search. Anything that takes an app shows matches as you type, and Enter takes the
one at the bottom.

| Command | |
|---|---|
| `:pin <app>` / `:unpin <app>` | Add to or remove from home. `:unpin` also takes shortcuts |
| `:rename <app>` | Give an app or shortcut your own name |
| `:hide <app>` / `:unhide <app>` | Take off the rail and search, or bring back |
| `:info <app>` | Android's page for the app |
| `:rm <app>` | Uninstall |
| `:widget <widget>` | Add a widget to the top of home. Hold it to move, resize or remove it |
| `:wallpaper` | The phone's wallpaper picker |
| `:icons <on\|off>` | Grey icons beside app names. Off by default |
| `:alphabet <name>` | The rail's letters: `default`, `english`, `russian`, `japanese`, `greek`, `korean`, `arabic`, `hebrew`, `hindi` or `thai` |
| `:language` | Android's page for risco's own language (Android 13 and up) |
| `:menu` | What holding empty space opens |
| `:set` | Android settings |
| `:default` | Choose the default launcher |
| `:q` | Close |

## Build

You need JDK 17 and the Android SDK (platform 34, build-tools 34). Android Studio isn't needed.

```powershell
.\gradlew.bat test              # the drag, search and commands, no phone needed
.\gradlew.bat spotlessApply     # format (ktlint)
.\gradlew.bat lint              # Android Lint; any warning fails
.\install-local.ps1             # build, install on the connected phone, open
```

CI runs the format check, lint, tests and a release build on every pull request and push to
`main`, and keeps the APK for two weeks under the run's artifacts.

Trying risco doesn't replace your launcher. It installs as a normal app until you run `:default`.

## How it's put together

| File | |
|---|---|
| [Drag.kt](app/src/main/java/com/kayf/risco/Drag.kt) | The whole gesture, with no Android in it, so it's tested on the PC |
| [RailView.kt](app/src/main/java/com/kayf/risco/RailView.kt) | Feeds touches to `Drag` and draws what it says |
| [Fuzzy.kt](app/src/main/java/com/kayf/risco/Fuzzy.kt) | Search ranking and accent folding |
| [Commands.kt](app/src/main/java/com/kayf/risco/Commands.kt) | The `:` line |
| [Apps.kt](app/src/main/java/com/kayf/risco/Apps.kt) | Installed apps, kept up to date by the system |
| [Widgets.kt](app/src/main/java/com/kayf/risco/Widgets.kt) | Widgets on home, and how `:widget` names them |
| [Hold.kt](app/src/main/java/com/kayf/risco/Hold.kt) | A press held still, for home's menu and a widget's |
| [MainActivity.kt](app/src/main/java/com/kayf/risco/MainActivity.kt) | Home, search and wiring |

No AndroidX, no Compose, no runtime dependencies beyond Kotlin itself.

With gesture navigation, a swipe in from the edge means *back*, and Android lets an app opt out for
only about 200dp per edge, less than a rail. The opt-out covers the bottom of each rail. Above it,
sliding along the rail still works, because the back gesture only takes strokes moving inward.

## License

Copyright (C) 2026 Kayf

risco is free software: you can redistribute it and/or modify it under the
terms of the GNU General Public License as published by the Free Software
Foundation, either version 3 of the License, or (at your option) any later
version. See [LICENSE](LICENSE).
