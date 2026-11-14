import { Inter, JetBrains_Mono } from "next/font/google";

/**
 * UI fonts, self-hosted by next/font at build time (no request to Google at runtime). Each sets a
 * CSS variable on <html>; the stacks in globals.css (`--font-sans`, `--font-mono`) read them.
 * Noto Sans SC is not here: it is large and only the zh locale needs it (ZhFontScope.tsx).
 */
export const inter = Inter({
  subsets: ["latin", "vietnamese"],
  variable: "--font-inter",
  display: "swap",
});

export const jetBrainsMono = JetBrains_Mono({
  subsets: ["latin"],
  variable: "--font-mono-src",
  display: "swap",
  // Code is rare in the UI; do not compete with the body font for early bandwidth.
  preload: false,
});
