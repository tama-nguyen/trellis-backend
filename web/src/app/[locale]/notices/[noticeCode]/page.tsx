import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";
import { NoticeDetailPage } from "@/features/notice/NoticeDetailPage";
import type { Locale } from "@/i18n/routing";

type NoticeDetailRouteProps = { params: Promise<{ locale: Locale; noticeCode: string }> };

export async function generateMetadata({ params }: NoticeDetailRouteProps): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "notice.detail" });
  return { title: t("title") };
}

export default async function NoticeDetailRoute({ params }: NoticeDetailRouteProps) {
  const { locale, noticeCode } = await params;
  setRequestLocale(locale);
  return <NoticeDetailPage noticeCode={noticeCode} />;
}
