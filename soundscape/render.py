#!/usr/bin/env python3
"""Render one track over a looped bed through an automated plugin chain (static, offline).

  render.py assets/tracks/victoria-spivey_no-papa-no-1928.mp3 --semitones -3 \
      --beds assets/loops/user-e6f34d46.wav assets/loops/user-6b78c2c5.wav --out out/spivey.wav

Chain: varispeed (lower pitch and slower, like a slowed record) -> iZotope Vinyl (AU) ->
Valhalla SpaceModulator -> Valhalla Supermassive.
Each wet/mix control wanders slowly between --wet-lo and --wet-hi, so the effect breathes.
"""
import argparse
import json
from pathlib import Path

import numpy as np
import pyloudnorm as pyln
from pedalboard import Compressor, load_plugin
from scipy.signal import resample

from loop import SR, drift, load, normalize, save, tile

VST3 = "/Library/Audio/Plug-Ins/VST3/"
AU = "/Library/Audio/Plug-Ins/Components/"
BLOCK = 8192  # samples between parameter updates (about 0.19 s)
VINYL_EVERY = 20  # blocks between Vinyl parameter updates (about 3.7 s)
MOD_SCALE = 0.18  # SpaceModulator wet relative to the reverb's wet range (30% of the earlier 0.6)
TRACK_LUFS = -20.0
FINAL_LUFS = -18.0


def wander(n_blocks, lo, hi, rng, periods=(37, 91, 173)):
    """Slow random curve that spans lo..hi, built from a few incommensurate sines."""
    t = np.arange(n_blocks) * BLOCK / SR
    x = sum(np.sin(2 * np.pi * t / p + rng.uniform(0, 6.28)) * rng.uniform(0.5, 1) for p in periods)
    x = (x - x.min()) / (x.max() - x.min() + 1e-9)
    return lo + (hi - lo) * x


def varispeed(a, semitones):
    """Resample so pitch and tempo drop together (negative semitones = lower and slower)."""
    if semitones == 0:
        return a
    factor = 2 ** (-semitones / 12)
    return resample(a, int(len(a) * factor), axis=0)


def set_raw(plugin, **values):
    for k, v in values.items():
        plugin.parameters[k].raw_value = float(v)


def process_chain(x, rng, wet_lo, wet_hi):
    vinyl = load_plugin(AU + "iZVinylAUHook.component")
    mod = load_plugin(VST3 + "ValhallaSpaceModulator.vst3")
    # Valhalla FreqEcho is left out: both its VST3 and AU builds intermittently output NaN under Pedalboard.
    verb = load_plugin(VST3 + "ValhallaSupermassive.vst3")
    # scratch stays at 0: Vinyl's scratch model punches 20-40 ms dropouts into the audio
    set_raw(vinyl, dust_amount=0.4, dust_gain=0.4, scratch_gain=0.0, scratch_amount=0.0,
            mech_noise=0.2, year=0.2, mono=0.0)
    set_raw(mod, rate=0.3, depth=0.55, feedback=0.3, manual=0.5)
    set_raw(verb, feedback=0.55, density=0.0, width=1.0, moddepth=0.5)

    nb = (len(x) + BLOCK - 1) // BLOCK
    auto = {
        "warp": wander(nb, 0.15, 0.4, rng),
        "wear": wander(nb, 0.2, 0.5, rng),
        "mod": wander(nb, wet_lo * MOD_SCALE, wet_hi * MOD_SCALE, rng),  # modulation sits well under the reverb
        "verb": wander(nb, wet_lo, wet_hi, rng),
        "verb_fb": wander(nb, 0.4, 0.58, rng),
    }
    out = np.zeros_like(x)
    for b in range(nb):
        s, e = b * BLOCK, min(len(x), (b + 1) * BLOCK)
        if b % VINYL_EVERY == 0:  # Vinyl AU stalls ~0.3 s per parameter change; its curves are slow anyway
            set_raw(vinyl, warp_depth=auto["warp"][b], wear=auto["wear"][b])
        set_raw(mod, wetdry=auto["mod"][b])
        set_raw(verb, mix=auto["verb"][b], feedback=auto["verb_fb"][b])
        blk = x[s:e].T.astype(np.float32)
        for name, p in (("vinyl", vinyl), ("mod", mod), ("verb", verb)):
            blk = p.process(blk, SR, reset=False)
            if not np.isfinite(blk).all():
                raise RuntimeError(f"{name} produced non-finite samples at {s / SR:.1f}s")
        out[s:e] = blk.T
    return out


