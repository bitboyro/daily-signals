# Soundscape lab

An experiment that is **separate from the radio pipeline**: no Maven module, no shared code, no dependency on the API, streamer or web client. It explores layered, slowly decaying audio programs for future stations.

## Layers

1. **Bed:** a continuous field recording from a real space (for example a tropical forest).
2. **Material:** one or more tracks with a permissive license.
3. **Processing:** existing VST3 plugins driven by an automation curve across time, with safe defaults and clamped ranges.
4. **Mix:** fade in, fade out, and a bed that never stops.

The first experiment is a **static offline render**: no live control, no streaming, no Liquidsoap, no Carla. Pedalboard loads the VST3 plugins already installed on the machine and applies parameter automation in chunks.

Ideas for more character from plugins, installed and free, are in [PLUGINS.md](PLUGINS.md).

## Rules for sources

- Only CC0 and CC BY (and public-domain items with a stated license) are used. No NonCommercial and no NoDerivatives items, because processing alters the audio.
- Every item is listed in `sources.json` with its title, author, license, source URL and fetch date, and the render writes a `credits.md` from it.
- Downloaded audio and rendered output are not committed (see `.gitignore`).

## Run

```
python3 -m venv soundscape/.venv
soundscape/.venv/bin/pip install -r soundscape/requirements.txt
```

The render script is added in a later step.
