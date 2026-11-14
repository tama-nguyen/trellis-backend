import { request } from "@/shared/api/http";
import type { HttpMethod } from "@/shared/constants/http";
import type { QueryParams } from "@/shared/lib/url";

/**
 * The orval mutator: every generated operation in `shared/api/generated/` calls this, and this calls
 * `http.ts`, so the generated client gets the same bearer token, refresh, `ApiError` and BFF routing
 * as hand-written code, and `http.ts` stays the only caller of `fetch`.
 */
export type ApiRequestConfig = {
  url: string;
  method: HttpMethod;
  params?: object;
  data?: unknown;
  headers?: Record<string, string | undefined>;
  signal?: AbortSignal;
};

/** The second argument of every generated operation. Pass React Query's `signal` here. */
export type ApiRequestOptions = {
  signal?: AbortSignal;
};

/** Drops headers the caller left undefined (optional header parameters such as `Idempotency-Key`). */
function definedHeaders(headers: ApiRequestConfig["headers"]): Record<string, string> | undefined {
  if (!headers) return undefined;
  const entries = Object.entries(headers).filter(
    (entry): entry is [string, string] => entry[1] !== undefined,
  );
  return entries.length > 0 ? Object.fromEntries(entries) : undefined;
}

export function apiRequest<T>(config: ApiRequestConfig, options?: ApiRequestOptions): Promise<T> {
  return request<T>({
    method: config.method,
    url: config.url,
    // Generated params types are plain objects of query values; http.ts drops undefined ones.
    params: config.params as QueryParams | undefined,
    body: config.data,
    headers: definedHeaders(config.headers),
    signal: options?.signal ?? config.signal,
  });
}
