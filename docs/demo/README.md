# The README demo

`demo.gif` is a real recording of risco on a phone. The touches are scripted, so a take can be
redone exactly after the look changes.

| File | |
|---|---|
| [story.py](story.py) | The storyboard: every touch, key and caption, with its time. Writes `demo.txt` for the phone and `touches.json` / `story.json` for render.py |
| [Inject.java](Inject.java) | Plays `demo.txt` on the phone through Android's input manager, the way scrcpy does, so the strokes are smooth and repeatable |
| [rec.sh](rec.sh) | Records the screen on the phone while the script plays, then pulls the video |
| [render.py](render.py) | Trims the video, draws the thumb (Android doesn't show injected touches) and the captions, and writes the GIF |
| [run.sh](run.sh) | Plays any script, for trying out a single stroke |

The rows and letters in `story.py` are pixel positions on a 1080×2340 phone with the author's
apps, so a new take on another phone needs them measured again (hold a letter, take a
screenshot).

## Making a take

You need adb, ffmpeg, JDK 17, the Android SDK's `android.jar` and `d8`, and Python with Pillow
and NumPy. From this folder, in Git Bash:

```bash
SDK=<android sdk>
javac -source 8 -target 8 -cp "$SDK/platforms/android-34/android.jar" -d classes Inject.java
"$SDK/build-tools/34.0.0/d8.bat" --output . classes/Inject.class
MSYS_NO_PATHCONV=1 adb push classes.dex /data/local/tmp/inject.dex

python story.py          # writes demo.txt, touches.json, story.json
bash rec.sh take         # records take.mp4 on the phone
python render.py take.mp4 demo   # writes demo.mp4 and demo.gif
```

Before recording: a plain black wallpaper, icons off (`:icons off`, since the take turns them on),
and anything private hidden with `:hide`. Unhide it afterwards.
