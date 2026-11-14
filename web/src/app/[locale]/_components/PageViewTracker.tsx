"use client";

import { usePathname } from "next/navigation";
import { useEffect } from "react";
import { EventType, track } from "@/shared/lib/analytics";

/** Sends a page view whenever the path changes. Analytics is an external system, so an effect fits. */
export function PageViewTracker() {
  const pathname = usePathname();
  useEffect(() => {
    track({ type: EventType.PageViewed, path: pathname });
  }, [pathname]);
  return null;
}
