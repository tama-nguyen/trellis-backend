"use client";

import { useQuery } from "@tanstack/react-query";
import { useSyncExternalStore } from "react";
import {
  DEFAULT_FEATURE_FLAGS,
  fetchFeatureFlags,
  featureFlagKeys,
  readCachedFlags,
  type FeatureFlagKey,
  type FeatureFlags,
} from "@/shared/lib/featureFlags";

/** Config changes on deploy, not while a tab is open. */
const FLAGS_STALE_TIME_MS = Number.POSITIVE_INFINITY;

const noSubscription = () => () => {};
const serverFlags = () => DEFAULT_FEATURE_FLAGS;

/**
 * The value of one feature flag. Order of preference: the server's answer, the last answer
 * cached in localStorage, the safe default. The server render and hydration always use the
 * default (`getServerSnapshot`), so there is no hydration mismatch; the cached value applies
 * right after.
 */
export function useFeatureFlag<K extends FeatureFlagKey>(key: K): FeatureFlags[K] {
  const { data } = useQuery({
    queryKey: featureFlagKeys.all,
    queryFn: ({ signal }) => fetchFeatureFlags(signal),
    staleTime: FLAGS_STALE_TIME_MS,
  });
  const cached = useSyncExternalStore(noSubscription, readCachedFlags, serverFlags);
  return (data ?? cached)[key];
}
