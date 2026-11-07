# dedo

A one-handed Android launcher for people who'd rather not look for things. Every feature is free,
nothing runs in the background, and there are no icons: just names, under your thumb.

Press the alphabet rail, slide to a letter, slide off to an app, let go. That's one stroke.

## Use

| | |
|---|---|
| Open an app | Press either rail, slide to its letter, slide off towards the list, slide to the app, let go |
| Change your mind | Let go on the rail or past the far end of the list, or slide back onto the rail for another letter |
| Search | Swipe up, type, Enter. Matches accents, word starts and initials (`gm` finds Google Maps) |
| Notifications | Swipe down |
| Pin, hide, uninstall | Hold an app on home or in search |

Both edges are rails, one for each thumb, but only the one you used last is drawn. Press the other
edge and it takes over. The list opens beside whichever rail you pressed. Letters never move, so
your thumb learns where they are. A letter with no apps hands over to the closest one that has some.

## Commands

Type these in search. Anything that takes an app shows matches as you type, and Enter takes the
one at the bottom.

| Command | |
|---|---|
| `:pin <app>` / `:unpin <app>` | Add to or remove from home |
| `:hide <app>` / `:unhide <app>` | Take off the rail and search, or bring back |
| `:info <app>` | Android's page for the app |
| `:rm <app>` | Uninstall |
| `:clock <top\|middle\|bottom\|off>` | Where the clock and date go. Off by default |
| `:set` | Android settings |
| `:default` | Choose the default launcher |
| `:q` | Close |

## Build

You need JDK 17 and the Android SDK (platform 34, build-tools 34). Android Studio isn't needed.

```powershell
.\gradlew.bat test              # the drag, search and commands, no phone needed
.\install-local.ps1             # build, install on the connected phone, open
```

Trying dedo doesn't replace your launcher. It installs as a normal app until you run `:default`.

## How it's put together

| File | |
|---|---|
| [Drag.kt](app/src/main/java/com/kayf/dedo/Drag.kt) | The whole gesture, with no Android in it, so it's tested on the PC |
| [RailView.kt](app/src/main/java/com/kayf/dedo/RailView.kt) | Feeds touches to `Drag` and draws what it says |
| [Fuzzy.kt](app/src/main/java/com/kayf/dedo/Fuzzy.kt) | Search ranking and accent folding |
| [Commands.kt](app/src/main/java/com/kayf/dedo/Commands.kt) | The `:` line |
| [Apps.kt](app/src/main/java/com/kayf/dedo/Apps.kt) | Installed apps, kept up to date by the system |
| [MainActivity.kt](app/src/main/java/com/kayf/dedo/MainActivity.kt) | Home, search and wiring |

No AndroidX, no Compose, no runtime dependencies beyond Kotlin itself.

With gesture navigation, a swipe in from the edge means *back*, and Android lets an app opt out for
only about 200dp per edge, less than a rail. The opt-out covers the bottom of each rail. Above it,
sliding along the rail still works, because the back gesture only takes strokes moving inward.
