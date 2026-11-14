import { ArrowRight } from "lucide-react";
import { useTranslations } from "next-intl";
import { setRequestLocale } from "next-intl/server";
import { use } from "react";
import { Link } from "@/i18n/navigation";
import type { Locale } from "@/i18n/routing";
import { ROUTES } from "@/shared/constants/routes";
import { Button } from "@/ui/Button";
import { Icon } from "@/ui/Icon";
import { PageShell } from "@/ui/PageShell";

export default function WelcomePage({ params }: { params: Promise<{ locale: Locale }> }) {
  setRequestLocale(use(params).locale);
  const t = useTranslations("common.welcome");

  return (
    <PageShell>
      <section className="flex max-w-3xl flex-col gap-6 py-12 md:py-20">
        <h1 className="text-3xl font-semibold tracking-tight text-fg md:text-5xl" data-testid="welcome-title">
          {t("title")}
        </h1>
        <p className="text-lg text-fg-muted">{t("description")}</p>
        <div className="flex flex-wrap gap-3">
          <Button asChild size="lg">
            <Link href={ROUTES.notices}>
              {t("ctaNotices")}
              <Icon icon={ArrowRight} size="md" />
            </Link>
          </Button>
          <Button asChild size="lg" variant="secondary">
            <Link href={ROUTES.login}>{t("ctaSignIn")}</Link>
          </Button>
        </div>
      </section>
    </PageShell>
  );
}
