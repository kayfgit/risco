"""render.py <in.mp4> <out-basename>: finds sync, draws the thumb dot, writes <out>.mp4 and <out>.gif."""
import json, subprocess, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

SRC, OUT = sys.argv[1], sys.argv[2]
FPS, W, H = 60, 540, 1170            # working size: half the phone
K = W / 1080
touches = json.load(open("touches.json"))
FIRST_TOUCH = touches[0][0] / 1000   # script time of the first press
LEAD, TAIL = 0.9, 0.25                # seconds kept before the first press / after the last event
story = json.load(open("story.json", encoding="utf-8"))
END = story["end"] / 1000
from PIL import ImageFont
try:
    FONT = ImageFont.truetype("C:/Windows/Fonts/consola.ttf", 25)
except OSError:
    FONT = ImageFont.truetype("DejaVuSansMono.ttf", 23)

def caption_layer(t):
    for a, b, text in story["captions"]:
        a, b = a / 1000, b / 1000
        if a <= t <= b:
            alpha = min(1, (t - a) / 0.2, (b - t) / 0.2)
            layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
            d = ImageDraw.Draw(layer)
            tw = d.textlength(text, font=FONT)
            cx, cy = W / 2, H - 120
            d.rounded_rectangle([cx - tw / 2 - 20, cy - 26, cx + tw / 2 + 20, cy + 26], 26,
                                fill=(255, 255, 255, int(235 * alpha)))
            d.text((cx, cy), text, font=FONT, anchor="mm", fill=(0, 0, 0, int(255 * alpha)))
            return layer
    return None

def frames():
    p = subprocess.Popen(["ffmpeg", "-v", "error", "-i", SRC, "-vf", f"fps={FPS},scale={W}:{H}:flags=lanczos",
                          "-f", "rawvideo", "-pix_fmt", "rgb24", "-"], stdout=subprocess.PIPE)
    while True:
        b = p.stdout.read(W * H * 3)
        if len(b) < W * H * 3: break
        yield np.frombuffer(b, np.uint8).reshape(H, W, 3)

all_frames = list(frames())
# Sync: the first press opens a list, so text appears in the panel area.
ref = all_frames[0][: H // 2, int(W * .2):int(W * .85)].astype(int).mean(2)
sync = None
for i, f in enumerate(all_frames):
    d = np.abs(f[: H // 2, int(W * .2):int(W * .85)].astype(int).mean(2) - ref)
    if (d > 60).sum() > 200:
        sync = i; break
offset = sync / FPS - FIRST_TOUCH      # video time = script time + offset
print("list appears at frame", sync, "offset", round(offset, 3))

def touch_at(t):
    """Finger state at script time t: (x, y, alpha) or None, with a short fade after lifting."""
    last = None
    for ev in touches:
        if ev[0] / 1000 > t: break
        last = ev
    if last is None: return None
    tt, x, y, down = last
    if down:
        # fade in over 70 ms after the press of this stroke
        start = max(e[0] for e in touches if e[0] <= tt * 1 and e[0] / 1000 <= t and
                    (touches.index(e) == 0 or touches[touches.index(e) - 1][3] == 0))
        return x, y, min(1, (t - start / 1000) / 0.07)
    age = t - tt / 1000
    return (x, y, 1 - age / 0.18) if age < 0.18 else None

R = 34  # dot radius at working size (~ a thumb pad on screen)
def dot_layer(x, y, a):
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    cx, cy = x * K, y * K
    d.ellipse([cx - R - 4, cy - R - 4, cx + R + 4, cy + R + 4], outline=(0, 0, 0, int(150 * a)), width=4)
    d.ellipse([cx - R, cy - R, cx + R, cy + R], fill=(255, 255, 255, int(80 * a)),
              outline=(255, 255, 255, int(235 * a)), width=4)
    return layer.filter(ImageFilter.GaussianBlur(0.8))

first = max(0, int((offset + FIRST_TOUCH - LEAD) * FPS))
last = min(len(all_frames), int((offset + END + TAIL) * FPS))
enc = subprocess.Popen(["ffmpeg", "-v", "error", "-y", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}",
                        "-r", str(FPS), "-i", "-", "-c:v", "ffv1", f"{OUT}.mkv"], stdin=subprocess.PIPE)
for i in range(first, last):
    t = i / FPS - offset
    im = Image.fromarray(all_frames[i]).convert("RGBA")
    st = touch_at(t)
    if st and st[2] > 0: im = Image.alpha_composite(im, dot_layer(*st))
    cap = caption_layer(t)
    if cap is not None: im = Image.alpha_composite(im, cap)
    enc.stdin.write(im.convert("RGB").tobytes())
enc.stdin.close(); enc.wait()

# GIF: 360 wide, rounded corners made transparent, one palette for the whole clip.
gw, rad = 360, 26
mask = Image.new("L", (gw, int(gw * H / W)), 0)
ImageDraw.Draw(mask).rounded_rectangle([0, 0, mask.width - 1, mask.height - 1], rad, fill=255)
mask.save("mask.png")
vf = (f"fps=FPSG,scale={gw}:-1:flags=lanczos,format=rgba[v];movie=mask.png,format=gray[m];[v][m]alphamerge,"
      "split[a][b];[a]palettegen=max_colors=COLORS:reserve_transparent=1:stats_mode=full[p];"
      "[b][p]paletteuse=dither=none:diff_mode=rectangle:alpha_threshold=128")
vf = vf.replace("FPSG", "25").replace("COLORS", "40")
subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", f"{OUT}.mkv", "-filter_complex", vf, f"{OUT}.gif"], check=True)
subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", f"{OUT}.mkv", "-c:v", "libx264", "-crf", "18", "-preset", "slow",
                "-pix_fmt", "yuv420p", "-movflags", "+faststart", f"{OUT}.mp4"], check=True)
print("frames", last - first, "seconds", round((last - first) / FPS, 2))
