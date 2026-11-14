import type { Metadata } from "next";
import { useTranslations } from "next-intl";
import { getTranslations } from "next-intl/server";
import type { Locale } from "@/i18n/routing";
import { EmptyState } from "@/ui/EmptyState";

type AdminPageProps = { params: Promise<{ locale: Locale }> };

export async function generateMetadata({ params }: AdminPageProps): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "admin.dashboard" });
  return { title: t("title") };
}

/** Placeholder: products add their admin pages under this segment. */
export default function AdminDashboardPage() {
  const t = useTranslations("admin.dashboard");
  return (
    <section className="flex flex-col gap-4">
      <h1 className="text-2xl font-semibold text-fg">{t("title")}</h1>
      <EmptyState title={t("emptyTitle")} description={t("emptyDescription")} />
    </section>
  );
}
