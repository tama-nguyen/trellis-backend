import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { hasLocale, NextIntlClientProvider } from "next-intl";
import { getTranslations, setRequestLocale } from "next-intl/server";
import type { ReactNode } from "react";
import { INTL_LOCALES, LOCALES, routing, type Locale } from "@/i18n/routing";
import { cn } from "@/ui/cn";
import { THEME_INIT_SCRIPT } from "@/shared/lib/theme";
import { AppHeader } from "./_components/AppHeader";
import { inter, jetBrainsMono } from "./_components/fonts";
import { ZhFontLoader } from "./_components/ZhFontLoader";
import { Providers } from "./_components/Providers";
import "@/styles/globals.css";

/** Locale whose script needs the CJK font. */
const CJK_LOCALE: Locale = "zh";

type LocaleLayoutProps = {
  children: ReactNode;
  params: Promise<{ locale: string }>;
};

export function generateStaticParams() {
  return LOCALES.map((locale) => ({ locale }));
}

export async function generateMetadata({ params }: LocaleLayoutProps): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({
    locale: hasLocale(LOCALES, locale) ? locale : routing.defaultLocale,
    namespace: "common.metadata",
  });
  // Only title and description here: Next merges nested metadata shallowly, so an openGraph or
  // canonical set in a layout leaks into every child page that does not override it.
  return {
    title: { template: `%s | ${t("siteName")}`, default: t("siteName") },
    description: t("description"),
  };
}

export default async function LocaleLayout({ children, params }: LocaleLayoutProps) {
  const { locale } = await params;
  if (!hasLocale(routing.locales, locale)) notFound();
  setRequestLocale(locale);

  return (
    // The inline theme script changes <html class> before hydration, which React would flag.
    <html
      lang={INTL_LOCALES[locale]}
      className={cn(inter.variable, jetBrainsMono.variable)}
      suppressHydrationWarning
    >
      <head>
        <script dangerouslySetInnerHTML={{ __html: THEME_INIT_SCRIPT }} />
      </head>
      <body className="flex min-h-dvh flex-col bg-canvas text-fg">
        <NextIntlClientProvider>
          <Providers>
            {locale === CJK_LOCALE ? (
              <ZhFontLoader>
                <AppShell>{children}</AppShell>
              </ZhFontLoader>
            ) : (
              <AppShell>{children}</AppShell>
            )}
          </Providers>
        </NextIntlClientProvider>
      </body>
    </html>
  );
}

function AppShell({ children }: { children: ReactNode }) {
  return (
    <>
      <AppHeader />
      <div className="flex flex-1 flex-col">{children}</div>
    </>
  );
}
