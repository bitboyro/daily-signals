#!/usr/bin/env python3
"""Synthesized low piano for the soundscape: pedal clusters and reversed swells.

  piano.py examples out/program-blues-nyc-v11.wav --outdir out/piano-examples

The piano model (no samples):
- 3 strings per note, each with its own small random detune and decay, so the beating evolves;
- up to 80 inharmonic partials f_k = k f sqrt(1 + B k^2) (bass notes are rich, with a weak fundamental);
- partials shaped by the hammer's strike point (~1/8 of the string) and velocity-dependent brightness;
- two-stage decay (fast "prompt" sound, then long aftersound), higher partials dying first;
- felt-hammer pulse plus a low knock on bass notes; damper release, slower with the pedal down;
- a synthetic soundboard body (damped wooden modes) and a convolution hall with darker, longer lows
  instead of a metallic algorithmic reverb.
Each example is a 40 s excerpt of the program in that section's key and 78 rpm tuning (from drone.py),
plus the piano part solo.
"""
import argparse
from pathlib import Path

import numpy as np
from pedalboard import LowpassFilter, Pedalboard
from scipy.signal import butter, fftconvolve, sosfilt

from loop import SR, load, save


def note(freq, dur, vel, rng, pedal=True):
    T0 = float(np.clip(14 * (110 / freq) ** 0.55, 0.8, 20))
    release = 0.6 if pedal else 0.12
    n = int(min(dur + release * 4, T0 * 1.3 + 0.5) * SR)
    t = np.arange(n, dtype=np.float32) / SR
    B = 0.00025 * (freq / 110) ** 0.7 + 0.00008
    beta = rng.uniform(0.115, 0.13)  # strike point
    y = np.zeros(n, np.float32)
    for _ in range(3):
        f = freq * 2 ** (rng.uniform(-1.8, 1.8) / 1200)
        d = rng.uniform(0.85, 1.15)
        for k in range(1, 81):
            fk = k * f * np.sqrt(1 + B * k * k)
            if fk > 10000:
                break
            amp = abs(np.sin(np.pi * k * beta)) / k ** 0.8 * np.exp(-fk / (1400 + 5200 * vel))
            if k == 1 and freq < 110:
                amp *= 0.35 + 0.65 * freq / 110
            Tk = d * T0 / (1 + fk / 700)
            env = 0.55 * np.exp(-t / (0.22 * Tk)) + 0.45 * np.exp(-t / Tk)
            y += (amp * env * np.sin(2 * np.pi * fk * t + rng.uniform(0, 6.28))).astype(np.float32)
    y /= 3
    off = int(dur * SR)
    if off < n:
        y[off:] *= np.exp(-np.arange(n - off) / (release * SR)).astype(np.float32)
    w = int((0.004 - 0.0025 * vel) * SR)  # felt: softer blows are longer pulses
    pulse = np.sin(np.pi * np.arange(w) / w) * 0.4 * vel
    y[:w] += pulse.astype(np.float32)
    if freq < 160:
        kn = int(0.06 * SR)
        y[:kn] += (0.12 * vel * np.exp(-np.arange(kn) / (0.012 * SR)) * np.sin(2 * np.pi * rng.uniform(70, 110) * np.arange(kn) / SR)).astype(np.float32)
    y *= (vel * (1 - np.exp(-t / 0.0015))).astype(np.float32)
    return y


def soundboard_ir(rng, length_s=0.3):
    n = int(length_s * SR)
    t = np.arange(n) / SR
    body = np.zeros(n)
    for _ in range(70):
        f = np.exp(rng.uniform(np.log(70), np.log(3500)))
        body += rng.uniform(0.2, 1) * np.exp(-t / (rng.uniform(0.015, 0.09) * (300 / f) ** 0.3)) * np.sin(2 * np.pi * f * t + rng.uniform(0, 6.28))
    body /= np.sqrt((body ** 2).sum())
    ir = 0.5 * body
    ir[0] += 1.0
    return ir.astype(np.float32)


