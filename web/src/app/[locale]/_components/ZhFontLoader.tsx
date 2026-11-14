"use client";

import dynamic from "next/dynamic";

/**
 * Code-splits ZhFontScope. `next/dynamic` must be called from a client module: only then does the
 * server render link the dynamic chunk's CSS for the pages that actually render it, instead of
 * bundling the font's @font-face rules into the layout stylesheet every locale downloads.
 */
export const ZhFontLoader = dynamic(() => import("./ZhFontScope"));