def compress(a):
    """Gentle glue compression on the processed track (Pedalboard built-in; Solid Bus Comp will not load)."""
    a = level(a, -20.0)  # fixed input level so the threshold means the same for every track
    return Compressor(threshold_db=-26.0, ratio=2.5, attack_ms=25.0, release_ms=250.0)(a.T.astype(np.float32), SR).T


def guard(a, ceiling=0.89):
    """Peak guard at -1 dBFS. Pedalboard's Limiter adds makeup gain, so scale instead."""
    peak = np.abs(a).max()
    return a * (ceiling / peak) if peak > ceiling else a


def level(a, lufs):
    return a * 10 ** ((lufs - pyln.Meter(SR).integrated_loudness(a)) / 20)


def fade(a, start_s, end_s, in_s=0.0, out_s=0.0):
    a = a.copy()
    if in_s:
        n = int(in_s * SR)
        a[:n] *= np.sin(np.linspace(0, np.pi / 2, n))[:, None]
    if out_s:
        n = int(out_s * SR)
        a[-n:] *= np.cos(np.linspace(0, np.pi / 2, n))[:, None]
    return a


def credits(track, beds, args):
    items = json.load(open(Path(__file__).with_name("sources.json")))["items"]
    by_file = {Path(i.get("file", "")).name: i for i in items}
    lines = [f"# Credits for {Path(args.out).name}", ""]
    for kind, p in [("Track", track)] + [("Bed", b) for b in beds]:
        key = Path(p).stem.replace(".wav", "")
        hit = next((i for f, i in by_file.items() if Path(f).stem == key), None)
        if hit:
            lines.append(f"- {kind}: {hit['title']} - {hit.get('author', 'unknown')} - {hit['license']} - {hit['source']}")
        else:
            lines.append(f"- {kind}: {Path(p).name} (not in sources.json)")
    lines += ["", f"Processing: varispeed {args.semitones} semitones, Vinyl, SpaceModulator, Supermassive, compression, "
                  f"wet {args.wet_lo:.0%}-{args.wet_hi:.0%}, seed {args.seed}."]
    return "\n".join(lines) + "\n"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("track")
    ap.add_argument("--beds", nargs="+", default=[], help="seamless loops from loop.py (required unless --stem)")
    ap.add_argument("--semitones", type=float, default=0.0)
    ap.add_argument("--wet-lo", type=float, default=0.15)
    ap.add_argument("--wet-hi", type=float, default=0.32)
    ap.add_argument("--lead-in", type=float, default=6.0)
    ap.add_argument("--tail", type=float, default=14.0)
    ap.add_argument("--bed-db", type=float, default=-12.0, help="bed gain in dB (beds are leveled to -30 LUFS first)")
    ap.add_argument("--stem", action="store_true", help="write only the processed track (no bed, no lead-in) for mix.py")
    ap.add_argument("--seed", type=int, default=1)
    ap.add_argument("--out", required=True)
    args = ap.parse_args()
    rng = np.random.default_rng(args.seed)

    dry = level(load(args.track), TRACK_LUFS)
    dry = varispeed(dry, args.semitones)
    dry = np.concatenate([dry, np.zeros((int(args.tail * SR), 2))])  # room for the reverb tail
    wet = process_chain(dry, rng, args.wet_lo, args.wet_hi)
    wet = compress(wet)

    if args.stem:
        save(args.out, guard(level(wet, FINAL_LUFS)))
        Path(args.out).with_suffix(".credits.md").write_text(credits(args.track, [], args))
        print(f"{args.out}: stem {len(wet) / SR:.1f}s")
        return
    if not args.beds:
        ap.error("--beds is required unless --stem")

    total = int(args.lead_in * SR) + len(wet)
    loops = [load(b) for b in args.beds]
    bed = sum(tile(l, total, int(rng.integers(len(l)))) * drift(total, rng) for l in loops) / np.sqrt(len(loops))
    bed = fade(bed * 10 ** (args.bed_db / 20), 0, 0, in_s=4.0, out_s=10.0)

    mix = bed
    start = int(args.lead_in * SR)
    mix[start:] += fade(wet, 0, 0, in_s=2.0)
    mix = guard(level(mix, FINAL_LUFS))
    save(args.out, mix)
    Path(args.out).with_suffix(".credits.md").write_text(credits(args.track, args.beds, args))
    print(f"{args.out}: {len(mix) / SR:.1f}s, {pyln.Meter(SR).integrated_loudness(mix):.1f} LUFS, peak {np.abs(mix).max():.2f}")


if __name__ == "__main__":
    main()
