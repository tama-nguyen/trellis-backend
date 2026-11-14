import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";
import { Suspense } from "react";
import { LoginForm } from "@/features/auth/LoginForm";
import type { Locale } from "@/i18n/routing";
import { PageShell } from "@/ui/PageShell";

type LoginPageProps = { params: Promise<{ locale: Locale }> };

export async function generateMetadata({ params }: LoginPageProps): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "auth.login" });
  return { title: t("title"), robots: { index: false } };
}

export default async function LoginPage({ params }: LoginPageProps) {
  const { locale } = await params;
  setRequestLocale(locale);
  const t = await getTranslations("auth.login");
  return (
    <PageShell title={t("title")} description={t("description")} className="max-w-md">
      <div className="rounded-card border border-border bg-surface p-6">
        <Suspense>
          <LoginForm />
        </Suspense>
      </div>
    </PageShell>
  );
}
