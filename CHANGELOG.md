# Changelog

## Unreleased

- Edge strip, experimental: `:edge` puts a thin strip on the thumb's edge over every app. Flicking
  in from it opens pins 1–5 without going home first, and one flick opens dedo. Needs the
  "display over other apps" permission and shows an ongoing notification.

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
