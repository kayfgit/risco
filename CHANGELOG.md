# Changelog

## Unreleased

- risco remembers app names and icons between starts, so after a restart your apps are back in
  about 0.2 seconds instead of up to 2, and it no longer briefly uses over 150 MB while reading
  them. Turning icons on reads every icon again, which picks up a new icon theme.
- With icons on, starting risco decodes app icons at the size they're drawn instead of at the
  screen's full density, so it briefly uses less memory and settles sooner.
- The README opens with a short recording of risco in use, made by the scripts in `docs/demo`.
- The README starts with a plain-words summary and quick facts (size, memory, battery, permissions)
  for people using risco, with building, contributing and releasing moved to a section for
  developers below.

## 0.2.0 - 2026-11-19

**TL;DR**
- Hold an app for its shortcuts, add websites and chats to home from other apps, and use risco with
  TalkBack.
- Rename apps, move and resize widgets by dragging them, and pick which alphabet the rail shows.
- risco now speaks seven languages.
- The first release you can install straight from GitHub: download `risco-0.2.0.apk` below. If you
  had risco from somewhere else, uninstall that first.

**Details**

- Releases are now signed with risco's own key and published here when a version is tagged, so
  each new release installs over the last one and keeps your pins and settings.
- risco speaks German, Portuguese, Spanish, French, Russian and Japanese, following the phone. On
  Android 13 and up you can give risco its own language with `:language`. Typed commands stay in
  English.
- Pick the rail's letters with `:alphabet`: `english`, `russian`, `japanese`, `greek`, `korean`,
  `arabic`, `hebrew`, `hindi`, `thai`, or `default` for the phone's languages plus A to Z. Names
  in other scripts are spelled in the chosen alphabet to find their letter, so "Камера" goes under
  K on the English rail. The name shown doesn't change.

- The rail's letters follow the phone's languages, plus A to Z since most app names are Latin.
  A Russian phone gets А to Я, a Japanese one the kana rows, and Chinese names are filed under
  their pinyin letter. Longer alphabets get smaller letters to fit.
- Rename any app or pinned shortcut: hold it and choose Rename, or type `:rename <app>`. It moves
  to its new letter on the rail, and search still finds it by its old name too. Reset, or saving
  it blank, brings back the app's own name.
- Edit widgets by holding them. An outline appears with a dot on each corner: drag a corner to
  resize (taller, shorter, wider, narrower, within what the widget allows), drag the widget to
  move it above or below others, or tap Remove. Tap anywhere else to finish. TalkBack and Switch
  Access users get the same moves in the actions menu.

- Holding an app shows its shortcuts, like "New message" or "New tab", above the other choices.
  Up to four, the app's main ones first. Needs risco to be the default launcher.
- Other apps can add shortcuts to home, such as a website from Chrome's "Add to home screen" or a
  chat from a messaging app. risco asks "Add to home screen?" first. Added shortcuts sit with your
  pinned apps and show up in search; hold one, or use `:unpin`, to remove it.
- Works with TalkBack and Switch Access. Each rail letter can be found by touch or by swiping, is
  read as "G, 10 apps", and opens a menu of its apps. Search, notifications, and wallpaper and
  widgets are in the actions menu, since TalkBack takes swipes and holds for itself.
- Menus and messages use plain, capitalised words: holding an app offers Add to home, Hide, App
  info and Uninstall instead of `pin`, `hide`, `info` and `rm`. Typed commands haven't changed.

- risco is now licensed under the GNU GPL v3 or later (GPL-3.0-or-later), copyright Kayf.

- Hold empty space on home for a menu like a stock launcher's: wallpaper, widgets, icons and
  commands. `:menu` opens it too.
- Widgets on home, stacked at the top. Add one from the menu or with `:widget <name>`, which lists
  them as you type; hold one to remove it. A clock is now a widget too, so `:clock` is gone.
- `:wallpaper` opens the phone's wallpaper picker.
- `:icons on` shows grey icons beside app names on home, in search and in the rail's list. Off by
  default.

- Renamed from dedo to risco, with a new icon: a lowercase r in one stroke. The app ID changed to
  `com.kayf.risco`, so it installs beside dedo as a new app; pins, hidden apps and settings start
  fresh, and dedo can be uninstalled (`:rm dedo`).

- A rail on both edges at once, so either thumb can open apps. The list opens beside the rail you
  pressed. Home and search are centred.
- Removed `:left` and `:right`, which only moved the single rail.
- Only the rail last used is drawn. Pressing the other edge switches to it, and the choice is kept.
- The rail's letters sit right at the edge again.
- Letting go on the rail closes the letter instead of keeping it open to tap. The hold menu inside
  an open letter went with it; hold an app on home or in search instead.
- The welcome text shows until the first app is opened, then goes away.
- Nothing is carried over when moving to a new phone, matching backups, which were already off.
- Autofill no longer offers itself in search.
- Search's results no longer jump up when the keyboard lands. They open where the keyboard will be,
  using its height from last time.
- For development: ktlint formatting (`spotlessApply`), Android Lint failing on any warning, and
  GitHub Actions running both with the tests and a release build, keeping the APK.

## 0.1.0 - 2026-10-30

First version.

- Alphabet rail: press it, slide to a letter, slide off to an app, let go. Letting go on the rail
  keeps the letter open to tap, and letting go past the list cancels.
- Search on swipe up, matching accents, word starts and initials, ranked by how often you open each app.
- `:` commands: `pin`, `unpin`, `hide`, `unhide`, `info`, `rm`, `left`, `right`, `set`, `default`, `q`.
- Swipe down for notifications.
- Hold an app on home, in search, or in a letter you tapped open for pin, hide, info and uninstall.
  Pausing mid-drag doesn't open this menu.
- The `:` list scrolls when it doesn't fit above the keyboard.
- Design notes, a launch-time model and a usage reader in `docs/`.
