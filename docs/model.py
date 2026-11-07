"""Expected seconds per app launch for each launcher design, for a trained user.

These are estimates to compare designs, not measurements. Real numbers come from :bench once the
prototype exists. Everything assumed is in P, so any of it can be challenged by changing one line.
"""
from dataclasses import dataclass, replace
from math import log2


@dataclass(frozen=True)
class P:
    recall: float = 0.30       # deciding what to do; every design pays it
    fitts_a: float = 0.10      # thumb tap: a + b * ID, about 5.5 bits/s
    fitts_b: float = 0.18
    home_stroke: float = 0.25  # the gesture that goes home from inside an app
    home_anim: float = 0.35    # waiting for the home animation before the launcher can be used
    from_app: float = 0.75     # share of launches that start inside another app
    mark: float = 0.55         # zone stroke: Zhao 2006 measured 553 ms at 32 items (stylus)
    mark_error: float = 0.04   # zone-32 was 96% accurate
    leader: float = 0.25       # edge flick that arms the marks from inside any app
    edge_mark: float = 0.30    # single flick in from the edge that opens an app directly
    t9_tap: float = 0.28
    t9_keys: float = 3.1       # see keys_to_unique below
    recovery: float = 2.0      # backing out of the wrong app
    apps: int = 80             # apps used at least now and then
    zipf: float = 1.0          # app launches fall off with rank as 1 / rank^zipf
    curve: tuple = ()          # measured (rank, cumulative share) points, used instead of zipf
    slots: int = 32            # how many marks exist


# From usage.py on the author's phone: 16,946 launches over six months.
MEASURED = dict(
    apps=83,
    curve=((1, 0.353), (3, 0.639), (5, 0.735), (8, 0.819), (16, 0.913), (32, 0.973), (83, 1.0)),
)
LAUNCHES_PER_DAY = 94


def fitts(p, distance, width):
    return p.fitts_a + p.fitts_b * log2(distance / width + 1)


def weights(p):
    if p.curve:
        result = []
        previous_rank, previous_share = 0, 0.0
        for rank, share in p.curve:
            result += [(share - previous_share) / (rank - previous_rank)] * (rank - previous_rank)
            previous_rank, previous_share = rank, share
        return result
    raw = [1 / rank ** p.zipf for rank in range(1, p.apps + 1)]
    total = sum(raw)
    return [w / total for w in raw]


def go_home(p):
    return p.from_app * (p.home_stroke + p.home_anim)


def stock(p, rank):
    tap = fitts(p, 150, 60)
    if rank <= 4:
        return go_home(p) + tap, 0.015
    if rank <= 20:
        return go_home(p) + 0.20 + 0.40 + tap, 0.015
    return go_home(p) + 0.45 + 1.00 + tap, 0.015


def rail(p, rank, favorites):
    if rank <= favorites:
        return go_home(p) + 0.15 + fitts(p, 150, 60), 0.015
    letter = fitts(p, 300, 22)
    row = fitts(p, 60, 52)
    return go_home(p) + fitts(p, 150, 64) + letter + 0.40 + row, 0.03


def keyboard_search(p, rank):
    return go_home(p) + 0.25 + 0.25 + 2.5 * 0.30 + 0.30 + 0.40, 0.02


def marks(p, rank):
    if rank <= p.slots:
        return go_home(p) + p.mark, p.mark_error
    return go_home(p) + p.t9_keys * p.t9_tap + 0.15, 0.03


def marks_anywhere(p, rank):
    if rank <= 5:
        return p.edge_mark, p.mark_error
    entry = p.from_app * p.leader
    if rank <= p.slots:
        return entry + p.mark, p.mark_error
    return entry + p.t9_keys * p.t9_tap + 0.15, 0.03


DESIGNS = {
    "stock launcher": stock,
    "Niagara / dedo today": lambda p, rank: rail(p, rank, 8),
    "keyboard search only": keyboard_search,
    "marks at home": marks,
    "marks from anywhere": marks_anywhere,
}


def expected(design, p):
    total = 0.0
    for rank, weight in enumerate(weights(p), 1):
        seconds, error = design(p, rank)
        total += weight * (p.recall + seconds + error * p.recovery)
    return total


def table(p, title):
    print(f"\n{title}")
    base = expected(DESIGNS["Niagara / dedo today"], p)
    for name, design in DESIGNS.items():
        seconds = expected(design, p)
        print(f"  {name:24} {seconds:5.2f} s  {seconds / base * 100:4.0f}% of Niagara")


T9 = {c: k for k, group in enumerate(["abc", "def", "ghi", "jkl", "mno", "pqrs", "tuv", "wxyz"]) for c in group}
SAMPLE = """Camera Chrome Clock Contacts Calendar Calculator Gmail Google Maps Messages Phone Photos Play
Store Settings YouTube WhatsApp Instagram Telegram Discord Spotify Netflix Twitch Reddit X TikTok
Files Drive Docs Sheets Slides Keep Meet Translate Authenticator Bitwarden Firefox Brave Termux
Obsidian Notion Slack Teams Zoom LinkedIn GitHub Steam Pinterest Snapchat Facebook Messenger Uber
Waze Nubank Itau Bradesco iFood Rappi MercadoLivre Shopee Amazon Kindle Duolingo Anki Strava Fitbit
Weather Recorder Gallery Notes Tasks Wallet Pay Lens Podcasts Music SoundCloud Deezer Crunchyroll
Prime Disney HBO Paramount Tinder Bumble Signal Outlook OneDrive Word Excel PowerPoint Canva CapCut
Lightroom VLC NewPipe""".split()


def keys_to_unique():
    """How many T9 keys until a name is the only match left, over a plausible 100-app phone."""
    codes = {name: [T9[c] for c in name.lower() if c in T9] for name in SAMPLE}
    needed = []
    for name, code in codes.items():
        others = [c for n, c in codes.items() if n != name]
        for length in range(1, len(code) + 1):
            if not any(o[:length] == code[:length] for o in others):
                break
        needed.append(length)
    return sum(needed) / len(needed), max(needed), len(SAMPLE)


def main():
    print("T9 keys to a unique match: mean %.2f, worst %d, over %d names" % keys_to_unique())
    p = P(**MEASURED)
    table(p, "measured usage, 32 marks")
    # Zhao 2006: zone-16 was 97-99% accurate with the same drawing time as zone-32.
    p16 = replace(p, slots=16, mark_error=0.02)
    table(p16, "measured usage, 16 marks")
    table(replace(p16, mark=0.35, mark_error=0.01), "16 marks, better than the stylus data")
    table(replace(p16, mark=0.80, mark_error=0.10), "16 marks, much worse on a thumb than the stylus data")
    table(replace(p16, from_app=0.4), "16 marks, most launches start at home")
    table(replace(p16, fitts_b=0.14), "16 marks, faster thumb tapping")
    saved = expected(DESIGNS["Niagara / dedo today"], p16) - expected(DESIGNS["marks from anywhere"], p16)
    print(f"\nsaved vs Niagara: {saved:.2f} s per launch, {saved * LAUNCHES_PER_DAY / 60:.1f} min per day")
    table(P(), "assumed zipf usage, for reference")


if __name__ == "__main__":
    main()