def hall_ir(rng, rt=(5.0, 3.6, 2.2, 0.9), predelay=0.03):
    """Stereo hall: decorrelated noise in 4 bands, each with its own RT60 (lows longest), soft onset."""
    n = int((max(rt) + 0.3) * SR)
    t = np.arange(n) / SR
    edges = [(None, 250), (250, 1200), (1200, 4000), (4000, None)]
    ir = np.zeros((n, 2))
    for (lo, hi), r in zip(edges, rt):
        sos = butter(4, hi, "lowpass", fs=SR, output="sos") if lo is None else \
            butter(4, lo, "highpass", fs=SR, output="sos") if hi is None else \
            butter(4, (lo, hi), "bandpass", fs=SR, output="sos")
        for c in range(2):
            ir[:, c] += sosfilt(sos, rng.normal(0, 1, n)) * np.exp(-6.91 * t / r)
    ir *= (1 - np.exp(-t / 0.04))[:, None]  # diffuse build-up
    pd = int(predelay * SR)
    ir = np.vstack([np.zeros((pd, 2)), ir])
    return (ir / np.sqrt((ir ** 2).sum() / 2)).astype(np.float32)


def place(buf, y, t, pan=0.0):
    i = int(t * SR)
    m = min(len(y), len(buf) - i)
    if m > 0 and i >= 0:
        buf[i:i + m, 0] += y[:m] * np.sqrt(0.5 * (1 - pan))
        buf[i:i + m, 1] += y[:m] * np.sqrt(0.5 * (1 + pan))


def hz(root, semis, octave):
    return root * 2 ** (semis / 12) * 2 ** octave


TAIL = 10  # seconds of ring-out kept after a passage
VOICINGS = [[0, 7, 12], [0, 3, 10], [0, 5, 7], [-5, 0, 7], [0, 7, 10, 15], [-12, 0, 7]]


def cluster(root, rng, octave=-1, dur=9.0):
    """One pedalled low cluster as a stereo buffer (bass to the left, like sitting at the keys)."""
    v = VOICINGS[rng.integers(len(VOICINGS))]
    buf = np.zeros((int((dur + 3) * SR), 2), np.float32)
    vel = rng.uniform(0.45, 0.7)
    for j, s in enumerate(v):
        place(buf, note(hz(root, s, octave), dur, vel * rng.uniform(0.85, 1.05), rng), 0.035 * j * rng.uniform(0.6, 1.4),
              -0.4 + 0.8 * j / max(1, len(v) - 1))
    return buf


def low_pedal(root, dur, rng):
    """Low clusters with the sustain pedal down, struck rarely, into a big dark hall."""
    buf = np.zeros((int((dur + TAIL) * SR), 2), np.float32)
    t = 0.3
    while t < dur - 6:
        c = cluster(root, rng)
        place(buf, c[:, 0], t, -1)
        place(buf, c[:, 1], t, 1)
        t += rng.uniform(9, 14)
    return buf, dict(wet=0.55, lowpass=2400)


def reverse_swells(root, dur, rng):
    """Chords rendered into the hall, reversed: they swell up out of nothing and cut off."""
    buf = np.zeros((int((dur + TAIL) * SR), 2), np.float32)
    hall = hall_ir(rng, rt=(4.5, 3.4, 2.0, 0.8))
    t = 0.5
    while t < dur - 7:
        c = cluster(root, rng, octave=0, dur=4)
        wet = np.stack([fftconvolve(c[:, ch], hall[:, ch])[: int(6.5 * SR)] for ch in range(2)], axis=1)
        sw = (0.4 * np.vstack([c, np.zeros((max(0, len(wet) - len(c)), 2), np.float32)])[: len(wet)] + wet)[::-1]
        place(buf, sw[:, 0], t, -1)
        place(buf, sw[:, 1], t, 1)
        t += rng.uniform(8, 11)
    return buf, dict(wet=0.2, lowpass=4000)


