const PREFIX = 'daily-signals:playback:';

function key(editionId: string): string {
  if (!/^[a-z0-9-]+$/.test(editionId)) throw new Error('invalid edition id');
  return `${PREFIX}${editionId}`;
}

export function readPlaybackPosition(storage: Pick<Storage, 'getItem'>, editionId: string): number {
  try {
    const value = Number(storage.getItem(key(editionId)));
    return Number.isFinite(value) && value >= 0.25 ? value : 0;
  } catch {
    return 0;
  }
}

export function savePlaybackPosition(storage: Pick<Storage, 'setItem'>, editionId: string, seconds: number): void {
  if (!Number.isFinite(seconds) || seconds < 0) return;
  try {
    storage.setItem(key(editionId), String(Math.round(seconds * 1000) / 1000));
  } catch {
    // Playback remains available when browser storage is disabled or full.
  }
}

export function clearPlaybackPosition(storage: Pick<Storage, 'removeItem'>, editionId: string): void {
  try {
    storage.removeItem(key(editionId));
  } catch {
    // A completed recording does not become an error when storage is unavailable.
  }
}

/** Restore only a meaningful in-range position, then keep it current per edition. */
export function bindPlaybackPosition(root: ParentNode, editionId: string,
                                     storage: Pick<Storage, 'getItem' | 'setItem' | 'removeItem'> = localStorage): void {
  const audio = root.querySelector<HTMLAudioElement>('audio');
  if (!audio) return;
  audio.addEventListener('loadedmetadata', () => {
    const saved = readPlaybackPosition(storage, editionId);
    const endBuffer = Number.isFinite(audio.duration) ? Math.min(1, Math.max(0.25, audio.duration * 0.01)) : 0;
    if (saved > 0 && (!Number.isFinite(audio.duration) || saved < audio.duration - endBuffer)) audio.currentTime = saved;
  });
  audio.addEventListener('timeupdate', () => savePlaybackPosition(storage, editionId, audio.currentTime));
  audio.addEventListener('ended', () => clearPlaybackPosition(storage, editionId));
}
