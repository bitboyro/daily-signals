#!/usr/bin/env python3
"""Seamless loops and long mixed beds from field recordings.

  loop.py loops  assets/beds/*          -> assets/loops/<name>.wav  + seam report
  loop.py render assets/loops/*.wav --seconds 600 --mode layer|sequence -> out/bed.wav

Loop: the tail of the file is crossfaded (equal power) into its head, so the wrap point is
continuous. Render/layer: each loop runs at its own length, so the sum does not repeat audibly.
Render/sequence: beds take turns, crossfading into each other.
"""
import argparse
import random
from pathlib import Path

import numpy as np
import pyloudnorm as pyln
from pedalboard.io import AudioFile

SR = 44100
TARGET_LUFS = -30.0  # beds sit well below music


def load(path):
    with AudioFile(str(path)).resampled_to(SR) as f:
        chunks = []
        while True:
            c = f.read(SR * 10)
            if c.shape[1] == 0:
                break
            chunks.append(c)
    a = np.concatenate(chunks, axis=1).T  # (n, ch)
    return a if a.shape[1] == 2 else np.repeat(a[:, :1], 2, axis=1)


def save(path, a):
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    with AudioFile(str(path), "w", SR, 2) as f:
        f.write(np.ascontiguousarray(a.T.astype(np.float32)))


def normalize(a):
    lufs = pyln.Meter(SR).integrated_loudness(a)
    gain = 10 ** ((TARGET_LUFS - lufs) / 20)
    out = a * gain
    peak = np.abs(out).max()
    return out / peak * 0.9 if peak > 0.9 else out


def make_loop(a, xfade_s):
    n = int(min(xfade_s, len(a) / SR / 4) * SR)
    m = len(a) - n
    t = np.linspace(0, 1, n, endpoint=False)[:, None]
    out = a[:m].copy()
    out[:n] = a[m:] * np.cos(t * np.pi / 2) + a[:n] * np.sin(t * np.pi / 2)
    return out, n / SR


def seam_ratio(loop):
    """Sample-to-sample jump at the wrap vs the typical jump. About 1 means inaudible."""
    d = np.abs(np.diff(loop, axis=0)).max(axis=1)
    jump = np.abs(loop[0] - loop[-1]).max()
    return float(jump / (np.median(d) + 1e-9)), float(np.percentile(d, 99.9) / (np.median(d) + 1e-9))


def cmd_loops(args):
    for p in args.files:
        a = normalize(load(p))
        loop, used = make_loop(a, args.xfade)
        ratio, worst = seam_ratio(loop)
        dest = Path(args.out) / (Path(p).stem + ".wav")
        save(dest, loop)
        print(f"{Path(p).name}: {len(a)/SR:.1f}s -> loop {len(loop)/SR:.1f}s, xfade {used:.1f}s, "
              f"seam jump {ratio:.1f}x median (loudest normal jump {worst:.0f}x)")


def tile(loop, n_samples, start):
    idx = (start + np.arange(n_samples)) % len(loop)
    return loop[idx]


def drift(n, rng, depth=0.35, period_s=90):
    """Slow random gain wander around 1.0 for natural variation."""
    ph = rng.uniform(0, 2 * np.pi)
    t = np.arange(n) / SR
    return (1 + depth * np.sin(2 * np.pi * t / period_s + ph) * rng.uniform(0.5, 1))[:, None]


def cmd_render(args):
    rng = np.random.default_rng(args.seed)
    loops = [load(p) for p in args.files]
    n = int(args.seconds * SR)
    if args.mode == "layer":
        mix = sum(tile(l, n, int(rng.integers(len(l)))) * drift(n, rng) for l in loops) / np.sqrt(len(loops))
    else:
        mix = np.zeros((n, 2))
        pos, prev = 0, None
        xf = int(args.xfade * SR)
        order = list(range(len(loops)))
        while pos < n:
            rng.shuffle(order)
            for i in order:
                seg_len = int(rng.uniform(60, 120) * SR) + xf
                seg = tile(loops[i], seg_len, int(rng.integers(len(loops[i]))))
                env = np.ones(seg_len)
                ramp = np.sin(np.linspace(0, np.pi / 2, xf))
                env[:xf] = ramp
                env[-xf:] = ramp[::-1]
                end = min(n, pos + seg_len)
                mix[pos:end] += seg[: end - pos] * env[: end - pos, None]
                pos += seg_len - xf
                if pos >= n:
                    break
    peak = np.abs(mix).max()
    if peak > 0.95:
        mix = mix / peak * 0.95
    fade = int(3 * SR)
    mix[:fade] *= np.linspace(0, 1, fade)[:, None]
    mix[-fade:] *= np.linspace(1, 0, fade)[:, None]
    save(args.out, mix)
    print(f"rendered {args.seconds}s ({args.mode}) from {len(loops)} loops -> {args.out}")


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    a = sub.add_parser("loops")
    a.add_argument("files", nargs="+")
    a.add_argument("--xfade", type=float, default=8.0)
    a.add_argument("--out", default="assets/loops")
    a.set_defaults(fn=cmd_loops)
    b = sub.add_parser("render")
    b.add_argument("files", nargs="+")
    b.add_argument("--seconds", type=float, default=300)
    b.add_argument("--mode", choices=["layer", "sequence"], default="layer")
    b.add_argument("--xfade", type=float, default=8.0)
    b.add_argument("--seed", type=int, default=1)
    b.add_argument("--out", default="out/bed.wav")
    b.set_defaults(fn=cmd_render)
    args = ap.parse_args()
    args.fn(args)
