import type { ReactNode } from "react";

/**
 * The real root layout is `[locale]/layout.tsx`, which owns <html lang>. This file exists so
 * the global `not-found.tsx` (for paths outside any locale) has a parent.
 */
export default function RootLayout({ children }: { children: ReactNode }) {
  return children;
}
