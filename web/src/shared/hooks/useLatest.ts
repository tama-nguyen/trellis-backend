"use client";

import { useLayoutEffect, useRef, type RefObject } from "react";

/**
 * A ref that always holds the latest value. Read it from callbacks that must not be recreated
 * when the value changes (timers, subscriptions, debounced functions). Never read it during render.
 *
 * Assigned in a layout effect rather than during render, because writing a ref while rendering
 * leaks values from renders React later discards (concurrent rendering, StrictMode).
 */
export function useLatest<T>(value: T): RefObject<T> {
  const ref = useRef(value);
  useLayoutEffect(() => {
    ref.current = value;
  });
  return ref;
}
