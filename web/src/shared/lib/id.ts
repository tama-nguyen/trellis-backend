/**
 * A random identifier for client-side keys (idempotency keys, toast ids).
 *
 * `crypto.randomUUID` only exists in secure contexts, so a page opened over plain http on a
 * LAN address would throw; `getRandomValues` is available everywhere.
 */
export function randomId(prefix = ""): string {
  const bytes = new Uint8Array(16);
  globalThis.crypto.getRandomValues(bytes);
  const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, "0")).join("");
  return `${prefix}${hex}`;
}
