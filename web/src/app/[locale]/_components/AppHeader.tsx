import { useTranslations } from "next-intl";
import { AuthMenu } from "@/features/auth/AuthMenu";
import { Link } from "@/i18n/navigation";
import { ROUTES } from "@/shared/constants/routes";
import { LocaleSwitcher } from "./LocaleSwitcher";
import { ThemeToggle } from "./ThemeToggle";

export function AppHeader() {
  const t = useTranslations("common");
  return (
    <header className="border-b border-border bg-surface">
      <div className="mx-auto flex w-full max-w-7xl flex-wrap items-center justify-between gap-3 px-4 py-3 md:px-6">
        <div className="flex items-center gap-6">
          <Link href={ROUTES.home} className="text-base font-semibold text-fg">
            {t("metadata.siteName")}
          </Link>
          <nav aria-label={t("nav.label")} className="flex items-center gap-4 text-sm">
            <Link href={ROUTES.notices} className="text-fg-muted hover:text-fg">
              {t("nav.notices")}
            </Link>
          </nav>
        </div>
        <div className="flex min-w-0 flex-wrap items-center gap-1 sm:gap-2">
          <LocaleSwitcher />
          <ThemeToggle />
          <AuthMenu />
        </div>
      </div>
    </header>
  );
}
