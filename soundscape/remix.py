#!/usr/bin/env python3
"""Beat-mapped house remix of a free-time 78.

  remix.py beats assets/tracks/victoria-spivey_no-papa-no-1928.mp3 --out out/remix/beatcheck.wav
  remix.py grid   assets/tracks/victoria-spivey_no-papa-no-1928.mp3 --out out/remix/no-papa-no-grid.wav
  remix.py midi   out/remix/no-papa-no-grid.bars.txt --out out/remix/no-papa-no
  remix.py build  assets/tracks/victoria-spivey_no-papa-no-1928.mp3 --out out/remix/no-papa-no-house.wav

beats: spectral-flux onsets -> tempo (95-135 BPM, prior ~118) -> dynamic-programming beat tracker that
       follows the drift -> bar phase from the piano's left hand. Writes the original with clicks on top
       (high click = bar start) so the map can be checked by ear.
build: warps every beat onto an exact grid (Rubber Band, stretch factor varying per beat, pitch kept),
       plays a minor deep-house loop in the record's key, then lays synthesized house drums, an
       offbeat bass and chord stabs around it: intro, the song, an 8-bar breakdown, outro. Kick sidechains
       bass, stabs and the record.
"""
import argparse
import subprocess
from pathlib import Path

import numpy as np
import pyloudnorm as pyln
from pedalboard import Compressor, Delay, HighpassFilter, LowpassFilter, Pedalboard, Reverb, time_stretch
from scipy.ndimage import minimum_filter1d, uniform_filter1d
from scipy.signal import butter, sosfilt, stft

from loop import SR, load, save
from render import guard, level

NAMES = ["C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B"]


def decode(path):
    if str(path).endswith(".wav"):
        return load(path).astype(np.float32)
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", str(path), "-ar", str(SR), "-ac", "2", "-f", "f32le", "-"],
                         capture_output=True, check=True).stdout
    return np.frombuffer(raw, np.float32).reshape(-1, 2).copy()


# ---------- beats ----------

