import { defineRouting } from "next-intl/routing";

/** Supported UI languages. Adding one: see web/AGENTS.md "Add a locale". */
export const LOCALES = ["vi", "en", "zh"] as const;
export type Locale = (typeof LOCALES)[number];

export const DEFAULT_LOCALE: Locale = "vi";

/** BCP 47 tags handed to `Intl` and to `<html lang>`; `zh` is Simplified Chinese. */
export const INTL_LOCALES: Record<Locale, string> = {
  vi: "vi-VN",
  en: "en-GB",
  zh: "zh-CN",
};

export const routing = defineRouting({
  locales: LOCALES,
  defaultLocale: DEFAULT_LOCALE,
  // Every URL carries its locale, so a shared link always opens in the language it was copied from.
  localePrefix: "always",
});
