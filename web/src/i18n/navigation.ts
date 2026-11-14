import { createNavigation } from "next-intl/navigation";
import { routing } from "./routing";

/**
 * Locale-aware replacements for next/link and next/navigation. App code links with these so
 * the current locale prefix is kept; pass `{ locale }` to switch language.
 */
export const { Link, redirect, usePathname, useRouter, getPathname } = createNavigation(routing);
