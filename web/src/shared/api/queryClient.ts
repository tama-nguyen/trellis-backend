import { isServer, QueryClient } from "@tanstack/react-query";
import { ApiError } from "@/shared/api/apiError";
import { HTTP_STATUS } from "@/shared/constants/http";

/** Default freshness. Data that changes rarely (config, the signed-in user) sets its own `staleTime`. */
const DEFAULT_STALE_TIME_MS = 30_000;
const MAX_RETRIES = 2;

/** A 4xx will answer the same way next time; only network failures and 5xx are worth retrying. */
function shouldRetry(failureCount: number, error: unknown): boolean {
  if (failureCount >= MAX_RETRIES) return false;
  if (error instanceof ApiError) {
    return error.status === 0 || error.status >= HTTP_STATUS.INTERNAL_SERVER_ERROR;
  }
  return true;
}

function makeQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: { staleTime: DEFAULT_STALE_TIME_MS, retry: shouldRetry, refetchOnWindowFocus: false },
      mutations: { retry: false },
    },
  });
}

let browserClient: QueryClient | undefined;

/**
 * A fresh client per server request (so users never share a cache) and one per browser tab
 * (so React suspending during the first render does not throw the cache away).
 */
export function getQueryClient(): QueryClient {
  if (isServer) return makeQueryClient();
  browserClient ??= makeQueryClient();
  return browserClient;
}