def swell_strike(root, dur, rng):
    """A reversed cluster swells up and lands exactly on the same cluster struck for real, which rings out."""
    buf = np.zeros((int((dur + TAIL) * SR), 2), np.float32)
    hall = hall_ir(rng, rt=(4.5, 3.4, 2.0, 0.8))
    t = 0.5
    while t < dur - 12:
        state = rng.bit_generator.state
        c = cluster(root, rng, octave=-1, dur=4)
        wet = np.stack([fftconvolve(c[:, ch], hall[:, ch])[: int(5 * SR)] for ch in range(2)], axis=1)[::-1]
        rng.bit_generator.state = state  # same voicing and velocities for the real strike
        hit = cluster(root, rng, octave=-1, dur=9)
        place(buf, 0.8 * wet[:, 0], t, -1)
        place(buf, 0.8 * wet[:, 1], t, 1)
        place(buf, hit[:, 0], t + len(wet) / SR, -1)
        place(buf, hit[:, 1], t + len(wet) / SR, 1)
        t += len(wet) / SR + rng.uniform(9, 13)
    return buf, dict(wet=0.5, lowpass=2400)


# (style, context start in the program (s), root Hz of that section from drone.py, label)
EXAMPLES = [
    ("low_pedal", 600, 46.2, "Spivey, F#"),
    ("low_pedal", 1590, 58.3, "Tokyo Blues, Bb"),
    ("reverse_swells", 1100, 65.6, "How Can You Do It, C"),
    ("reverse_swells", 90, 54.4, "Son House, A -20c"),
    ("swell_strike", 750, 44.0, "My Buddy, F +12c"),
    ("swell_strike", 1640, 58.3, "Tokyo Blues, Bb"),
]


# Passages used in the program: example number -> dB relative to the example level (2 and 6 dropped).
PASSAGES = {1: -4.0, 3: 0.0, 4: -4.0, 5: -6.0}


def ping_pong(x, delay_s=0.42, fb=0.35, taps=3):
    """A little ping-pong delay: 3 darkened repeats alternating left and right."""
    y = x.copy()
    mono = x.mean(axis=1)
    lp = Pedalboard([LowpassFilter(2500)])
    for k in range(1, taps + 1):
        sh = int(k * delay_s * SR)
        tap = np.zeros(len(x), np.float32)
        tap[sh:] = mono[: len(x) - sh] * fb ** k
        y[:, (k + 1) % 2] += lp(tap[None, :], SR)[0]
    return y


def render_part(part, fx, board, hall, big=None, big_wet=0.4):
    rms = lambda x: float(np.sqrt((x.astype(np.float64) ** 2).mean()) + 1e-12)
    n = len(part)
    part = np.stack([fftconvolve(part[:, c], board)[:n] for c in range(2)], axis=1)
    wet = np.stack([fftconvolve(part[:, c], hall[:, c])[:n] for c in range(2)], axis=1)
    part = (np.sqrt(1 - fx["wet"]) * part + np.sqrt(fx["wet"]) * wet * rms(part) / rms(wet)).astype(np.float32)
    if big is not None:
        part = ping_pong(part)
        wet = np.stack([fftconvolve(part[:, c], big[:, c])[:n] for c in range(2)], axis=1)
        part = (np.sqrt(1 - big_wet) * part + np.sqrt(big_wet) * wet * rms(part) / rms(wet)).astype(np.float32)
    return Pedalboard([LowpassFilter(fx["lowpass"])])(part.T.copy(), SR).T


