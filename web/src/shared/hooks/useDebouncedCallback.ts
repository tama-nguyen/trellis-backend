"use client";

import { useEffect, useMemo } from "react";
import { useLatest } from "@/shared/hooks/useLatest";
import { debounce, type Debounced } from "@/shared/lib/debounce";

export const DEFAULT_DEBOUNCE_MS = 300;

/**
 * A debounced version of `callback` that survives re-renders.
 *
 * The common bug this prevents: `debounce(() => ..., 300)` written inline creates a new
 * debounced function (and a new timer) on every render, so the debounce never fires as
 * intended. Here the debounced function depends only on `delayMs`; the latest callback is read
 * through a ref, and the pending call is cancelled on unmount.
 *
 * Debounce only the expensive part (the request, the URL update). State that drives what the
 * user sees while typing, like the input value, must stay synchronous.
 */
export function useDebouncedCallback<TArgs extends unknown[]>(
  callback: (...args: TArgs) => void,
  delayMs: number = DEFAULT_DEBOUNCE_MS,
): Debounced<TArgs> {
  const callbackRef = useLatest(callback);
  const debounced = useMemo(
    // The ref is read when the timer fires, never during render; the compiler cannot see that.
    // eslint-disable-next-line react-hooks/refs
    () => debounce((...args: TArgs) => callbackRef.current(...args), delayMs),
    [delayMs, callbackRef],
  );
  useEffect(() => () => debounced.cancel(), [debounced]);
  return debounced;
}
