/**
 * A random UUID v4 for Idempotency-Keys.
 * crypto.randomUUID() only exists in secure contexts (https or localhost), so over plain http it is undefined.
 * crypto.getRandomValues() works everywhere, so build the UUID from it when needed.
 */
export function newIdempotencyKey(): string {
  const c: Crypto | undefined = globalThis.crypto;
  if (c && typeof c.randomUUID === 'function') {
    return c.randomUUID();
  }
  const bytes = new Uint8Array(16);
  if (c && typeof c.getRandomValues === 'function') {
    c.getRandomValues(bytes);
  } else {
    for (let i = 0; i < 16; i++) bytes[i] = Math.floor(Math.random() * 256);
  }
  bytes[6] = (bytes[6] & 0x0f) | 0x40; // version 4
  bytes[8] = (bytes[8] & 0x3f) | 0x80; // RFC 4122 variant
  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

/** Location and clipboard APIs only exist on https (or localhost). */
export function isSecureContextAvailable(): boolean {
  return typeof window !== 'undefined' && window.isSecureContext === true;
}
