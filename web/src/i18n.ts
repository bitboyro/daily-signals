import { messages, type Locale, type MessageKey } from './generated/messages.ts';

export const supportedLocales: readonly Locale[] = ['en', 'ro'];

export function normalizeLocale(candidate?: string): Locale {
  const language = candidate?.trim().toLowerCase().split(/[-_]/, 1)[0];
  return language === 'ro' ? 'ro' : 'en';
}

export function message(locale: Locale, key: MessageKey): string {
  return messages[locale][key];
}
