/**
 * Where the backend lives, read at **runtime** on the server. Never a `NEXT_PUBLIC_` value: those
 * are baked into the bundle at build time, which would tie an image to one environment and break
 * "build once, promote by retag" (docs/architecture/delivery.md).
 *
 * The browser never needs this: it calls same-origin `/api/...`, which the proxy route
 * (`src/app/api/[...path]/route.ts`) forwards here.
 */
export const BACKEND_URL_ENV = "TRELLIS_BACKEND_URL";

/** Backend origin without a trailing slash, or undefined when not configured (e.g. mock-only dev). */
export function backendOrigin(): string | undefined {
  // Bracket access keeps Next from inlining the value at build time.
  const raw = process.env[BACKEND_URL_ENV]?.trim();
  return raw ? raw.replace(/\/+$/, "") : undefined;
}
