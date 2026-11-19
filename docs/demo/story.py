"""Writes demo.txt (for the phone) and touches.json (for drawing the thumb dot later)."""
import json, math

def Y(i): return 612 + 61.9 * i          # centre of rail letter i (A=0 ... #=26)
L, R = 36, 1044                           # left and right rail x
STEP = 8                                  # ms between moves
lines, touches = [], []                   # touches: [t, x, y, down?]

def ease(u): return u * u * (3 - 2 * u)

def stroke(t, pts):
    """pts: [(x, y, ms_to_get_here)]; the first is the press, a 0-ms point is a hold."""
    x, y, _ = pts[0]
    lines.append(f"{t} down {x:.0f} {y:.0f}"); touches.append([t, x, y, 1])
    for nx, ny, ms in pts[1:]:
        n = max(1, ms // STEP)
        for k in range(1, n + 1):
            u = ease(k / n)
            px, py = x + (nx - x) * u, y + (ny - y) * u
            tt = t + k * ms // n
            lines.append(f"{tt} move {px:.0f} {py:.0f}"); touches.append([tt, px, py, 1])
        t += ms; x, y = nx, ny
    lines.append(f"{t} up {x:.0f} {y:.0f}"); touches.append([t, x, y, 0])
    return t

def hold(x, y, ms): return (x, y, ms)

captions = []   # [start ms, end ms, text]
AUTH = [781, 928, 1070]                   # the three Authenticator rows (Google's is the middle one)
AX = 720                                  # finger x over the right rail's list
t = 1200
# 1. Left thumb: press A, slide down to C, off to Calculator, let go: it opens.
c0 = t
t = stroke(t, [(L, Y(0), 0), hold(L, Y(0) + 2, 300), (L, Y(2), 650), hold(L, Y(2), 450),
               (300, Y(2), 250), (420, 200, 550), hold(422, 200, 500)])
captions.append([c0, t, "Slide to a letter, then to the app"])
t += 1300
lines.append(f"{t} key 3"); t += 900       # HOME
# 2. Right thumb: wants Google Authenticator, finds three of them, gives up.
c0 = t
t = stroke(t, [(R, Y(2), 0), hold(R, Y(2) + 2, 250), (R, Y(0), 400), hold(R, Y(0), 300),
               (780, Y(0), 250), (AX, AUTH[0], 400), hold(AX, AUTH[0], 450),
               (AX, AUTH[1], 300), hold(AX, AUTH[1], 400), (AX, AUTH[2], 300), hold(AX, AUTH[2], 400),
               (AX, AUTH[1], 300), hold(AX, AUTH[1], 350), (R, AUTH[1] - 300, 500), hold(R, AUTH[1] - 300, 250)])
captions.append([c0 + 1600, t + 200, "Google Authenticator… which one?"])
t += 600
# 3. Swipe up, ask for icons.
t = stroke(t, [(560, 1750, 0), (560, 1150, 180)])
t += 600
for ch in ":icons on":
    lines.append(f"{t} key 62" if ch == " " else f"{t} text {ch}"); t += 110
t += 500
lines.append(f"{t} key 66"); t += 800      # ENTER
# 4. Same thumb, now it can tell them apart.
c0 = t
t = stroke(t, [(R, Y(0), 0), hold(R, Y(0) + 2, 650), (780, Y(0), 250), (AX, AUTH[1], 450), hold(AX, AUTH[1], 900)])
captions.append([c0 + 900, t + 300, "That one"])
END = t
lines.append(f"{t + 2500} key 3")          # back home after the cut
open("demo.txt", "w").write("\n".join(lines) + "\n")
json.dump(touches, open("touches.json", "w"))
json.dump({"captions": captions, "end": END}, open("story.json", "w"))
print("length ms", t)
