# Changelog

## Unreleased

- A rail on both edges at once, so either thumb can open apps. The list opens beside the rail you
  pressed. Home and search are centred.
- Removed `:left` and `:right`, which only moved the single rail.
- Only the rail last used is drawn. Pressing the other edge switches to it, and the choice is kept.
- The rail's letters sit right at the edge again.
- Letting go on the rail closes the letter instead of keeping it open to tap. The hold menu inside
  an open letter went with it; hold an app on home or in search instead.
- The welcome text shows until the first app is opened, then goes away.
- The clock and date are off by default. `:clock` puts them at the top, middle or bottom, or turns
  them off.
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
