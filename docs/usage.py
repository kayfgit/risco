"""Shape of real app usage, from `adb shell dumpsys usagestats`. Prints shares only, never app names.

usage: python usage.py <usagestats dump> <launchable activities list>
"""
import re
import sys
from math import log

SECTION = re.compile(r"^\s*In-memory (daily|weekly|monthly|yearly) stats")
RANGE = re.compile(r'timeRange="([^"]+)"')
ROW = re.compile(r"package=(\S+).*?appLaunchCount=(\d+)")


def read_sections(path):
    sections = {}
    name = None
    with open(path, encoding="utf-8", errors="replace") as dump:
        for line in dump:
            found = SECTION.match(line)
            if found:
                name = found.group(1)
                sections[name] = {"counts": {}, "range": None}
            elif name:
                if line.strip().endswith("stats files:") or "sorted list of files" in line:
                    name = None
                    continue
                span = RANGE.search(line)
                if span and not sections[name]["range"]:
                    sections[name]["range"] = span.group(1)
                row = ROW.search(line)
                if row:
                    counts = sections[name]["counts"]
                    counts[row.group(1)] = max(counts.get(row.group(1), 0), int(row.group(2)))
    return sections


def launchable(path):
    with open(path, encoding="utf-8", errors="replace") as listing:
        return {line.strip().split("/")[0] for line in listing if "/" in line}


def zipf_exponent(counts):
    """Least-squares slope of log(count) on log(rank)."""
    points = [(log(rank), log(count)) for rank, count in enumerate(counts, 1) if count > 0]
    mean_x = sum(x for x, _ in points) / len(points)
    mean_y = sum(y for _, y in points) / len(points)
    top = sum((x - mean_x) * (y - mean_y) for x, y in points)
    return -top / sum((x - mean_x) ** 2 for x, _ in points)


def main():
    sections = read_sections(sys.argv[1])
    apps = launchable(sys.argv[2])
    print(f"launchable apps on the phone: {len(apps)}")
    for name in ("weekly", "monthly", "yearly"):
        section = sections.get(name)
        if not section:
            continue
        counts = sorted((c for p, c in section["counts"].items() if p in apps and c > 0), reverse=True)
        total = sum(counts)
        if not total:
            print(f"{name}: no launches")
            continue
        print(f"\n{name}: {section['range'].encode('ascii', 'replace').decode()}")
        print(f"  launches {total}, distinct apps launched {len(counts)}, zipf exponent {zipf_exponent(counts):.2f}")
        for n in (1, 3, 5, 8, 16, 32):
            print(f"  top {n:2}: {sum(counts[:n]) / total * 100:5.1f}%")


if __name__ == "__main__":
    main()
