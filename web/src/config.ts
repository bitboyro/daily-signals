/** Where the listener finds the Java API and the Rust streamer. */
export interface ListenerConfig {
  apiBase: string;
  streamBase: string;
}

function trimBase(value: string): string {
  return value.endsWith('/') ? value.slice(0, -1) : value;
}

export function readConfig(origin: string, overrides: Partial<ListenerConfig> = {}): ListenerConfig {
  const apiBase = trimBase(overrides.apiBase ?? origin);
  return { apiBase, streamBase: trimBase(overrides.streamBase ?? apiBase) };
}
