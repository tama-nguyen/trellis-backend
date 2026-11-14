"use client";

import { useRef } from "react";
import { ApiError, NETWORK_ERROR_STATUS } from "@/shared/api/apiError";
import { HTTP_STATUS } from "@/shared/constants/http";
import { randomId } from "@/shared/lib/id";

/**
 * One `Idempotency-Key` per attempt at an action.
 *
 * The key is reused while the outcome is unknown (no answer, or the server says it is still
 * working), so a retry is replayed by the server instead of acting twice; it is dropped once
 * the server has answered for good, so the next attempt is a new one. A key minted per call is
 * never recognised as a retry.
 *
 * `scope` names what is being attempted (`${orderId}:pay`): a new scope starts a new attempt.
 * `label` only prefixes the key so it reads well in a log line.
 *
 * Usage: `current()` in the submit handler, `settle(!isOutcomeUnknown(err))` in `onError`,
 * `settle(true)` in `onSuccess`. Read from event handlers and callbacks only, never in render.
 */
export function useAttemptKey(scope: string, label = "attempt") {
  const attempt = useRef<{ scope: string; key: string } | null>(null);

  return {
    current(): string {
      if (attempt.current?.scope !== scope) {
        attempt.current = { scope, key: randomId(`${label}-`) };
      }
      return attempt.current.key;
    },
    settle(outcomeKnown: boolean): void {
      if (outcomeKnown) attempt.current = null;
    },
  };
}

/**
 * True when we cannot know whether the server acted: no answer at all, a server fault, or one
 * of the caller's "still working" codes. A 502/504 from a proxy, or a 500 thrown after the
 * commit, says nothing about whether the action happened; keeping the key is always safe.
 */
export function isOutcomeUnknown(error: unknown, pendingCodes: readonly string[] = []): boolean {
  if (!(error instanceof ApiError)) return true;
  return (
    error.status === NETWORK_ERROR_STATUS ||
    error.status >= HTTP_STATUS.INTERNAL_SERVER_ERROR ||
    pendingCodes.includes(error.code)
  );
}
