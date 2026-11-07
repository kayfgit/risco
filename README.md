# dedo

A one-handed Android launcher for people who'd rather not look for things. Every feature is free,
nothing runs in the background, and there are no icons: just names, under your thumb.

Press the alphabet rail, slide to a letter, slide off to an app, let go. That's one stroke.

## Use

| | |
|---|---|
| Open an app | Press the rail, slide to its letter, slide off towards the list, slide to the app, let go |
| Change your mind | Let go past the left of the list, or slide back onto the rail for another letter |
| Open a letter to tap instead | Let go while still on the rail |
| Search | Swipe up, type, Enter. Matches accents, word starts and initials (`gm` finds Google Maps) |
| Notifications | Swipe down |
| Pin, hide, uninstall | Hold an app on home, in search, or in a letter you tapped open |

### Edge strip (experimental)

`:edge` puts a thin strip low on the rail's side of the screen, over every app. Flick in from it
and an app opens without going home first. Where the flick starts and which way it goes picks the
app:

| | Straight in | In and up | In and down |
|---|---|---|---|
| **Upper half** | pin 3 | pin 4 | dedo |
| **Lower half** | pin 1 | pin 2 | pin 5 |

It needs the "display over other apps" permission and keeps a silent notification while it runs.
Every stroke is logged, so `adb logcat -s dedo-edge` shows how often the system takes a stroke for
its back gesture.

Letters never move, so your thumb learns where they are. A letter with no apps hands over to the
closest one that has some.

## Commands

Type these in search. Anything that takes an app shows matches as you type, and Enter takes the
one at the bottom.

| Command | |
|---|---|
| `:pin <app>` / `:unpin <app>` | Add to or remove from home |
| `:hide <app>` / `:unhide <app>` | Take off the rail and search, or bring back |
| `:info <app>` | Android's page for the app |
| `:rm <app>` | Uninstall |
| `:left` / `:right` | Which side the rail and edge strip are on |
| `:edge` | Edge strip on or off |
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

The rail's letters sit 38dp in from the edge on purpose. With gesture navigation, a swipe from the
edge means *back*, and Android lets an app opt out for only about 200dp per edge, far less than a
rail. So the opt-out covers what it can, and the rest stays clear of the edge.