def program(args):
    """Lay the chosen passages into the whole program; the music narrows toward mono while they play."""
    mix = load(args.mix).astype(np.float32)
    rng = np.random.default_rng(args.seed)  # same draw order as `examples`, so the passages are the approved ones
    board, hall = soundboard_ir(rng), hall_ir(rng)
    big = hall_ir(np.random.default_rng(99), rt=(7.5, 5.5, 3.2, 1.2), predelay=0.06)
    rms = lambda x: float(np.sqrt((x.astype(np.float64) ** 2).mean()) + 1e-12)
    side = np.zeros(len(mix), np.float32)
    parts, lines = [], []
    d = int(args.dur * SR)
    for i, (style, start, root, label) in enumerate(EXAMPLES, 1):
        part, fx = globals()[style](root * 4, args.dur, rng)
        if i not in PASSAGES:
            continue
        part = render_part(part, fx, board, hall, big)
        s = int(start * SR)
        part[d - int(3 * SR):d] *= np.linspace(1, 0.35, int(3 * SR), dtype=np.float32)[:, None]  # ease into the tail
        part *= rms(mix[s:s + d]) / rms(part[:d]) * 10 ** ((args.db + PASSAGES[i]) / 20)
        parts.append((s, part))
        ramp = int(6 * SR)
        shape = np.concatenate([np.linspace(0, 1, ramp), np.ones(max(0, d - ramp)), np.linspace(1, 0, ramp)])
        e = min(len(side), s + len(shape))
        side[s:e] = np.maximum(side[s:e], shape[: e - s])
        lines.append(f"- {int(start // 60)}:{int(start % 60):02d} {style.replace('_', ' ')} over {label}, "
                     f"{args.db + PASSAGES[i]:+.0f} dB vs the music")
    g = 1 - args.narrow * side  # side gain: 1 -> 0.9 -> 1
    mid, sd = (mix[:, 0] + mix[:, 1]) / 2, (mix[:, 0] - mix[:, 1]) / 2 * g
    out = np.stack([mid + sd, mid - sd], axis=1)
    for s, part in parts:
        m = min(len(part), len(out) - s)
        out[s:s + m] += part[:m]
    peak = float(np.abs(out).max())
    if peak > 0.98:
        out *= 0.98 / peak
    save(args.out, out)
    Path(args.out).with_suffix(".piano.md").write_text(
        f"# Piano passages in {Path(args.out).name}\n\nMusic narrows {args.narrow:.0%} toward mono during each.\n\n"
        + "\n".join(lines) + "\n")
    print("\n".join(lines))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("cmd", choices=["examples", "program"])
    ap.add_argument("mix")
    ap.add_argument("--outdir", default="out/piano-examples")
    ap.add_argument("--dur", type=float, default=40)
    ap.add_argument("--db", type=float, default=-5.0, help="piano RMS relative to the excerpt")
    ap.add_argument("--seed", type=int, default=5)
    ap.add_argument("--narrow", type=float, default=0.1, help="program: how far the music moves toward mono")
    ap.add_argument("--out", help="program: output wav")
    args = ap.parse_args()
    if args.cmd == "program":
        return program(args)

    out = Path(args.outdir)
    out.mkdir(parents=True, exist_ok=True)
    mix = load(args.mix).astype(np.float32)
    rng = np.random.default_rng(args.seed)
    board = soundboard_ir(rng)
    hall = hall_ir(rng)
    rms = lambda x: float(np.sqrt((x.astype(np.float64) ** 2).mean()) + 1e-12)
    for i, (style, start, root, label) in enumerate(EXAMPLES, 1):
        part, fx = globals()[style](root * 4, args.dur, rng)  # root*4: drone octave -> piano middle octave
        n = int(args.dur * SR)
        part = render_part(part, fx, board, hall)[:n]
        ctx = mix[int(start * SR):int(start * SR) + n].copy()
        part *= rms(ctx) / rms(part) * 10 ** (args.db / 20)
        fade = np.linspace(0, 1, int(1.5 * SR), dtype=np.float32)[:, None]
        both = ctx + part
        both[: len(fade)] *= fade
        both[-len(fade):] *= fade[::-1]
        both /= max(1.0, float(np.abs(both).max()) / 0.95)
        solo = part / max(1e-9, float(np.abs(part).max()) / 0.9)
        name = f"{i}-{style.replace('_', '-')}-{label.split(',')[0].lower().replace(' ', '-')}"
        save(str(out / f"{name}.wav"), both)
        save(str(out / f"{name}-solo.wav"), solo)
        print(f"{name}: over {int(start // 60)}:{int(start % 60):02d} ({label}), root {root * 4:.1f} Hz")


if __name__ == "__main__":
    main()