def onset_env(mono, lo=60, hi=5000):
    x = mono[::2]
    f, t, Z = stft(x, SR // 2, nperseg=1024, noverlap=768)
    band = (f > lo) & (f < hi)
    mag = np.log1p(10 * np.abs(Z[band]))
    flux = np.maximum(np.diff(mag, axis=1), 0).sum(0)
    fr = 1 / (t[1] - t[0])
    flux = np.maximum(flux - uniform_filter1d(flux, int(fr)), 0)
    return flux / (flux.std() + 1e-9), fr, t[1:]


def estimate_period(flux, fr, lo=95, hi=135, prior=118):
    x = flux - flux.mean()
    ac = np.correlate(x, x, "full")[len(x) - 1:]
    lags = np.arange(int(fr * 60 / hi), int(fr * 60 / lo) + 1)
    w = np.exp(-0.5 * (np.log2(60 * fr / lags / prior) / 0.2) ** 2)
    sc = ac[lags] * w
    k = int(np.argmax(sc))
    if 0 < k < len(sc) - 1:  # parabolic refinement
        a, b, c = sc[k - 1:k + 2]
        return lags[k] + 0.5 * (a - c) / (a - 2 * b + c + 1e-12)
    return float(lags[k])


def track(flux, period, tight=60.0):
    n = len(flux)
    score = flux.astype(float).copy()
    back = -np.ones(n, int)
    for i in range(int(period / 2), n):
        prev = np.arange(max(0, int(i - 2 * period)), int(i - period / 2))
        if len(prev) == 0:
            continue
        cand = score[prev] - tight * np.log((i - prev) / period) ** 2
        k = int(np.argmax(cand))
        score[i] = flux[i] + cand[k]
        back[i] = prev[k]
    i = n - int(period) + int(np.argmax(score[-int(period):]))
    beats = [i]
    while back[i] >= 0:
        i = back[i]
        beats.append(i)
    return np.array(beats[::-1])


def find_beats(a):
    mono = a.mean(axis=1)
    flux, fr, times = onset_env(mono)
    period = estimate_period(flux, fr)
    beats = times[track(flux, period)]
    rms = np.sqrt(uniform_filter1d(mono ** 2, SR))  # drop beats in the needle noise before/after the music
    loud = np.where(rms > 0.1 * rms.max())[0]
    beats = beats[(beats >= loud[0] / SR - 0.3) & (beats <= loud[-1] / SR + 0.3)]
    low, _, ltimes = onset_env(mono, 50, 300)  # piano left hand marks the strong beats
    strength = np.interp(beats, ltimes, low)
    phase = int(np.argmax([strength[p::4].mean() for p in range(4)]))
    return beats, phase, 60 * fr / period


def click(freq, n=int(0.03 * SR)):
    t = np.arange(n) / SR
    return (np.sin(2 * np.pi * freq * t) * np.exp(-t / 0.006)).astype(np.float32)


def cmd_beats(args):
    a = decode(args.track)
    beats, phase, bpm = find_beats(a)
    out = a * 0.7
    for j, b in enumerate(beats):
        c = click(2000 if (j - phase) % 4 == 0 else 1000) * (0.5 if (j - phase) % 4 == 0 else 0.3)
        i = int(b * SR)
        out[i:i + len(c)] += c[:len(out) - i, None]
    save(args.out, out)
    ibi = np.diff(beats)
    local = 60 / uniform_filter1d(ibi, 8)
    print(f"{len(beats)} beats, start {beats[0]:.2f}s, tempo estimate {bpm:.1f} BPM, local tempo "
          f"{np.percentile(local, 5):.0f}-{np.percentile(local, 95):.0f} BPM, bar phase {phase}")
    return beats, phase


# ---------- synthesis ----------

def bp(x, lo, hi, order=2):
    return sosfilt(butter(order, (lo, hi), "bandpass", fs=SR, output="sos"), x).astype(np.float32)


def hp(x, f, order=2):
    return sosfilt(butter(order, f, "highpass", fs=SR, output="sos"), x).astype(np.float32)


def kick(tune_hz):
    t = np.arange(int(0.45 * SR)) / SR
    f = tune_hz + 115 * np.exp(-t / 0.032)
    y = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 0.3)
    y[:130] += np.random.default_rng(0).normal(0, 0.25, 130) * np.exp(-np.arange(130) / 25)
    return np.tanh(1.6 * y).astype(np.float32)


def clap(rng):
    t = np.arange(int(0.35 * SR)) / SR
    env = sum(np.exp(-np.maximum(t - d, 0) / 0.004) * (t >= d) for d in (0, 0.009, 0.018))
    env += 0.6 * np.exp(-np.maximum(t - 0.025, 0) / 0.1) * (t >= 0.025)
    return bp(rng.normal(0, 1, len(t)) * env, 900, 3200) * 0.8


def hat(rng, decay, cutoff=7500):
    t = np.arange(int((decay * 6) * SR)) / SR
    return hp(rng.normal(0, 1, len(t)) * np.exp(-t / decay), cutoff, 4) * 0.6


def saw_env(freq, t, bright, voices=(0.0,), hmax=40):
    y = np.zeros(len(t), np.float32)
    for det in voices:
        f = freq * 2 ** (det / 1200)
        ph = 2 * np.pi * f * t
        for h in range(1, hmax + 1):
            if h * f > 9000:
                break
            y += (np.sin(h * ph) / h * np.exp(-(h - 1) / bright)).astype(np.float32)
    return y / len(voices)


def bass_note(freq, dur):
    t = np.arange(int((dur + 0.05) * SR)) / SR
    bright = 1.0 + 5.0 * np.exp(-t / 0.05)
    y = 0.8 * np.sin(2 * np.pi * freq * t) + 0.5 * saw_env(freq, t, bright, hmax=16)
    env = (1 - np.exp(-t / 0.003)) * np.exp(-t / 0.28)
    env[int(dur * SR):] *= np.exp(-np.arange(len(t) - int(dur * SR)) / (0.01 * SR))
    return np.tanh(1.3 * y * env).astype(np.float32)


