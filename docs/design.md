# Designing past Niagara

The goal is the lowest expected time from "I want X" to X being open, for a trained thumb. Looks,
discoverability and first-time use don't count. A design that takes a week to learn is fine if it
is faster for the next three years. The same bargain as vim.

**Status:** a proposal. Nothing here exists yet except the model. App usage is measured from the
author's phone, but the timings are estimates built on stylus and tablet studies, so they still
need measuring on a real thumb (see [Build order](#build-order)).

## Measured usage

16,946 launches over six months on one phone, about 94 a day, across 83 different apps. From
[usage.py](usage.py).

| Top apps | 1 | 3 | 5 | 8 | 16 | 32 |
|---|---|---|---|---|---|---|
| Share of launches | 35% | 64% | 74% | 82% | 91% | 97% |

Five apps are three quarters of everything. This decides the priorities below.

## What's wrong with Niagara

Counting slides undersells the problem, because a slide isn't one unit of cost.

1. **Going home is the biggest cost, and nobody counts it.** From inside an app you first swipe home
   and wait out the animation. In the model that's about 0.45 s on an average launch, more than
   the gain from any layout change.
2. **The rail needs your eyes for the whole stroke.** Hitting one of 27 letters, each 3.5 mm tall,
   is a precise aim, and then you read a list. It can't become muscle memory.
3. **It stops at the app.** "Open WhatsApp" is usually the first step of "message this person".
   Those taps come after the launcher, and they cost more than the launcher does.

The model puts Niagara's rail *behind* a well-arranged stock home screen on time, at 1.60 s against
1.45 s, even though the rail takes fewer strokes. That is a modeled result from assumed timings,
not a measurement, and `:bench` should test it.

## The design

Three layers, in the order the thumb reaches them. Each one handles what the one above can't.

| Layer | Reaches | Gesture | Vim equivalent |
|---|---|---|---|
| **Edge flick** | Top 5 apps (74% of launches), from inside any app | One flick in from the screen edge | A global mapping |
| **Marks** | 16 bindings (91% of launches) | Landing zone + stroke direction | Normal-mode mappings |
| **Pad** | Everything else | Tap T9 keys; it opens at the first unique match, no Enter | Incremental search |

### Edge flick: leave home out of it

An always-on overlay holds a thin strip on the thumb's edge, inside the 200dp that Android lets an
app claim from the back gesture. The strip has two zones and three inward directions, which makes
six flicks: five open an app directly, and one is the **leader**. The leader arms the marks over
whatever app you're in, which replaces going home.

`SYSTEM_ALERT_WINDOW` provides both halves: it draws the overlay, and Android's documentation lists
it as an exception that allows starting activities from the background. If the strip fights the
system on your phone, the fallback is the digital-assistant role. An app qualifies by handling
`ACTION_ASSIST`, and the assistant gets the corner swipe, so that gives one system-owned entry
gesture with no overlay.

### Marks: position plus direction

A mark is *where the thumb lands* (4 zones) plus *which way it flicks*, in one stroke with nothing
drawn on screen. 4 directions give 16 bindings, and 8 give 32. Sixteen is the default: it already
reaches 91% of launches, and it's the more accurate size, so going to 32 costs accuracy to cover
another 6%.

```
 +-----------+-----------+
 |           |           |      each zone:   NW  N  NE
 |    Z1     |    Z2     |                    W  .  E
 |           |           |                   SW  S  SE
 +-----------+-----------+
 |           |           |      Z3 + NW  =  one app, or one task
 |    Z3     |    Z4     |
 |           |           |      no menu, no animation, one haptic tick
 +-----------+-----------+
```

This is Zone menus (Zhao et al., 2006), the best-supported numbers here: 16 items at 97–99% accuracy
and 32 items at 96%, both with about 550 ms of drawing time, while 64 items failed pilot testing.
Depth loses to
breadth, since 16 items as two strokes took 1489 ms against 1081–1208 ms as one. So marks stay
**one stroke**, and if two ever become necessary they lift between strokes, because lifted marks
were 93% accurate against 80% for a single zig-zag.

A thumb has a limited arc, and some zone and direction pairs will be bad. `:bench` finds those, and
they get dropped.

### Pad: the long tail without a keyboard

The same surface doubles as a 3×3 T9 pad: a stroke is a mark and a tap is a key, so there's no mode
to enter. Keys go straight to a filter, and the app opens the moment only one match is left. Across
a sample of 96 common app names that takes 3.1 keys on average and 5 at worst. There's no system
keyboard, so no keyboard animation.

### Rules that hold across all three

- **Slots never move on their own.** Bindings are set by hand. Split menus beat both alphabetical
  and pure-frequency menus, and adaptive menus that rearrange themselves were no faster and
  disoriented people. Any suggestion from usage data is reported, never applied.
- **Bind tasks, not apps.** A slot can hold an app, an Android app shortcut, a deep link or a
  `:` command. One stroke to a specific chat saves more than any launcher trick. Reading app
  shortcuts requires risco to be the default launcher.
- **Config is text.** Bindings live in a plain `riscorc` you can edit, diff and back up, like a vimrc:

  ```
  map Z3.NW  com.whatsapp
  map Z3.N   shortcut:com.whatsapp/<id>
  map edge.1 com.android.chrome
  ```
- **No visual feedback path at all.** A haptic tick confirms the mark, and the app simply opens.

## Estimates

Seconds per launch on the measured usage with 16 marks, including 0.30 s of deciding that every
design pays. From [model.py](model.py).

| Scenario | Stock | Niagara / risco today | Marks at home | Marks from anywhere |
|---|---|---|---|---|
| Base case | 1.45 | 1.60 | 1.38 | **0.80** |
| Marks better than the stylus data | 1.45 | 1.60 | 1.18 | **0.74** |
| Marks much worse on a thumb (0.8 s, 90% accurate) | 1.45 | 1.60 | 1.76 | **0.99** |
| Most launches start at home | 1.24 | 1.39 | 1.17 | **0.78** |
| Faster thumb tapping | 1.38 | 1.49 | 1.38 | **0.80** |

**Marks at home are not worth building alone.** Niagara's favorites already reach the top apps
cheaply, so marks gain only 14%, and they lose to Niagara in the bad case. Removing the trip home is
what holds the design near half of Niagara in every row, so the edge strip is the product and the
marks are its second layer.

The base case saves 0.80 s per launch, or about 1.3 minutes a day at 94 launches. The wider gain is
fewer interruptions: most launches stop needing your eyes or a trip home. Task bindings aren't
counted here at all, and they can save more per use than the launch itself.

## What could break this

| Risk | Why it matters | Test |
|---|---|---|
| Thumb accuracy is worse than the stylus data | Every mark number comes from pens on tablets | `:bench`: 95% accurate and under 0.7 s per mark, or reduce the set |
| The edge strip loses touches to the back gesture | There's a report of touches failing right at the edge on Android 11 | Try the strip on your phone before building on it |
| The overlay gets killed | Battery managers vary by manufacturer | Run it as a foreground service for a week |
| Sideload restrictions | Android may gate some sensitive permissions for sideloaded apps; not confirmed for overlays | Grant it once on your phone |
| T9 auto-open fires on the wrong app | A new app changes which prefix is unique | Log wrong opens in `:bench` |

## Build order

1. **Edge strip spike.** The riskiest piece and nearly all of the gain, so it decides whether this
   design stands. It needs five flicks that open apps from inside any app, on the real phone. If it
   fails, switch to the assistant fallback before designing further around it.
2. **`:bench`.** Prompts a target and logs milliseconds and errors per zone and direction, which
   replaces these estimates with real numbers.
3. **Marks and `riscorc`**, reached through the leader.
4. **Task bindings.** Uncounted by the model, and potentially the largest saving per use.
5. **Pad.** Last, since it only serves the 9% of launches past the top 16, and search already
   covers those.

The rail stays until `:bench` shows marks beating it on your thumb, then it goes.

## Sources

- Zhao, Agrawala, Hinckley. [Zone and Polygon Menus](https://www.microsoft.com/en-us/research/wp-content/uploads/2016/11/Zone-Polygon-Menus-CHI-2006.pdf), CHI 2006
- Zhao, Balakrishnan. [Simple vs. Compound Mark Hierarchical Marking Menus](https://www.dgp.toronto.edu/~ravin/papers/uist2004_simplemm.pdf), UIST 2004
- Kurtenbach, Buxton. [The Limits of Expert Performance Using Hierarchic Marking Menus](https://research.autodesk.com/app/uploads/2023/03/the-limits-of-expert.pdf_recyw5wL6ekPqcE4Q.pdf), CHI 1993
- Kurtenbach, Buxton. [User Learning and Performance with Marking Menus](https://www.research.autodesk.com/publications/user-learning-and-performance-with-marking-menus/), CHI 1994
- Sears, Shneiderman. [Split Menus](https://drum.lib.umd.edu/items/456aaf07-ad36-43a1-8f6d-99bf80dfce2f/full), 1994
- Findlater, McGrenere. [A Comparison of Static, Adaptive, and Adaptable Menus](https://www.cs.ubc.ca/labs/edapt/papers/findlater2004.pdf), CHI 2004
- Gajos et al. [Exploring the Design Space for Adaptive Graphical User Interfaces](https://www.eecs.harvard.edu/~kgajos/papers/2006/kgajos-avi06.pdf), AVI 2006
- Android: [Background activity starts](https://developer.android.com/guide/components/activities/background-starts), [RoleManagerCompat](https://developer.android.com/reference/androidx/core/role/RoleManagerCompat), [handling gesture conflicts](https://medium.com/androiddevelopers/gesture-navigation-handling-gesture-conflicts-8ee9c2665c69)
- Edge touch failure report: [Stack Overflow archive](https://kiwix.lema.org/stackoverflow.com_en_all_2023-11/questions/71585679/touch-not-detected-on-edges-when-touch-is-held)
