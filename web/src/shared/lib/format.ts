import { INTL_LOCALES, type Locale } from "@/i18n/routing";
import type { IsoDateTime } from "@/shared/types/api.types";

/**
 * Presentation-only formatting through `Intl`, keyed by the UI locale. No domain knowledge.
 * Pass the locale from `useLocale()` / `getLocale()`; never hardcode one.
 */

export const DATE_STYLES = {
  SHORT: "short",
  MEDIUM: "medium",
  LONG: "long",
} as const;
export type DateStyle = (typeof DATE_STYLES)[keyof typeof DATE_STYLES];

const MS_PER_SECOND = 1000;
/** Thresholds for picking the unit in `formatRelativeTime`, largest first. */
const RELATIVE_UNITS: readonly { unit: Intl.RelativeTimeFormatUnit; seconds: number }[] = [
  { unit: "year", seconds: 365 * 24 * 3600 },
  { unit: "month", seconds: 30 * 24 * 3600 },
  { unit: "week", seconds: 7 * 24 * 3600 },
  { unit: "day", seconds: 24 * 3600 },
  { unit: "hour", seconds: 3600 },
  { unit: "minute", seconds: 60 },
  { unit: "second", seconds: 1 },
];

const intlLocale = (locale: Locale): string => INTL_LOCALES[locale];

export function formatDate(iso: IsoDateTime, locale: Locale, style: DateStyle = DATE_STYLES.MEDIUM): string {
  return new Intl.DateTimeFormat(intlLocale(locale), { dateStyle: style }).format(new Date(iso));
}

export function formatDateTime(
  iso: IsoDateTime,
  locale: Locale,
  style: DateStyle = DATE_STYLES.MEDIUM,
): string {
  return new Intl.DateTimeFormat(intlLocale(locale), {
    dateStyle: style,
    timeStyle: DATE_STYLES.SHORT,
  }).format(new Date(iso));
}

/** "3 days ago", "in 2 hours". `now` is injectable so callers rendering on the server stay deterministic. */
export function formatRelativeTime(iso: IsoDateTime, locale: Locale, now: Date = new Date()): string {
  const diffSeconds = Math.round((new Date(iso).getTime() - now.getTime()) / MS_PER_SECOND);
  const format = new Intl.RelativeTimeFormat(intlLocale(locale), { numeric: "auto" });
  const match =
    RELATIVE_UNITS.find(({ seconds }) => Math.abs(diffSeconds) >= seconds) ??
    RELATIVE_UNITS[RELATIVE_UNITS.length - 1];
  const { unit, seconds } = match ?? { unit: "second", seconds: 1 };
  return format.format(Math.round(diffSeconds / seconds), unit);
}

export function formatNumber(value: number, locale: Locale, options?: Intl.NumberFormatOptions): string {
  return new Intl.NumberFormat(intlLocale(locale), options).format(value);
}

/** 1.2K, 3.4M (or the locale's own compact form, e.g. 1,2 N in Vietnamese). */
export function formatCompactNumber(value: number, locale: Locale): string {
  return formatNumber(value, locale, { notation: "compact", maximumFractionDigits: 1 });
}
