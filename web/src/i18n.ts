import { messages, type Locale, type MessageKey } from './generated/messages.ts';
export type { Locale, MessageKey } from './generated/messages.ts';

export const supportedLocales: readonly Locale[] = ['en', 'ro'];

export function normalizeLocale(candidate?: string): Locale {
  const language = candidate?.trim().toLowerCase().split(/[-_]/, 1)[0];
  return language === 'ro' ? 'ro' : 'en';
}

export function message(locale: Locale, key: MessageKey): string {
  return messages[locale][key];
}

export function localeTag(locale: Locale): 'en-GB' | 'ro-RO' {
  return locale === 'ro' ? 'ro-RO' : 'en-GB';
}

export function formatDate(value: string, locale: Locale): string {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) throw new Error('invalid date');
  return new Intl.DateTimeFormat(localeTag(locale), { dateStyle: 'long', timeZone: 'UTC' })
    .format(new Date(`${value}T00:00:00Z`));
}

export function formatDateTime(value: string, locale: Locale, timeZone = 'UTC'): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) throw new Error('invalid date time');
  return new Intl.DateTimeFormat(localeTag(locale), { dateStyle: 'medium', timeStyle: 'short', timeZone }).format(date);
}
