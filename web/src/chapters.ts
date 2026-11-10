import { escapeHtml } from './html.ts';
import { readEnvelope } from './schema.ts';

export interface EditionChapter { title: string; startMillis: number; }

function isChapter(value: unknown): value is EditionChapter {
  if (typeof value !== 'object' || value === null) return false;
  const chapter = value as Record<string, unknown>;
  return typeof chapter.title === 'string' && chapter.title.trim() !== ''
    && typeof chapter.startMillis === 'number' && Number.isSafeInteger(chapter.startMillis)
    && chapter.startMillis >= 0;
}

export function parseChapters(payload: unknown): EditionChapter[] {
  const chapters = readEnvelope(payload, 'chapters', 1);
  if (!chapters.every(isChapter)
    || chapters.some((chapter, index) => index > 0 && chapter.startMillis <= chapters[index - 1].startMillis)) {
    throw new Error('unexpected chapter response');
  }
  return chapters;
}

export async function fetchChapters(streamBase: string, editionId: string,
                                    fetchImpl: typeof fetch = fetch): Promise<EditionChapter[]> {
  const response = await fetchImpl(`${streamBase}/editions/${encodeURIComponent(editionId)}/chapters`,
    { headers: { Accept: 'application/json' } });
  if (!response.ok) throw new Error(`chapters unavailable: ${response.status}`);
  return parseChapters(await response.json());
}

export function chapterSeconds(chapter: EditionChapter): number {
  return chapter.startMillis / 1000;
}

export function renderChapters(chapters: EditionChapter[]): string {
  if (chapters.length === 0) return '';
  const items = chapters.map((chapter) => `<li><button type="button" data-seek-millis="${chapter.startMillis}">`
    + `${escapeHtml(chapter.title)} <span>${formatOffset(chapter.startMillis)}</span></button></li>`).join('');
  return `<nav class="chapters" aria-label="Edition chapters"><h3>Chapters</h3><ol>${items}</ol></nav>`;
}

export function bindChapterSeeking(root: HTMLElement): void {
  const audio = root.querySelector<HTMLAudioElement>('audio');
  if (!audio) return;
  root.querySelectorAll<HTMLButtonElement>('button[data-seek-millis]').forEach((button) => {
    button.addEventListener('click', () => {
      audio.currentTime = Number(button.dataset.seekMillis) / 1000;
      void audio.play();
    });
  });
}

function formatOffset(millis: number): string {
  const seconds = Math.floor(millis / 1000);
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
}
