#!/usr/bin/env python3
"""Sequence processed stems (render.py --stem) into one continuous program over a looped bed.

  mix.py out/stems/a.wav out/stems/b.wav ... --beds assets/loops/x.wav assets/loops/y.wav --out out/program.wav

Each stem already carries its own reverb tail. Transitions are mostly 'stream' (the next song enters
as the last notes ring out) and some 'breathe' (the tail decays into the bed, then a slow fade-in).
The bed runs under everything without a break.
Writes <out>.tracklist.md with start times and the collected credits.
"""
import argparse
from pathlib import Path

import numpy as np

from loop import SR, drift, load, save, tile
from render import FINAL_LUFS, fade, guard, level


def transition_styles(n, breathe_share, rng):
    """Mostly 'stream' (songs run into each other), some 'breathe' (bed heard between). At least one of each."""
    styles = ["breathe" if rng.random() < breathe_share else "stream" for _ in range(n)]
    if n >= 2:
        if "breathe" not in styles:
            styles[int(rng.integers(n))] = "breathe"
        if "stream" not in styles:
            styles[int(rng.integers(n))] = "stream"
    return styles


def stamp(seconds):
    return f"{int(seconds // 60)}:{int(seconds % 60):02d}"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("stems", nargs="+")
    ap.add_argument("--beds", nargs="+", required=True)
    ap.add_argument("--bed-db", type=float, default=-12.0)
    ap.add_argument("--overlap", type=float, default=None, help="fixed overlap in seconds for every transition (disables styles)")
    ap.add_argument("--breathe", type=float, default=0.3, help="share of transitions that let the bed breathe")
    ap.add_argument("--lead-in", type=float, default=6.0)
    ap.add_argument("--seed", type=int, default=7)
    ap.add_argument("--out", required=True)
    args = ap.parse_args()
    rng = np.random.default_rng(args.seed)

    stems = [load(s) for s in args.stems]
    styles = transition_styles(len(stems) - 1, args.breathe, rng) if args.overlap is None else ["fixed"] * (len(stems) - 1)
    starts, fade_ins, pos = [], [2.0], int(args.lead_in * SR)
    for i, s in enumerate(stems):
        starts.append(pos)
        if i == len(stems) - 1:
            break
        style = styles[i]
        if style == "stream":  # next song enters as this one's last notes ring out
            overlap, fin = rng.uniform(13, 17), 1.5
        elif style == "breathe":  # tail decays into the bed, then a slow entry
            overlap, fin = rng.uniform(3, 6), rng.uniform(5, 8)
        else:
            overlap, fin = args.overlap, 2.0
        pos += len(s) - int(overlap * SR)
        fade_ins.append(fin)
    total = starts[-1] + len(stems[-1]) + int(4 * SR)

    loops = [load(b) for b in args.beds]
    bed = sum(tile(l, total, int(rng.integers(len(l)))) * drift(total, rng) for l in loops) / np.sqrt(len(loops))
    mix = fade(bed * 10 ** (args.bed_db / 20), 0, 0, in_s=4.0, out_s=10.0)
    for s, at, fin in zip(stems, starts, fade_ins):
        mix[at:at + len(s)] += fade(s, 0, 0, in_s=fin, out_s=4.0)

    mix = guard(level(mix, FINAL_LUFS))
    save(args.out, mix)

    lines = [f"# {Path(args.out).stem}", "", f"Length {stamp(total / SR)}, bed {args.bed_db:+.0f} dB", ""]
    into = ["start"] + styles
    lines += [f"{i + 1}. {stamp(at / SR)} {Path(p).stem} ({into[i]})" for i, (p, at) in enumerate(zip(args.stems, starts))]
    lines += ["", "## Credits", ""]
    for p in args.stems:
        c = Path(p).with_suffix(".credits.md")
        if c.exists():
            lines += [l for l in c.read_text().splitlines() if l.startswith("- ")]
    lines += [f"- Bed: {Path(b).name}" for b in args.beds]
    Path(args.out).with_suffix(".tracklist.md").write_text("\n".join(lines) + "\n")
    print(f"{args.out}: {stamp(total / SR)}, {len(stems)} stems")


if __name__ == "__main__":
    main()
