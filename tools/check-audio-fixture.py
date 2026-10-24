#!/usr/bin/env python3
"""Verify the pinned synthetic voice fixture's format, content and identity."""
from pathlib import Path
import hashlib
import wave

ROOT = Path(__file__).resolve().parents[1]
path = ROOT / 'radio-edition/src/main/resources/fixtures/fixture-voice.wav'
assert hashlib.sha256(path.read_bytes()).hexdigest() == 'da609c7a1c4bd8bb70f7b9ca39505db0f9fd545d303408f665ce80835744627c'
with wave.open(str(path), 'rb') as audio:
    assert (audio.getnchannels(), audio.getsampwidth(), audio.getframerate(), audio.getnframes()) == (1, 2, 8000, 8000)
    frames = audio.readframes(8000)
    assert any(frames[:6400]) and not any(frames[6400:8000]) and any(frames[8000:])
print('Pinned synthetic voice fixture passed')