def stab(freqs):
    t = np.arange(int(0.7 * SR)) / SR
    bright = 1.5 + 9 * np.exp(-t / 0.06)
    y = sum(saw_env(f, t, bright, voices=(-8, 0, 8)) for f in freqs)
    return (y * (1 - np.exp(-t / 0.002)) * np.exp(-t / 0.22) / len(freqs)).astype(np.float32)


def riser(rng, dur):
    t = np.arange(int(dur * SR)) / SR
    n = rng.normal(0, 1, len(t)).astype(np.float32)
    out = np.zeros_like(n)
    for i, (lo, hi) in enumerate(zip(np.geomspace(300, 4000, 8), np.geomspace(1200, 12000, 8))):
        sl = slice(i * len(t) // 8, (i + 1) * len(t) // 8)
        out[sl] = bp(n, lo, hi)[sl]
    return out * (t / dur) ** 2


# ---------- build ----------

def grid_offsets(w, grid, span=8, search=0.08):
    """Where the warped record's beats really are vs the grid: best offset around each beat (over +-span beats)."""
    flux, _, times = onset_env(w.mean(axis=1))
    offs = np.arange(-search, search + 1e-9, 0.0025)
    strength = np.array([np.interp(grid + o, times, flux) for o in offs])  # (offsets, beats)
    sm = uniform_filter1d(strength, size=2 * span + 1, axis=1, mode="nearest")
    return offs[np.argmax(sm, axis=0)]


def warp_to_grid(a, bs, T, passes=2):
    """Stretch so each beat interval becomes one grid beat; then measure where beats landed and correct
    the per-beat stretch (Rubber Band's chunked rate changes drift a few ms per minute)."""
    base = np.diff(bs) / T
    corr = np.ones(len(base))
    for p in range(passes + 1):
        factor = np.full(len(a), float(np.median(base)))
        for (i0, i1), f in zip(zip(bs[:-1], bs[1:]), base * corr):
            factor[i0:i1] = f
        w = time_stretch(a.T.copy(), SR, stretch_factor=factor, pitch_shift_in_semitones=0.0,
                         transient_mode="mixed").T
        pre_out = bs[0] / factor[0]
        grid = pre_out / SR + np.arange(len(bs)) * T / SR
        off = grid_offsets(w, grid)
        if p == passes:
            break
        # offset slope per beat = how much each output beat is too long (+) or short (-)
        slope = np.gradient(uniform_filter1d(off, 9, mode="nearest"))[:-1]
        corr *= 1 + slope * SR / T  # beats landing early -> output too short -> stretch less
    pre_out = int(round(pre_out + off[0] * SR))  # a constant offset just moves the record
    return w, pre_out, [float(np.median(q) - off[0]) for q in np.array_split(off, 4)]


def limit(x, ceiling=0.89, window_s=0.03):
    """Lookahead peak limiter: the gain is the moving average of a windowed minimum of the needed gain,
    so it is never above what each sample needs, and it moves smoothly (no clipping, no distortion)."""
    need = np.minimum(1.0, ceiling / np.maximum(np.abs(x).max(axis=1), 1e-9))
    w = int(window_s * SR)
    g = uniform_filter1d(minimum_filter1d(need, size=w), size=w)
    return (x * g[:, None]).astype(np.float32)


# Deep-house loop that fits a minor-pentatonic blues melody (no major 3rd to fight the blue third):
# i (minor 11, rootless: b3 5 b7 11) for 2 bars, then iv (minor 9 over the 4th: b3 5 b7 9), bass on the roots.
PROGRESSION = [(0, (3, 7, 10, 17)), (0, (3, 7, 10, 17)), (5, (3, 7, 10, 14)), (5, (3, 7, 10, 14))]


def cmd_build(args):
    rng = np.random.default_rng(args.seed)
    a = decode(args.track)
    beats, phase, _ = find_beats(a)
    beats = beats[phase:]  # start on a bar line
    beat = 60 / args.bpm
    T = beat * SR

    bs = (beats * SR).astype(int)
    w, pre_out, drift = warp_to_grid(a, bs, T)
    print("grid offset per quarter after correction (ms): " + " ".join(f"{d * 1000:+.0f}" for d in drift))
    song_beats = len(bs) - 1
    song_bars = int(np.ceil(song_beats / 4))

    # key and tuning of the record, then a chord for every bar
    from drone import analyze
    wins = analyze(a.mean(axis=1))
    conf = [x for x in wins if x[4] > 0.5] or wins
    key_pc = int(np.bincount([x[1] for x in conf], minlength=12).argmax())
    tune = float(np.median([x[3] for x in conf]))
    bar = 4 * beat

    intro, outro = 16, 16
    total_bars = intro + song_bars + outro
    n = int((total_bars * bar + 4) * SR)
    song_start = intro * bar * SR - pre_out  # first downbeat of the record on bar `intro`
    bk0 = intro + (song_bars // 2 // 4) * 4  # 8-bar breakdown starting on a phrase near the middle
    bk1 = bk0 + 8

    root_hz = lambda off, octv: 32.703 * 2 ** ((key_pc + off) / 12 + tune / 1200) * 2 ** octv
    swing = (args.swing - 0.5) * 2 * beat / 4
    step_t = lambda b, s: (b * 16 + s) * beat / 4 + (swing if s % 2 else 0)

    kick_bus, perc, bass_bus, stab_bus = (np.zeros((n, 2), np.float32) for _ in range(4))
    k_one, cl = kick(root_hz(0, 0) if root_hz(0, 0) > 40 else root_hz(0, 1)), [clap(rng) for _ in range(4)]
    oh, ch = [hat(rng, 0.09) for _ in range(4)], [hat(rng, 0.025, 9000) for _ in range(6)]
    kicks = []

    def put(bus, y, t, gain=1.0, pan=0.0):
        i = int(t * SR)
        m = min(len(y), n - i)
        if m > 0:
            bus[i:i + m, 0] += gain * y[:m] * np.sqrt(0.5 * (1 - pan))
            bus[i:i + m, 1] += gain * y[:m] * np.sqrt(0.5 * (1 + pan))

    for b in range(total_bars):
        in_song = intro <= b < intro + song_bars
        brk = bk0 <= b < bk1
        off, voicing = PROGRESSION[b % len(PROGRESSION)]
        if brk:  # breakdown: hold the i chord, let it breathe
            off, voicing = 0, (3, 7, 10, 14)
        has_kick = not brk and not (b == total_bars - 1)
        has_clap = (8 <= b and not brk and b < total_bars - 8) or (b == bk1 - 1)
        has_oh = 4 <= b and not brk and b < total_bars - 4
        has_bass = 8 <= b and not brk and b < total_bars - 8
        has_stab = in_song or (12 <= b < intro) or (total_bars - 16 <= b < total_bars - 8)
        for s in range(16):
            t = step_t(b, s)
            if has_kick and s % 4 == 0:
                put(kick_bus, k_one, t, 1.0)
                kicks.append(t)
            if s % 4 == 2 and has_oh:
                put(perc, oh[rng.integers(4)], t, 0.22, 0.25)
            elif s % 4 != 2 and (b >= 0):
                put(perc, ch[rng.integers(6)], t, (0.12 if s % 4 == 0 else 0.07) * rng.uniform(0.8, 1.1), -0.3)
            if has_clap and s in (4, 12):
                put(perc, cl[rng.integers(4)], t, 0.5)
            if b == bk1 - 1 and s >= 8:  # clap roll into the drop
                put(perc, cl[rng.integers(4)], t, 0.15 + 0.35 * (s - 8) / 8)
            if has_bass and s in (2, 6, 10, 14):
                note_off = off + (7 if (b % 4 == 3 and s == 10) else 12 if (b % 8 == 7 and s == 14) else 0)
                put(bass_bus, bass_note(root_hz(note_off, 1), beat / 4 * 1.6), t, 0.6)
            if has_bass and s == 15 and b % 2 == 1:
                put(bass_bus, bass_note(root_hz(off + 10, 0 if off + 10 >= 12 else 1), beat / 4 * 0.9), t, 0.45)
            if has_stab and (s in (3, 10) or (s == 14 and b % 2 == 0)):
                fr = [root_hz(off + x, 3) for x in voicing]
                put(stab_bus, stab(fr), t, 0.3 if not brk else 0.4, rng.uniform(-0.3, 0.3))
    put(perc, riser(rng, 2 * bar), (bk1 - 2) * bar, 0.25)

    # the record: cleaned, a bit compressed, placed on the grid
    rec = np.zeros((n, 2), np.float32)
    s0 = int(song_start)
    m = min(len(w), n - s0)
    rec[s0:s0 + m] = w[:m]
    rec = Pedalboard([HighpassFilter(110), LowpassFilter(9500), Compressor(threshold_db=-22, ratio=2.5, attack_ms=10,
                                                                         release_ms=150)])(rec.T.copy(), SR).T

    # sidechain from the kick
    duck = np.zeros(n, np.float32)
    env = np.exp(-np.arange(int(0.3 * SR)) / (0.11 * SR)).astype(np.float32)
    for t in kicks:
        i = int(t * SR)
        mm = min(len(env), n - i)
        duck[i:i + mm] = np.maximum(duck[i:i + mm], env[:mm])
    sc = lambda depth: (1 - depth * duck)[:, None]

    stab_bus = Pedalboard([Delay(delay_seconds=0.75 * beat, feedback=0.3, mix=0.25), LowpassFilter(5000),
                           Reverb(room_size=0.6, wet_level=0.25, dry_level=0.8)])(stab_bus.T.copy(), SR).T
    perc = Pedalboard([Reverb(room_size=0.3, wet_level=0.12, dry_level=0.9)])(perc.T.copy(), SR).T

    rms = lambda x: float(np.sqrt((x.astype(np.float64) ** 2).mean()) + 1e-12)
    song_rng = slice(int(intro * bar * SR), int((intro + song_bars) * bar * SR))
    rec *= rms(kick_bus[song_rng]) / rms(rec[song_rng]) * 10 ** (args.record_db / 20)
    mix = kick_bus + perc + bass_bus * sc(0.75) + stab_bus * sc(0.5) + rec * sc(0.25)
    mix = Pedalboard([Compressor(threshold_db=-14, ratio=2, attack_ms=20, release_ms=200)])(mix.T.copy(), SR).T
    for _ in range(3):  # level, then limit; converges on the target (pedalboard's Limiter adds makeup gain)
        mix = limit(level(mix, args.lufs))
    mix = guard(mix)
    end = int((total_bars * bar + 2.5) * SR)
    mix = mix[:end]
    mix[-int(2 * SR):] *= np.linspace(1, 0, int(2 * SR))[:, None]
    save(args.out, mix)

    stamp = lambda bb: f"{int(bb * bar // 60)}:{int(bb * bar % 60):02d}"
    lines = [f"# {Path(args.out).stem}", "",
             f"{args.bpm} BPM, key {NAMES[key_pc]} ({tune:+.0f} cents), swing {args.swing:.2f}, "
             f"{total_bars} bars, {int(len(mix) / SR // 60)}:{len(mix) / SR % 60:04.1f}", "",
             f"- 0:00 intro: kick + hats, open hats from {stamp(4)}, clap and bass from {stamp(8)}, stabs from {stamp(12)}",
             f"- {stamp(intro)} the record comes in on the grid",
             f"- {stamp(bk0)}-{stamp(bk1)} breakdown: no kick/bass, stabs and the record, clap roll + riser into the drop",
             f"- {stamp(intro + song_bars)} outro: drums and bass, stripping back from {stamp(total_bars - 8)}", "",
             "Harmony: i-minor 11 (2 bars), iv-minor 9 (2 bars), bass on the roots; breakdown holds the i chord."]
    Path(args.out).with_suffix(".md").write_text("\n".join(lines) + "\n")
    print("\n".join(lines))
    print(f"loudness {pyln.Meter(SR).integrated_loudness(mix):.1f} LUFS, peak {np.abs(mix).max():.2f}")


def cmd_grid(args):
    """The record alone on a steady grid for sampling: one bar of count-in clicks, then the record with its
    first downbeat on bar 2, nothing added. Writes Audacity/DAW labels for every bar next to it."""
    a = decode(args.track)
    beats, phase, _ = find_beats(a)
    beats = beats[phase:]
    bpm = args.bpm or round(float(60 / np.median(np.diff(beats))))
    T = 60 / bpm * SR
    w, pre_out, drift = warp_to_grid(a, (beats * SR).astype(int), T)
    bar = 4 * T
    lead = int(np.ceil(pre_out / bar)) + 1  # count-in bar(s); the pickup fits before the first downbeat
    down = int(lead * bar)
    out = np.zeros((down - pre_out + len(w), 2), np.float32)
    out[down - pre_out:] = w
    for k in range(4):  # count-in on the bar just before the record's first downbeat
        c = click(1600 if k == 0 else 1000) * (0.5 if k == 0 else 0.35)
        i = int(down - bar + k * T)
        out[i:i + len(c)] += c[:, None]
    save(args.out, out)
    n_bars = int((len(out) - down) / bar) + 1
    labels = [f"{(down - bar) / SR:.6f}\t{(down - bar) / SR:.6f}\tcount-in"]
    labels += [f"{(down + b * bar) / SR:.6f}\t{(down + b * bar) / SR:.6f}\tbar {b + 1}" for b in range(n_bars)]
    Path(args.out).with_suffix(".bars.txt").write_text("\n".join(labels) + "\n")
    print(f"{bpm} BPM (record's own median tempo)" if not args.bpm else f"{bpm} BPM")
    print(f"record's bar 1 at {down / SR:.3f}s, {n_bars} bars, length {len(out) / SR:.1f}s, "
          f"grid offset per quarter (ms): " + " ".join(f"{d * 1000:+.0f}" for d in drift))


# ---------- MIDI ----------
# Ambient harmony built only from A minor pentatonic notes (A C D E G), so a pad can never fight the
# record's blue third or melody: 8-bar cycle of Am11 (4 bars), D9sus4 (2), C6/9 (2); last 4 bars on Am11.
PAD_CHORDS = {"Am11": [45, 52, 55, 60, 62], "D9sus4": [50, 57, 60, 64, 67], "C6/9": [48, 55, 57, 62, 64]}
BASS_ROOT = {"Am11": 33, "D9sus4": 38, "C6/9": 36}
CYCLE = ["Am11"] * 4 + ["D9sus4"] * 2 + ["C6/9"] * 2


def smf(tracks, bpm, ppq=480):
    """Minimal Standard MIDI File (format 1). tracks: list of (name, channel, [(start_tick, dur_tick, note, vel)])."""
    def vlq(n):
        out = [n & 0x7F]
        while n > 0x7F:
            n >>= 7
            out.insert(0, (n & 0x7F) | 0x80)
        return bytes(out)

    def chunk(kind, data):
        return kind + len(data).to_bytes(4, "big") + data

    us = int(round(60_000_000 / bpm))
    tempo = b"\x00\xff\x51\x03" + us.to_bytes(3, "big") + b"\x00\xff\x58\x04\x04\x02\x18\x08" + b"\x00\xff\x2f\x00"
    body = [chunk(b"MTrk", tempo)]
    for name, ch, notes in tracks:
        ev = []
        for start, dur, note, vel in notes:
            ev.append((start + dur, 0, bytes([0x80 | ch, note, 0])))  # offs before ons at the same tick
            ev.append((start, 1, bytes([0x90 | ch, note, vel])))
        ev.sort(key=lambda e: (e[0], e[1]))
        data, now = bytearray(b"\x00\xff\x03" + vlq(len(name)) + name.encode()), 0
        for tick, _, msg in ev:
            data += vlq(tick - now) + msg
            now = tick
        data += b"\x00\xff\x2f\x00"
        body.append(chunk(b"MTrk", bytes(data)))
    header = chunk(b"MThd", (1).to_bytes(2, "big") + len(body).to_bytes(2, "big") + ppq.to_bytes(2, "big"))
    return header + b"".join(body)


def cmd_midi(args):
    """Pads and bass MIDI lined up with a grid export, read from its .bars.txt (tempo, count-in, bar count)."""
    rows = [l.split("\t") for l in Path(args.track).read_text().splitlines() if "bar " in l]
    t = np.array([float(r[0]) for r in rows])
    bar_s = float(np.median(np.diff(t)))
    bpm = round(240 / bar_s, 3)
    lead = int(round(t[0] / bar_s))  # bars before the record's bar 1 (silence + count-in)
    n_bars = len(t)  # includes the record's last, partial bar
    ppq, bar = 480, 1920
    names = [CYCLE[b % len(CYCLE)] for b in range(n_bars)]
    names[-4:] = ["Am11"] * 4
    segs = []  # merge repeated chords into one held chord
    for b, nm in enumerate(names):
        if segs and segs[-1][0] == nm:
            segs[-1][2] += 1
        else:
            segs.append([nm, b, 1])
    pads, bass = [], []
    for nm, b0, length in segs:
        start, dur = (lead + b0) * bar, length * bar
        pads += [(start, dur - 10, n, 64 + 4 * (i % 2)) for i, n in enumerate(PAD_CHORDS[nm])]
        for k in range(0, length, 2):  # bass: root, re-struck softly every 2 bars
            bass.append((start + k * bar, min(2, length - k) * bar - 10, BASS_ROOT[nm], 84 if k == 0 else 70))
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    for suffix, tracks in (("pads", [("Pads", 0, pads)]), ("bass", [("Bass", 1, bass)]),
                           ("pads-bass", [("Pads", 0, pads), ("Bass", 1, bass)])):
        Path(f"{out}-{suffix}.mid").write_bytes(smf(tracks, bpm))
    print(f"{bpm} BPM, {lead} bars lead-in, {n_bars} bars; chords: "
          + " | ".join(f"{nm} x{length}" for nm, _, length in segs[:4]) + " ... ends on Am11")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("cmd", choices=["beats", "build", "grid", "midi"])
    ap.add_argument("track")
    ap.add_argument("--bpm", type=float, default=None, help="build: default 122; grid: default the record's own tempo")
    ap.add_argument("--swing", type=float, default=0.56, help="16th swing: 0.5 straight, 0.6 heavy")
    ap.add_argument("--record-db", type=float, default=-1.0, help="the record vs the kick bus, in the song")
    ap.add_argument("--lufs", type=float, default=-11.0)
    ap.add_argument("--seed", type=int, default=4)
    ap.add_argument("--out", required=True)
    args = ap.parse_args()
    Path(args.out).parent.mkdir(parents=True, exist_ok=True)
    if args.cmd == "build" and not args.bpm:
        args.bpm = 122.0
    {"beats": cmd_beats, "build": cmd_build, "grid": cmd_grid, "midi": cmd_midi}[args.cmd](args)


if __name__ == "__main__":
    main()
