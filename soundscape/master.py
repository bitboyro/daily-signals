#!/usr/bin/env python3
"""Smooth a finished mix: soften the top end and add breathing room with gentle downward expansion.

  master.py out/program-blues-nyc-v7.wav --out out/program-blues-nyc-v8.wav

Expansion: below the threshold (default: 8 dB under the mix's average RMS) the level is pushed down at
--ratio (1.4 = for every dB under, 0.4 dB extra), capped at --max-cut dB, with slow smoothing so it never
pumps. Loud passages are untouched; quiet ones open up. The result is re-leveled to --lufs.
"""
import argparse

import numpy as np
import pyloudnorm as pyln
from pedalboard import HighpassFilter, HighShelfFilter, LowpassFilter, Pedalboard, Reverb
from scipy.ndimage import uniform_filter1d
from scipy.signal import lfilter

from loop import SR, load, save
from render import guard, level


def expand(a, ratio, max_cut, below_db, window_s=0.4, smooth_s=0.6):
    power = uniform_filter1d((a ** 2).mean(axis=1), size=int(window_s * SR))
    lvl = 10 * np.log10(power + 1e-12)
    thr = 10 * np.log10(power.mean() + 1e-12) - below_db
    gain_db = np.clip((lvl - thr) * (ratio - 1), -max_cut, 0)
    gain_db[lvl < thr - 40] = 0  # leave near-silence alone (no gating artifacts)
    k = 1 - np.exp(-1 / (smooth_s * SR))  # one-pole smoothing, run forward and back: no lag, no pumping
    gain_db = lfilter([k], [1, k - 1], gain_db)
    gain_db = lfilter([k], [1, k - 1], gain_db[::-1])[::-1]
    return (a * (10 ** (gain_db / 20))[:, None]).astype(np.float32), gain_db


def wet_arcs(a, starts, length_s, peak):
    """Long reverb journeys: over length_s the mix drifts into a big dark room and back (raised cosine,
    equal-power crossfade, wet share peaking at `peak` in the middle)."""
    board = Pedalboard([Reverb(room_size=0.93, damping=0.55, wet_level=1.0, dry_level=0.0, width=1.0),
                        LowpassFilter(4500)])
    for s0 in starts:
        i0, i1 = int(s0 * SR), min(len(a), int((s0 + length_s) * SR))
        pre = min(i0, int(3 * SR))  # let the reverb fill up before the arc starts
        seg = a[i0 - pre:i1]
        wet = board(seg.T.copy(), SR).T[pre:]
        dry = a[i0:i1]
        wet *= np.sqrt((dry ** 2).mean() / ((wet ** 2).mean() + 1e-12))
        c = (peak * 0.5 * (1 - np.cos(2 * np.pi * np.arange(i1 - i0) / (i1 - i0)))).astype(np.float32)[:, None]
        a[i0:i1] = dry * np.sqrt(1 - c) + wet * np.sqrt(c)
    return a


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("mix")
    ap.add_argument("--ratio", type=float, default=1.6)
    ap.add_argument("--max-cut", type=float, default=6.0)
    ap.add_argument("--below", type=float, default=3.0, help="threshold in dB under the average RMS")
    ap.add_argument("--window", type=float, default=1.5, help="seconds of audio the expander listens to")
    ap.add_argument("--shelf-db", type=float, default=-2.5)
    ap.add_argument("--lufs", type=float, default=-18.5)
    ap.add_argument("--wet-arcs", type=float, nargs="*", default=[], help="start times (s) of reverb journeys")
    ap.add_argument("--arc-len", type=float, default=240.0)
    ap.add_argument("--arc-peak", type=float, default=0.6, help="wet share at the top of each arc")
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    a = load(args.mix).astype(np.float32)
    tone = Pedalboard([HighpassFilter(30), HighShelfFilter(cutoff_frequency_hz=7000, gain_db=args.shelf_db, q=0.7),
                       LowpassFilter(15000)])
    a = tone(a.T, SR).T
    a = wet_arcs(a, args.wet_arcs, args.arc_len, args.arc_peak)
    a, g = expand(a, args.ratio, args.max_cut, args.below, window_s=args.window)
    a = guard(level(a, args.lufs))
    save(args.out, a)
    print(f"{args.out}: {pyln.Meter(SR).integrated_loudness(a):.1f} LUFS; expansion cut median {np.median(g):.1f} dB, "
          f"max {g.min():.1f} dB, active {100 * (g < -0.5).mean():.0f}% of the time")


if __name__ == "__main__":
    main()
