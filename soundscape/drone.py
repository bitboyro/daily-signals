#!/usr/bin/env python3
"""Add a key-following low drone under a finished mix.

  drone.py out/program-blues-nyc-v7.wav --out out/program-blues-nyc-v9-pre.wav --stem out/drone.wav

1. Listens: 20 s windows -> 1-cent pitch-class histogram (catches 78 rpm off-tuning) -> Krumhansl key
   profiles -> root + tuning per window. Low-confidence windows (speech, beds) hold the previous root.
   Roots are median-smoothed and short segments merged, so the drone changes only between songs.
2. Plays: sub sine on the root (41-82 Hz), 2nd/3rd harmonics so laptops can hear it, and a detuned,
   slowly opening saw texture for width. Glides between roots, breathes slowly.
The root timeline is written next to --out as <name>.drone.md.
"""
import argparse
from pathlib import Path

import numpy as np
from scipy.ndimage import gaussian_filter1d, median_filter, uniform_filter1d
from scipy.signal import decimate

from loop import SR, load, save

NAMES = ["C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B"]
MAJOR = np.array([6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88])
MINOR = np.array([6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17])
C1 = 32.703


def analyze(mono, win_s=20, hop_s=5):
    sr = SR // 4
    x = decimate(decimate(mono, 2), 2).astype(np.float32)
    n_fft = 16384
    f = np.fft.rfftfreq(n_fft, 1 / sr)
    band = (f >= 90) & (f <= 1500)
    cents = (1200 * np.log2(f[band] / C1)) % 1200
    idx = np.round(cents).astype(int) % 1200
    hann = np.hanning(n_fft).astype(np.float32)
    out = []
    for t0 in np.arange(0, len(x) / sr - win_s, hop_s):
        seg = x[int(t0 * sr):int((t0 + win_s) * sr)]
        frames = [seg[i:i + n_fft] * hann for i in range(0, len(seg) - n_fft, n_fft // 2)]
        p = np.mean([np.abs(np.fft.rfft(fr)) ** 2 for fr in frames], axis=0)[band]
        w = np.log1p(p / (np.median(p) + 1e-12))
        hist = gaussian_filter1d(np.bincount(idx, weights=w, minlength=1200), 12, mode="wrap")
        tune = int(np.argmax([hist[np.arange(12) * 100 + o].sum() for o in range(-50, 50)])) - 50
        chroma = np.array([hist[(k * 100 + tune + np.arange(-30, 31)) % 1200].sum() for k in range(12)])
        chroma = chroma - chroma.mean()
        best = (-1.0, 0, "")
        for mode, prof in (("major", MAJOR), ("minor", MINOR)):
            for k in range(12):
                r = float(np.corrcoef(chroma, np.roll(prof, k))[0, 1])
                if r > best[0]:
                    best = (r, k, mode)
        rms = float(np.sqrt((seg ** 2).mean()))
        out.append((t0 + win_s / 2, best[1], best[2], tune, best[0], rms))
    return out


def segments(wins, min_conf, min_seg_s):
    """Segment on absolute pitch (root + tuning, in thirds of a semitone), so a 40-cent-sharp 78 in F
    gets its own drone instead of sharing one with an in-tune F."""
    cents, prev = [], None
    for _, k, _, tune, conf, _ in wins:
        if conf >= min_conf or prev is None:
            prev = k * 100 + tune
        cents.append(prev)
    cents = np.array(cents, dtype=float)
    labels = median_filter(np.round(cents / 33.3).astype(int), size=9, mode="nearest")
    segs = []  # [start_s, end_s, label, member cents]
    for (t, *_), lab, c in zip(wins, labels, cents):
        if segs and abs(segs[-1][2] - lab) <= 0:
            segs[-1][1] = t
            segs[-1][3].append(c)
        else:
            segs.append([t, t, lab, [c]])
    changed = True
    while changed and len(segs) > 1:
        changed = False
        for i in range(len(segs) - 1):  # neighbours within ~20 cents are the same drone
            if abs(np.median(segs[i][3]) - np.median(segs[i + 1][3])) <= 20:
                segs[i][1] = segs[i + 1][1]
                segs[i][3] += segs[i + 1][3]
                segs.pop(i + 1)
                changed = True
                break
        if changed:
            continue
        short = min(range(len(segs)), key=lambda i: segs[i][1] - segs[i][0])
        if segs[short][1] - segs[short][0] < min_seg_s:  # fold the shortest into its longer neighbour
            i = short
            nb = [j for j in (i - 1, i + 1) if 0 <= j < len(segs)]
            tune = lambda j: (np.median(segs[j][3]) - np.median(segs[i][3]) + 50) % 100 - 50
            j = min(nb, key=lambda j: (round(abs(tune(j)) / 10), -(segs[j][1] - segs[j][0])))  # same 78 speed first
            segs[j][0], segs[j][1] = min(segs[j][0], segs[i][0]), max(segs[j][1], segs[i][1])
            segs[j][3] += segs[i][3]
            segs.pop(i)
            changed = True
    segs[0][0] = 0
    for a, b in zip(segs, segs[1:]):
        a[1] = b[0] = (a[1] + b[0]) / 2
    out = []
    for s0, s1, _, cs in segs:
        c = float(np.median(cs))
        k = int(round(c / 100)) % 12
        out.append((s0, s1, k, c - round(c / 100) * 100))
    return out


def root_hz(k, tune):
    f = C1 * 2 ** ((k * 100 + tune) / 1200)
    while f < 41:
        f *= 2
    return f


MOVES = [0, 0, 7, 12, 5, 10, -12]  # semitones from the root: fifth, octave, fourth, flat 7th, octave down
RIFFS = [[0, 0, 7, 10], [0, 12, 0, 7], [0, 0, 0, -5], [0, 10, 7, 5], [0, 7, 12, 7], [0, 0, 12, 10]]
RATES = [0, 0, 0.25, 0.5, 0.75, 1, 1.5, 2, 3, 4]  # pulses per second; 0 = steady


def phrases(dur, rng):
    t, out = 0.0, []
    while t < dur:
        mode = str(rng.choice(["hold", "move", "riff"], p=[0.4, 0.35, 0.25]))
        out.append(dict(t0=t, t1=min(dur, t + rng.uniform(8, 40)), mode=mode,
                        rate=float(rng.choice([0.5, 1, 1.5, 2]) if mode == "riff" else rng.choice(RATES)),
                        depth=float(rng.uniform(0.3, 0.95)), sharp=float(rng.uniform(1, 6)),
                        move=int(rng.choice(MOVES)) if mode == "move" else 0,
                        glide=float(rng.uniform(0.3, 6)), riff=RIFFS[rng.integers(len(RIFFS))]))
        t = out[-1]["t1"]
    return out


def synth(n, segs, glide_s, rng, grunge=1.0, chunk_s=10):
    """Root follows the detected keys; on top, random 8-40 s phrases that hold, move to another chord
    tone, or play a short riff, each with its own pulse rate/depth/shape. A slow intensity walk drives
    level, brightness and how hard the grit layer (tanh + bitcrush, 70-1100 Hz) is pushed."""
    from pedalboard import Bitcrush, HighpassFilter, LowpassFilter, Pedalboard
    cr = SR / 100  # control rate
    nc = n // 100 + 2
    t_ctrl = np.arange(nc) / cr
    root = np.zeros(nc)
    for a, b, k, tune in segs:
        root[int(a * cr):int(b * cr) + 2] = root_hz(k, tune)
    root = uniform_filter1d(root, size=max(1, int(glide_s * cr)), mode="nearest")

    ph_list = phrases(n / SR, rng)
    rate, depth, sharp, glide = (np.zeros(nc) for _ in range(4))
    for p in ph_list:
        i0, i1 = int(p["t0"] * cr), int(p["t1"] * cr) + 1
        rate[i0:i1], depth[i0:i1], sharp[i0:i1] = p["rate"], p["depth"] * (p["rate"] > 0), p["sharp"]
        glide[i0:i1] = 0.04 if p["mode"] == "riff" else p["glide"]
    cycles = np.cumsum(rate / cr)
    depth = uniform_filter1d(depth, size=int(cr), mode="nearest")
    pulse = 1 - depth + depth * (0.5 + 0.5 * np.cos(2 * np.pi * cycles)) ** sharp

    target = np.zeros(nc)
    for p in ph_list:
        i0, i1 = int(p["t0"] * cr), int(p["t1"] * cr) + 1
        if p["mode"] == "move":
            target[i0:i1] = p["move"]
        elif p["mode"] == "riff":
            r = np.array(p["riff"])
            target[i0:i1] = r[np.floor(cycles[i0:i1] - cycles[i0]).astype(int) % len(r)]
    off = np.empty(nc)
    y = 0.0
    k_g = 1 - np.exp(-1 / (np.maximum(glide, 0.01) * cr))
    for i in range(nc):  # per-phrase glide speed
        y += (target[i] - y) * k_g[i]
        off[i] = y
    freq = root * 2 ** (off / 12)
    freq = np.where((freq < 36) & (root > 0), freq * 2, freq)

    pts = rng.uniform(0, 1, int(n / SR / 25) + 3)  # intensity: a new level every ~25 s, smoothed
    inten = gaussian_filter1d(np.interp(t_ctrl, np.arange(len(pts)) * 25, pts), 8 * cr)
    inten = (inten - inten.min()) / (np.ptp(inten) + 1e-9)

    eps, t = [], float(rng.uniform(20, 60))  # episodes: bass present 60-240 s, then gone 30-120 s
    present = np.zeros(nc)
    while t < n / SR:
        e1 = min(n / SR, t + rng.uniform(60, 240))
        eps.append((t, e1))
        present[int(t * cr):int(e1 * cr)] = 1
        t = e1 + float(rng.uniform(30, 120))
    present[int((n / SR - 90) * cr):] = 1  # the ending (bass through to the final fade) stays as it is
    if eps and eps[-1][1] > n / SR - 90:
        eps[-1] = (min(eps[-1][0], n / SR - 90), n / SR)
    else:
        eps.append((n / SR - 90, n / SR))
    present = gaussian_filter1d(present, 4 * cr)  # ~15 s fades in and out

    grit_fx = [Pedalboard([Bitcrush(bit_depth=9), HighpassFilter(60), LowpassFilter(420)]) for _ in range(2)]
    out = np.zeros((n, 2), dtype=np.float32)
    ph = np.zeros(2)
    step = int(chunk_s * SR)
    for s in range(0, n, step):
        m = min(step, n - s)
        tc = (s + np.arange(m)) / 100
        at = lambda arr: np.interp(tc, np.arange(nc), arr)
        f, I, pu = at(freq), at(inten), at(pulse)
        base = ph[0] + np.cumsum(2 * np.pi * f / SR)
        det = ph[1] + np.cumsum(2 * np.pi * f * 1.0035 / SR)
        ph[:] = base[-1] % (2 * np.pi), det[-1] % (2 * np.pi)
        br = 1.2 + 2.0 * I
        clean = np.sin(base) + 0.2 * np.sin(2 * base)
        drive = (1.5 + 7 * I ** 2) * grunge
        mixg = (0.2 + 0.8 * I) * min(grunge, 1.5)
        amp = pu * (0.45 + 0.55 * I) * at(present)
        for c, phase in enumerate((base, det)):
            raw = sum(np.sin(h * phase) * np.exp(-(h - 1) / br) / h for h in range(1, 11))
            g = np.tanh(drive * (raw + 0.15)) - np.tanh(drive * 0.15)  # bias -> even harmonics, grittier
            g = grit_fx[c](g.astype(np.float32)[None, :], SR, reset=False)[0]
            out[s:s + m, c] = amp * (clean + 1.2 * mixg * g)
    fade = int(8 * SR)
    out[:fade] *= np.linspace(0, 1, fade)[:, None]
    out[-fade * 3:] *= np.linspace(1, 0, fade * 3)[:, None]
    return out, ph_list, eps


def stamp(s):
    return f"{int(s // 60)}:{int(s % 60):02d}"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("mix")
    ap.add_argument("--db", type=float, default=-11.0, help="drone RMS relative to the mix RMS")
    ap.add_argument("--glide", type=float, default=4.0, help="seconds to slide between roots")
    ap.add_argument("--min-conf", type=float, default=0.55, help="key-profile correlation needed to change root")
    ap.add_argument("--min-seg", type=float, default=75.0, help="shortest root segment in seconds")
    ap.add_argument("--grunge", type=float, default=1.0, help="0 = clean, 1 = default grit, 2 = filthy")
    ap.add_argument("--seed", type=int, default=3)
    ap.add_argument("--stem", help="also write the drone alone here")
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    a = load(args.mix).astype(np.float32)
    wins = analyze(a.mean(axis=1))
    segs = segments(wins, args.min_conf, args.min_seg)
    d, ph_list, eps = synth(len(a), segs, args.glide, np.random.default_rng(args.seed), grunge=args.grunge)
    rms = lambda x: float(np.sqrt((x.astype(np.float64) ** 2).mean()))
    blk = d[: len(d) // SR * SR].reshape(-1, SR, 2).astype(np.float64)
    blk_rms = np.sqrt((blk ** 2).mean(axis=(1, 2)))
    d_rms = float(np.sqrt((blk_rms[blk_rms > 0.3 * blk_rms.max()] ** 2).mean()))  # level of the bass when present
    d *= rms(a) / d_rms * 10 ** (args.db / 20)
    if args.stem:
        save(args.stem, d)
    mix = a + d
    peak = float(np.abs(mix).max())
    if peak > 0.98:
        mix *= 0.98 / peak
    save(args.out, mix)

    lines = [f"# Drone under {Path(args.mix).name}", "", f"Level {args.db} dB vs mix RMS, glide {args.glide} s", ""]
    for s0, s1, k, tune in segs:
        lines.append(f"- {stamp(s0)}-{stamp(s1)}: {NAMES[k]} ({tune:+.0f} cents), {root_hz(k, tune):.1f} Hz")
    lines += ["", "## Bass episodes", ""] + [f"- {stamp(a0)}-{stamp(a1)}" for a0, a1 in eps]
    lines += ["", "## Phrases", ""]
    lines += [f"- {stamp(p['t0'])} {p['mode']}" + (f" {p['move']:+d} st" if p['mode'] == 'move' else "")
              + (f" riff {p['riff']}" if p['mode'] == 'riff' else "")
              + (f", pulse {p['rate']}/s depth {p['depth']:.1f}" if p['rate'] else ", steady") for p in ph_list]
    lines += ["", "## Windows (time, key, tuning, confidence)", ""]
    lines += [f"- {stamp(t)} {NAMES[k]} {m} {tu:+d}c r={c:.2f}" for t, k, m, tu, c, _ in wins[::4]]
    Path(args.out).with_suffix(".drone.md").write_text("\n".join(lines) + "\n")
    print("\n".join(lines[4:4 + len(segs)]))


if __name__ == "__main__":
    main()
