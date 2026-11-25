#!/usr/bin/env python3
"""Post-process a finished mix (no re-render): voice inserts, grunge sections, repeat sprinkles.

  post.py out/program-blues-v2.wav --inserts a.mp3 b.mp3 c.mp3 --out out/program-blues-v3.wav

Event times come from the mix's <name>.tracklist.md (written by mix.py):
- inserts land just before the 'breathe' transitions (then later entries if more inserts than breathes),
  warmed up (band-limit, saturation, chorus, small Supermassive room) and with the music ducked under them;
- grunge (distortion, phaser, filtering) fades in and out over about a minute inside guitar-heavy tracks;
- short repeat/stutter events with a delay tail are sprinkled where nothing else is happening.
The same --seed gives the same event plan, so two mixtapes with the same tracklist get the same treatment.
"""
import argparse
import re
from pathlib import Path

import numpy as np
from pedalboard import (Bitcrush, Chorus, Compressor, Delay, Distortion, HighpassFilter, LowpassFilter, Pedalboard,
                        PeakFilter, Phaser, load_plugin)

from scipy.signal import resample

from loop import SR, load, save
from render import VST3, guard, level, set_raw

GRUNGE_TRACKS = ("burning-hell", "how-can-you-do-it", "tokyo-blues")


def read_tracklist(path):
    rows = []
    for line in Path(path).read_text().splitlines():
        m = re.match(r"\d+\. (\d+):(\d\d) (\S+) \((\w+)\)", line)
        if m:
            rows.append((int(m[1]) * 60 + int(m[2]), m[3], m[4]))
    return rows


