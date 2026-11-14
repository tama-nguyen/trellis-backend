"use client";

import { useLocale, useTranslations } from "next-intl";
import { useTransition } from "react";
import { usePathname, useRouter } from "@/i18n/navigation";
import { LOCALES, type Locale } from "@/i18n/routing";
import { EventType, track } from "@/shared/lib/analytics";
import { Select } from "@/ui/Select";

export function LocaleSwitcher() {
  const t = useTranslations("common.locale");
  const locale = useLocale();
  const router = useRouter();
  const pathname = usePathname();
  const [isPending, startTransition] = useTransition();

  const onChange = (next: Locale) => {
    track({ type: EventType.LocaleChanged, from: locale, to: next });
    startTransition(() => router.replace(pathname, { locale: next }));
  };

  return (
    <Select<Locale>
      aria-label={t("label")}
      value={locale}
      onValueChange={onChange}
      disabled={isPending}
      options={LOCALES.map((value) => ({ value, label: t(value) }))}
      className="h-8 w-32"
    />
  );
}
