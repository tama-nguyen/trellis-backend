import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";
import { Suspense } from "react";
import { NoticeListPage } from "@/features/notice/NoticeListPage";
import type { Locale } from "@/i18n/routing";

type NoticesPageProps = { params: Promise<{ locale: Locale }> };

export async function generateMetadata({ params }: NoticesPageProps): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "notice.list" });
  return { title: t("title") };
}

export default async function NoticesPage({ params }: NoticesPageProps) {
  setRequestLocale((await params).locale);
  return (
    <Suspense>
      <NoticeListPage />
    </Suspense>
  );
}
