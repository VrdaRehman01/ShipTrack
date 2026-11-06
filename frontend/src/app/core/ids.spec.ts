import { afterEach, describe, expect, it, vi } from 'vitest';
import { newIdempotencyKey } from './ids';

const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
const realCrypto = globalThis.crypto;

describe('newIdempotencyKey', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('uses crypto.randomUUID when available (https, localhost)', () => {
    expect(newIdempotencyKey()).toMatch(UUID_V4);
  });

  it('still works over plain http, where crypto.randomUUID does not exist', () => {
    // Simulate an insecure context: getRandomValues exists, randomUUID does not.
    vi.stubGlobal('crypto', { getRandomValues: <T extends ArrayBufferView>(a: T) => realCrypto.getRandomValues(a) });
    const a = newIdempotencyKey();
    const b = newIdempotencyKey();
    expect(a).toMatch(UUID_V4);
    expect(b).toMatch(UUID_V4);
    expect(a).not.toBe(b);
  });
});
