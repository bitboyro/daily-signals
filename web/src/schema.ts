/** Versioned response envelope shared by the Java API's listener and producer endpoints. */
export const SUPPORTED_SCHEMA_VERSION = 2;

export function readEnvelope(payload: unknown, key: string): unknown[] {
  if (typeof payload !== 'object' || payload === null || Array.isArray(payload)) {
    throw new Error('expected a versioned response');
  }
  const envelope = payload as Record<string, unknown>;
  if (envelope.schemaVersion !== SUPPORTED_SCHEMA_VERSION) {
    throw new Error(`unsupported schema version: ${String(envelope.schemaVersion)}`);
  }
  const items = envelope[key];
  if (!Array.isArray(items)) throw new Error(`versioned response is missing ${key}`);
  return items;
}
