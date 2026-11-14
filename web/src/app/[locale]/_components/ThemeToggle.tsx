"use client";

import { Moon, Sun } from "lucide-react";
import { useTranslations } from "next-intl";
import { applyTheme, currentTheme, THEMES } from "@/shared/lib/theme";
import { Button } from "@/ui/Button";
import { Icon } from "@/ui/Icon";

/** Both icons render; CSS shows the right one, so server and client output match. */
export function ThemeToggle() {
  const t = useTranslations("common.theme");
  return (
    <Button
      variant="ghost"
      size="sm"
      aria-label={t("toggle")}
      onClick={() => applyTheme(currentTheme() === THEMES.DARK ? THEMES.LIGHT : THEMES.DARK)}
    >
      <Icon icon={Moon} size="md" className="dark:hidden" />
      <Icon icon={Sun} size="md" className="hidden dark:block" />
    </Button>
  );
}
