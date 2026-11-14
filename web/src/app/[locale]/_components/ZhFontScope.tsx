"use client";

import { Noto_Sans_SC } from "next/font/google";
import type { ReactNode } from "react";

/**
 * Noto Sans SC for the zh locale only. Loaded through `ZhFontLoader` (next/dynamic in a client
 * module), so its @font-face stylesheet is a separate CSS file linked only by pages that render it:
 * /vi and /en never reference the font files.
 * `preload: false`: the font is split into many unicode-range files; the browser fetches the
 * ones the page's characters need.
 */
const notoSansSc = Noto_Sans_SC({
  variable: "--font-noto-sc",
  display: "swap",
  preload: false,
});

/** Defines `--font-noto-sc` for everything inside; `:lang(zh)` in globals.css then uses it. */
export default function ZhFontScope({ children }: { children: ReactNode }) {
  return <div className={`${notoSansSc.variable} contents`}>{children}</div>;
}