def glitch_voice(v, rng, count):
    """Stutter word fragments: a 70-200 ms slice repeats 2-5 times (some bit-crushed), then speech continues."""
    if count <= 0:
        return v
    env = np.sqrt(np.convolve((v.mean(1) ** 2), np.ones(int(0.05 * SR)) / int(0.05 * SR), "same"))
    voiced = np.where(env > env.max() * 0.25)[0]  # glitch where there is speech, not in pauses
    lo, hi = int(0.5 * SR), len(v) - int(0.5 * SR)
    voiced = voiced[(voiced > lo) & (voiced < hi)]
    if len(voiced) == 0:
        return v
    points = sorted(rng.choice(voiced, size=min(count, len(voiced)), replace=False))
    crush = Bitcrush(bit_depth=6)
    out, prev = [], 0
    for p in points:
        if p - prev < int(0.6 * SR):  # keep glitches apart
            continue
        n = int(rng.uniform(0.07, 0.2) * SR)
        piece = v[p:p + n].copy()
        ramp = min(int(0.004 * SR), n // 4)  # tiny fades so each repeat does not click
        piece[:ramp] *= np.linspace(0, 1, ramp)[:, None]
        piece[-ramp:] *= np.linspace(1, 0, ramp)[:, None]
        if rng.random() < 0.4:
            piece = crush(piece.T.astype(np.float32), SR).T
        reps = [piece * (0.9 ** k) for k in range(int(rng.integers(2, 6)))]
        out += [v[prev:p]] + reps
        prev = p
    out.append(v[prev:])
    return np.concatenate(out).astype(np.float32)


def warm_voice(path, lufs=-17.0, glitches=0, rng=None):
    v = level(load(path), -17.0)
    chain = Pedalboard([HighpassFilter(110), LowpassFilter(7000), Distortion(drive_db=6),
                        Chorus(rate_hz=0.6, depth=0.15, mix=0.2), Compressor(threshold_db=-20, ratio=3),
                        PeakFilter(cutoff_frequency_hz=3000, gain_db=3.0, q=0.9)])  # presence keeps words clear when low
    v = chain(v.T.astype(np.float32), SR).T
    v = glitch_voice(v, rng or np.random.default_rng(0), glitches)
    if glitches:
        v = np.concatenate([v, np.zeros((int(1.5 * SR), 2), np.float32)])
        v = Delay(delay_seconds=0.28, feedback=0.3, mix=0.22)(v.T.astype(np.float32), SR).T  # soft echo throw
    room = load_plugin(VST3 + "ValhallaSupermassive.vst3")
    set_raw(room, mix=0.16, feedback=0.3, density=0.0, width=0.8)
    v = np.concatenate([v, np.zeros((int(2.5 * SR), 2), np.float32)])  # room tail
    v = room.process(v.T.astype(np.float32), SR).T
    return level(v, lufs).astype(np.float32)


def envelope(n, fade_in, fade_out, peak=1.0):
    e = np.full(n, peak, np.float32)
    a, b = int(fade_in * SR), int(fade_out * SR)
    e[:a] = peak * np.sin(np.linspace(0, np.pi / 2, a)) ** 2
    e[n - b:] = peak * np.cos(np.linspace(0, np.pi / 2, b)) ** 2
    return e[:, None]


def place_inserts(mix, voices, at_seconds, duck_db=-9.0):
    for v, t in zip(voices, at_seconds):
        s = int(t * SR)
        e = min(len(mix), s + len(v))
        n = e - s
        duck = 1 - envelope(n + int(2.0 * SR), 0.5, 1.5, peak=1 - 10 ** (duck_db / 20))  # music dips under the voice
        mix[s:s + len(duck)] *= duck[: len(mix) - s]
        mix[s:e] += v[:n]


def grunge(mix, start_s, length_s=60.0, wet=0.65):
    s, n = int(start_s * SR), int(length_s * SR)
    seg = mix[s:s + n]
    chain = Pedalboard([HighpassFilter(90), Distortion(drive_db=20), Phaser(rate_hz=0.35, depth=0.6, mix=0.35),
                        LowpassFilter(5200)])
    g = chain(seg.T.astype(np.float32), SR).T
    g *= np.sqrt((seg ** 2).mean() / ((g ** 2).mean() + 1e-12))  # same energy as the dry part
    w = envelope(len(seg), 15.0, 15.0, peak=wet)
    mix[s:s + n] = seg * (1 - w) + g * w


def repeat(mix, start_s, rng, length_s=3.0, wet=0.6):
    s, n = int(start_s * SR), int(length_s * SR)
    slice_len = int(rng.choice([0.125, 0.1875, 0.25]) * SR)
    piece = mix[s:s + slice_len].copy()
    reps = int(np.ceil(n / slice_len))
    st = np.concatenate([piece * (0.88 ** k) for k in range(reps)])[:n]
    tail = int(2.0 * SR)
    st = np.concatenate([st, np.zeros((tail, 2), np.float32)])
    st = Delay(delay_seconds=slice_len / SR * 1.5, feedback=0.35, mix=0.35)(st.T.astype(np.float32), SR).T
    w_body = envelope(n, 0.03, 0.4, peak=wet)
    mix[s:s + n] = mix[s:s + n] * (1 - w_body) + st[:n] * w_body
    mix[s + n:s + n + tail] += st[n:] * envelope(tail, 0.01, 1.8, peak=0.5)  # delay tail rings over the music


def plan(rows, total_s, n_inserts, n_repeats, rng, insert_targets=None, end_s=None):
    starts = [r[0] for r in rows] + [total_s]
    grunges = []
    for i, (t, name, _) in enumerate(rows):
        if any(k in name for k in GRUNGE_TRACKS):
            dur = starts[i + 1] - t
            grunges.append(t + max(30.0, dur * 0.35))
    if insert_targets:  # requested times, nudged just past any grunge section
        inserts = []
        for t in insert_targets[:n_inserts]:
            for g in grunges:
                if g - 3 <= t <= g + 63:
                    t = g + 63
            inserts.append(float(t))
    else:  # default: just before 'breathe' transitions
        entries = [(rows[i][0], rows[i][2]) for i in range(1, len(rows))]
        breathe = [t for t, style in entries if style == "breathe"]
        others = [t for t, style in entries if style != "breathe"]
        inserts = [t - 6.0 for t in sorted((breathe + others[::-1])[:n_inserts])]
    busy = [(t - 3, t + 22) for t in inserts] + [(t - 3, t + 63) for t in grunges]
    last = (end_s or total_s) - 40
    repeats, tries = [], 0
    while len(repeats) < n_repeats and tries < 5000:
        tries += 1
        t = float(rng.uniform(40, last))
        if any(a <= t <= b for a, b in busy) or any(abs(t - r) < 90 for r in repeats):
            continue
        repeats.append(t)
    return inserts, grunges, sorted(repeats)


def stereo_collapse(mix, start_s, length_s=50.0, depth=0.9):
    """Narrow towards mono and widen again (mid/side), over the given window."""
    s, n = int(start_s * SR), int(length_s * SR)
    seg = mix[s:s + n]
    mid, side = (seg[:, 0] + seg[:, 1]) / 2, (seg[:, 0] - seg[:, 1]) / 2
    side = side * (1 - envelope(len(seg), 15.0, 15.0, peak=depth)[:, 0])
    mix[s:s + n, 0], mix[s:s + n, 1] = mid + side, mid - side


def xfade_into(mix, s, new, ramp_s=0.015):
    """Replace mix[s:s+len(new)] with new, with short crossfades at both edges so nothing clicks."""
    n, r = len(new), int(ramp_s * SR)
    w = np.ones(n, np.float32)
    w[:r] = np.linspace(0, 1, r)
    w[-r:] = np.linspace(1, 0, r)
    mix[s:s + n] = mix[s:s + n] * (1 - w[:, None]) + new * w[:, None]


def half_speed(mix, start_s, length_s=4.0):
    """Play the next length/2 seconds at half speed (an octave down), then snap back to the timeline."""
    s, n = int(start_s * SR), int(length_s * SR)
    src = mix[s:s + n // 2]
    slow = resample(src, n, axis=0).astype(np.float32)
    xfade_into(mix, s, slow)


def underwater(mix, start_s, length_s=10.0, floor_hz=380.0):
    """Low-pass sweep down to floor_hz and back up, smoothly, block by block."""
    s, n = int(start_s * SR), int(length_s * SR)
    lp = LowpassFilter(cutoff_frequency_hz=18000)
    blk = 1024
    for b in range(0, n, blk):
        x = b / n
        depth = np.sin(np.pi * x) ** 2  # 0 -> 1 -> 0
        lp.cutoff_frequency_hz = float(np.exp(np.log(18000) + depth * (np.log(floor_hz) - np.log(18000))))
        seg = mix[s + b:s + min(n, b + blk)]
        mix[s + b:s + b + len(seg)] = lp.process(seg.T.astype(np.float32), SR, reset=False).T


def tape_stop(mix, start_s, stop_s=1.6, gap_s=0.6, resume_s=0.25):
    """Slow to a halt (pitch drops with speed), a short silence, then the program resumes on its timeline."""
    s, n = int(start_s * SR), int(stop_s * SR)
    tau = np.arange(n) / n
    rate = (1 - tau) ** 1.5
    pos = np.cumsum(rate)  # read position in samples, slows to a stop
    src = mix[s:s + int(pos[-1]) + 2].copy()
    idx = np.arange(len(src))
    stopped = np.stack([np.interp(pos, idx, src[:, c]) for c in (0, 1)], axis=1).astype(np.float32)
    stopped *= np.linspace(1, 0.6, n)[:, None]
    xfade_into(mix, s, stopped)
    g = int(gap_s * SR)
    mix[s + n:s + n + g] = 0
    r = int(resume_s * SR)
    mix[s + n + g:s + n + g + r] *= np.linspace(0, 1, r)[:, None]


def pick_times(count, length_s, busy, lo, hi, rng, spacing=60.0):
    times, tries = [], 0
    while len(times) < count and tries < 5000:
        tries += 1
        t = float(rng.uniform(lo, hi))
        if any(a - length_s <= t <= b for a, b in busy) or any(abs(t - x) < spacing for x in times):
            continue
        times.append(t)
        busy.append((t - 3, t + length_s + 3))
    return sorted(times)


def outro(mix, start_s, bed_path, ref_rms, fade_s=20.0, hold_s=35.0, out_s=15.0):
    """Fade the program out from start_s while another bed fades in, holds alone, then fades out."""
    s, f = int(start_s * SR), int(fade_s * SR)
    total = s + f + int((hold_s + out_s) * SR)
    out = np.zeros((total, 2), np.float32)
    keep = min(len(mix), s + f)
    out[:keep] = mix[:keep]
    out[s:s + f] *= np.cos(np.linspace(0, np.pi / 2, f))[:, None] ** 2
    bed = load(bed_path)
    n = total - s
    bed = bed[np.arange(n) % len(bed)]
    bed *= ref_rms / (np.sqrt((bed ** 2).mean()) + 1e-12)
    out[s:] += bed * envelope(n, fade_s, out_s)
    return out


def stamp(t):
    return f"{int(t // 60)}:{int(t % 60):02d}"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("mix")
    ap.add_argument("--inserts", nargs="*", default=[])
    ap.add_argument("--insert-at", type=float, nargs="*", help="target insert times in seconds (default: before breathe transitions)")
    ap.add_argument("--voice-lufs", type=float, default=-17.0, help="lower = further in the background")
    ap.add_argument("--duck-db", type=float, default=-9.0)
    ap.add_argument("--voice-glitches", type=int, default=0, help="stutter glitches per insert (plus a soft echo)")
    ap.add_argument("--outro-bed", help="loop that takes over after the last grunge/insert; the program fades out under it")
    ap.add_argument("--outro-db", type=float, default=2.0, help="outro bed level relative to the program's bed")
    ap.add_argument("--end-at", type=float, help="total length in seconds; the outro bed holds until then")
    ap.add_argument("--repeats", type=int, default=6)
    ap.add_argument("--collapse", type=int, default=0, help="stereo collapses, riding on the first N grunge sections")
    ap.add_argument("--collapse-depth", type=float, default=0.9, help="0 = no narrowing, 1 = full mono at the peak")
    ap.add_argument("--half-speed", type=int, default=0)
    ap.add_argument("--half-speed-long", type=int, default=0, help="extra half-speed drops of 8 s")
    ap.add_argument("--outro-at-end", action="store_true", help="let the last track play out; the outro bed enters under its tail")
    ap.add_argument("--outro-hold", type=float, default=35.0, help="seconds the outro bed holds alone")
    ap.add_argument("--underwater", type=int, default=0)
    ap.add_argument("--tape-stops", type=int, default=0, help="placed at the end of a song, before a 'stream' entry")
    ap.add_argument("--seed", type=int, default=11)
    ap.add_argument("--out", required=True)
    args = ap.parse_args()
    rng = np.random.default_rng(args.seed)

    rows = read_tracklist(Path(args.mix).with_suffix(".tracklist.md"))
    mix = load(args.mix).astype(np.float32)
    total_s = len(mix) / SR
    vrng = np.random.default_rng(args.seed + 100)
    voices = [warm_voice(p, args.voice_lufs, args.voice_glitches, vrng) for p in args.inserts]
    end_s = None
    if args.outro_bed:  # first pass to find where the last grunge/insert ends (also bounds event planning)
        ins0, gr0, _ = plan(rows, total_s, len(voices), 0, np.random.default_rng(0), args.insert_at)
        ends = [g + 60 for g in gr0] + [t + len(v) / SR for t, v in zip(ins0, voices)]
        end_s = max(ends) + 5.0
    # the last stem's reverb tail (14 s) plus mix.py's 4 s pad: the music itself ends here
    outro_s = (total_s - 18.0) if args.outro_at_end else end_s
    inserts, grunges, repeats = plan(rows, total_s, len(voices), args.repeats, rng, args.insert_at, end_s)

    # extra effects, sparse: never on top of inserts, grunge, repeats or each other
    busy = [(t - 3, t + 25) for t in inserts] + [(g - 3, g + 63) for g in grunges] + [(t - 3, t + 6) for t in repeats]
    hi = (end_s or total_s) - 30
    entries = [r[0] for r in rows[1:] if r[2] == "stream"]
    stops = []
    for t in rng.permutation(entries):
        t = float(t) - 8.0  # the song before the entry halts, then the next one comes in
        if len(stops) < args.tape_stops and not any(a - 3 <= t <= b for a, b in busy):
            stops.append(t)
            busy.append((t - 3, t + 6))
    stops.sort()
    halves = pick_times(args.half_speed, 4.0, busy, 60, hi, rng)
    waters = pick_times(args.underwater, 10.0, busy, 60, hi, rng)
    longs = pick_times(args.half_speed_long, 8.0, busy, 60, hi, rng)
    collapses = [g + 5 for g in grunges[:args.collapse]]

    bed_ref = float(np.sqrt((mix[int(4.0 * SR):int(5.9 * SR)] ** 2).mean()))  # lead-in: the program's bed alone
    for t in grunges:
        grunge(mix, t)
    for t in collapses:
        stereo_collapse(mix, t, depth=args.collapse_depth)
    for t in repeats:
        repeat(mix, t, rng)
    for t in halves:
        half_speed(mix, t)
    for t in longs:
        half_speed(mix, t, 8.0)
    for t in waters:
        underwater(mix, t)
    for t in stops:
        tape_stop(mix, t)
    place_inserts(mix, voices, inserts, args.duck_db)
    if args.outro_bed:
        hold = args.outro_hold if args.end_at is None else max(5.0, args.end_at - outro_s - 20.0 - 15.0)
        mix = outro(mix, outro_s, args.outro_bed, bed_ref * 10 ** (args.outro_db / 20), hold_s=hold)

    save(args.out, guard(mix))
    lines = [f"# {Path(args.out).stem}", "", f"Post-processed from {Path(args.mix).name} (seed {args.seed}), "
             f"length {stamp(len(mix) / SR)}", ""]
    lines += [f"- {stamp(t)} insert: {Path(p).stem}" for t, p in zip(inserts, args.inserts)]
    lines += [f"- {stamp(t)}-{stamp(t + 60)} grunge" for t in grunges]
    lines += [f"- {stamp(t)} repeat (3 s)" for t in repeats]
    lines += [f"- {stamp(t)}-{stamp(t + 50)} stereo collapse" for t in collapses]
    lines += [f"- {stamp(t)} half-speed drop (4 s)" for t in halves]
    lines += [f"- {stamp(t)} half-speed drop (8 s)" for t in longs]
    lines += [f"- {stamp(t)} underwater sweep (10 s)" for t in waters]
    lines += [f"- {stamp(t)} tape stop" for t in stops]
    if args.outro_bed:
        lines += [f"- {stamp(outro_s)} outro: program fades out over 20 s, {Path(args.outro_bed).stem} takes over, ends {stamp(len(mix) / SR)}"]
    Path(args.out).with_suffix(".events.md").write_text("\n".join(lines) + "\n")
    print("\n".join(lines[4:]))


if __name__ == "__main__":
    main()
