#!/usr/bin/env python3
"""Estimate tempo, tuning and key for tracks; write analysis.json (used later for beat-aware FX).

  analyze.py assets/tracks/*.mp3

Estimates, not truth: old or live recordings drift in tempo and speed, and blues uses
blue notes, so the key margin is reported and a low margin means 'ambiguous'.
"""
import json
import sys
from pathlib import Path

import librosa
import numpy as np

NOTES = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"]
# Krumhansl-Kessler key profiles
MAJOR = np.array([6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88])
MINOR = np.array([6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17])


def analyze(path):
    y, sr = librosa.load(path, sr=22050, mono=True)
    y_h, _ = librosa.effects.hpss(y)  # harmonic part for pitch, percussive confuses chroma
    tempo, beats = librosa.beat.beat_track(y=y, sr=sr, units="time")
    tempo = float(np.atleast_1d(tempo)[0])
    ibi = np.diff(beats)
    steadiness = float(np.std(ibi) / np.mean(ibi)) if len(ibi) > 4 else None  # 0 = metronomic
    tuning = float(librosa.estimate_tuning(y=y_h, sr=sr))  # fraction of a semitone
    chroma = librosa.feature.chroma_cqt(y=y_h, sr=sr, tuning=tuning).mean(axis=1)
    scores = []
    for i in range(12):
        for name, prof in (("major", MAJOR), ("minor", MINOR)):
            scores.append((float(np.corrcoef(chroma, np.roll(prof, i))[0, 1]), f"{NOTES[i]} {name}"))
    scores.sort(reverse=True)
    return {
        "durationSeconds": round(len(y) / sr, 1),
        "tempoBpm": round(tempo, 1),
        "tempoAlternatives": [round(tempo / 2, 1), round(tempo * 2, 1)],
        "beatIrregularity": None if steadiness is None else round(steadiness, 3),
        "tuningCentsFromA440": round(tuning * 100, 1),
        "key": scores[0][1],
        "keyScore": round(scores[0][0], 3),
        "runnerUp": scores[1][1],
        "keyMargin": round(scores[0][0] - scores[1][0], 3),
        "top3Keys": [s[1] for s in scores[:3]],
    }


if __name__ == "__main__":
    out = {}
    for p in sys.argv[1:]:
        out[Path(p).name] = analyze(p)
        r = out[Path(p).name]
        print(f"{Path(p).name}: {r['tempoBpm']} BPM (or {r['tempoAlternatives']}), irregularity {r['beatIrregularity']}, "
              f"tuning {r['tuningCentsFromA440']:+} cents, key {r['key']} (margin {r['keyMargin']}, next {r['runnerUp']})")
    Path("analysis.json").write_text(json.dumps(out, indent=2) + "\n")
