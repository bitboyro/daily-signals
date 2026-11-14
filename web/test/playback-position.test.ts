import { test } from 'node:test';
import assert from 'node:assert/strict';
import { bindPlaybackPosition, clearPlaybackPosition, readPlaybackPosition, savePlaybackPosition } from '../src/playback-position.ts';

class MemoryStorage {
  readonly values = new Map<string, string>();
  getItem(key: string): string | null { return this.values.get(key) ?? null; }
  setItem(key: string, value: string): void { this.values.set(key, value); }
  removeItem(key: string): void { this.values.delete(key); }
}

class FakeAudio extends EventTarget {
  currentTime = 0;
  duration = 300;
}

test('stores positions independently for each edition', () => {
  const storage = new MemoryStorage();
  savePlaybackPosition(storage, 'morning', 42.9);
  savePlaybackPosition(storage, 'evening', 7);
  assert.equal(readPlaybackPosition(storage, 'morning'), 42);
  assert.equal(readPlaybackPosition(storage, 'evening'), 7);
  clearPlaybackPosition(storage, 'morning');
  assert.equal(readPlaybackPosition(storage, 'morning'), 0);
  assert.equal(readPlaybackPosition(storage, 'evening'), 7);
});

test('ignores corrupt, invalid and unavailable browser storage', () => {
  const storage = new MemoryStorage();
  storage.values.set('daily-signals:playback:morning', 'not-a-number');
  assert.equal(readPlaybackPosition(storage, 'morning'), 0);
  assert.equal(readPlaybackPosition(storage, '../private'), 0);
  const denied = { getItem: () => { throw new Error('denied'); }, setItem: () => { throw new Error('denied'); }, removeItem: () => { throw new Error('denied'); } };
  assert.equal(readPlaybackPosition(denied, 'morning'), 0);
  assert.doesNotThrow(() => savePlaybackPosition(denied, 'morning', 4));
  assert.doesNotThrow(() => clearPlaybackPosition(denied, 'morning'));
});

test('restores an in-range position, updates it and clears it at the end', () => {
  const storage = new MemoryStorage();
  const audio = new FakeAudio();
  const root = { querySelector: () => audio } as unknown as ParentNode;
  savePlaybackPosition(storage, 'morning', 90);
  bindPlaybackPosition(root, 'morning', storage);
  audio.dispatchEvent(new Event('loadedmetadata'));
  assert.equal(audio.currentTime, 90);
  audio.currentTime = 123.8;
  audio.dispatchEvent(new Event('timeupdate'));
  assert.equal(readPlaybackPosition(storage, 'morning'), 123);
  audio.dispatchEvent(new Event('ended'));
  assert.equal(readPlaybackPosition(storage, 'morning'), 0);
});

test('does not restore a position at the end of a recording', () => {
  const storage = new MemoryStorage();
  const audio = new FakeAudio();
  const root = { querySelector: () => audio } as unknown as ParentNode;
  savePlaybackPosition(storage, 'morning', 299.5);
  bindPlaybackPosition(root, 'morning', storage);
  audio.dispatchEvent(new Event('loadedmetadata'));
  assert.equal(audio.currentTime, 0);
});
