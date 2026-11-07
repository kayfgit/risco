# Edge strip: first test

Shelved after one session on the author's Galaxy A36 (Android 16, One UI, gesture navigation).
Parked on this branch to come back to.

## What the log showed

11 strokes, from `adb logcat -s dedo-edge`:

| Result | Count |
|---|---|
| Recognized as a flick | 10 |
| Taken by the system for back | **0** |
| Too short | 1 |

- **The back gesture never took a stroke.** That was the risk the strip was built to test, and it
  held up: the 200dp exclusion was enough on One UI.
- **Every flick came out straight in.** Slots 0 and 2 only, plus one accidental leader after
  15 ms. None of the up or down diagonals were ever recognized. The flick is decided after 28dp,
  and a thumb stroke is still nearly horizontal that early, curving only later.
- **Fast:** flicks registered 54–375 ms after touch-down, median about 170 ms.

## Why it was shelved

- There was no feedback, so you couldn't tell which pin a flick would open until it opened.
- A flick couldn't be cancelled or paused once started.
- Mis-opens, which match the diagonals never being recognized.

## If it comes back

- Decide on lift, or after a longer distance, so the diagonal has time to appear. Or classify by
  the stroke's overall direction instead of its first 28dp.
- Show the target while the finger is down, and let sliding back to the edge cancel it, the way the
  rail already works.
- Fewer slots: straight in from each half gave 100% of the recognized flicks here.
