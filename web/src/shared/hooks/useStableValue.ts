"use client";

import { useState } from "react";
import { isEqual } from "@/shared/lib/isEqual";

/**
 * Returns the previous reference while `value` is structurally equal to it.
 *
 * Use for objects rebuilt on every render that feed a query key, an effect dependency or a
 * memo: `{ status, q }` built inline is a new object each time, which would make an effect
 * re-run although nothing changed. React Query hashes keys itself, so query keys alone do not
 * need this; effects and memos do.
 *
 * Implemented as "adjust state during render", React's documented alternative to an effect.
 */
export function useStableValue<T>(value: T, equals: (a: T, b: T) => boolean = isEqual): T {
  const [stable, setStable] = useState(value);
  if (stable !== value && !equals(stable, value)) {
    setStable(value);
    return value;
  }
  return stable;
}
