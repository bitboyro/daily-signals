# Plugins for character

Ideas for giving the program more character with plugins, instead of adding more effect moments. Nothing here is wired into the scripts yet.

The current chain uses iZotope Vinyl, Valhalla Space Modulator and Valhalla Supermassive (in `render.py`). Everything else (grunge, repeats, sweeps, reverb arcs, the drone's grit) is done in NumPy and Pedalboard's built-in effects.

## Already installed, not used yet

Found in `~/Library/Audio/Plug-Ins` (VST3 and AU, arm64) on the development Mac.

| Plugin | Character | Where it would fit |
|---|---|---|
| iZotope Trash 2 | Multiband distortion, filters, speaker and box convolution | Replace the NumPy grunge sections; grit on the drone bass |
| Valhalla Freq Echo | Frequency shifter feeding a delay: dub throws, barber-pole tails | Throws on the ends of voice inserts and Kangaroos interludes |
| iZotope Mobius Filter | Endlessly rising or falling filter sweep | Tension moments, instead of the underwater sweeps |
| iZotope DDLY | Delay that reacts to level: echoes on quiet parts, dry on loud ones | Delay on the 78s without smearing them |
| NI Raum | Lush reverb with a "Cosmic" mode | The two long reverb arcs in `master.py` |
| NI Replika | Tape, analog and vintage digital delay modes | Slow modulated echoes in the arcs |
| NI Phasis | Phaser | Slow phase movement during the reverb arcs |
| iZotope RX 7 De-click, De-hum | Restoration | Light de-click on a track so chosen crackle can be placed on purpose |

## Free, worth installing

Check each site for the current version, price and an arm64 macOS build before installing.

| Plugin | Character | Where it would fit |
|---|---|---|
| Chow Tape Model (ChowDSP, open source) | Tape saturation, wow, flutter, loss | Subtly on the master, so the whole program sounds like one tape (the biggest win) |
| Airwindows Consolidated | Hundreds of small processors; ToTape, IronOxide, Galactic stand out | Tape colour, a huge dark reverb |
| Xfer OTT | Extreme multiband up/down compression | Drone bass only, 15-30% mix. On the 78s it lifts surface noise far too much |
| Surge XT Effects (Nimbus) | Granular texture processor | Turning the reverb arcs into grains |
| Glitchmachines Fracture, Hysteresis | Buffer glitch, glitch delay | Replacing the NumPy repeat stutters |
| PaulXStretch | Extreme stretching (10-100x) | A Kangaroos interlude as a long ambient bridge |
| TAL-Chorus-LX | Juno-60 chorus | Width and gloss on the drone bass |
| Kilohearts Essentials | Frequency shifter, tape stop, bitcrush, stereo tools | Small moves |

## Suggested use

The aim is fewer, better tricks. The current mix has about 20 effect moments in 30 minutes.

1. Chow Tape on the master, subtle, for cohesion.
2. Trash 2 replaces the grunge sections; keep two of the three.
3. Freq Echo throws at the end of each voice insert.
4. Raum (Cosmic) plus a slow Phasis on the two reverb arcs.
5. TAL-Chorus and a touch of OTT on the drone bass.
6. Drop the repeats, stereo collapses and underwater sweeps these replace.

The program's ending (around 29:33) stays untouched.

## Loading a plugin with Pedalboard

```python
from pedalboard import load_plugin

trash = load_plugin("/Users/<you>/Library/Audio/Plug-Ins/VST3/iZotope Trash 2.vst3")
print(trash.parameters.keys())  # parameter names differ per plugin; read them before automating
trash.some_parameter = 0.4       # then call trash(audio, sample_rate) like any Pedalboard effect
```

- Prefer the VST3 build; use the `.component` (AU) when only that exists.
- Some iZotope AUs are "hook" components (`iZ...AUHook`). Load the `.vst3` instead.
- Process in chunks with `reset=False` when automating parameters over time, as `render.py` does.
- Plugins that need an authorization or a GUI on first run must be opened once in a DAW first.
