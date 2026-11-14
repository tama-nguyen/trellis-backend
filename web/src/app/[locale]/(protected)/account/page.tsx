import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { AccountPanel } from "@/features/auth/AccountPanel";
import type { Locale } from "@/i18n/routing";

type AccountPageProps = { params: Promise<{ locale: Locale }> };

export async function generateMetadata({ params }: AccountPageProps): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "account" });
  return { title: t("title") };
}

export default function AccountPage() {
  return <AccountPanel />;
}
