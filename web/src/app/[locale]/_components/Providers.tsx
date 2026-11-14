"use client";

import { QueryClientProvider } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { useEffect, useState, type ReactNode } from "react";
// Side-effect import: registers the auth adapter with the HTTP client before the first request.
import "@/entities/auth/auth.session";
import { startMockBackend } from "@/mocks/browser";
import { mockedDomains } from "@/mocks/domains";
import { getQueryClient } from "@/shared/api/queryClient";
import { ToastViewport } from "@/ui/Toast";
import { PageViewTracker } from "./PageViewTracker";

const MOCKING = mockedDomains().size > 0;

/**
 * Holds the tree back until the mock backend is installed; otherwise the first queries race it
 * and hit the network. Renders immediately when mocking is off (every non-dev environment).
 */
function useMockBackendReady(): boolean {
  const [ready, setReady] = useState(!MOCKING);
  useEffect(() => {
    if (!MOCKING) return;
    let cancelled = false;
    // Fail open: a mock that will not install is a broken dev setup, not a reason for a blank page.
    startMockBackend()
      .catch((error: unknown) => console.error("[mocks] failed to install, continuing without", error))
      .finally(() => {
        if (!cancelled) setReady(true);
      });
    return () => {
      cancelled = true;
    };
  }, []);
  return ready;
}

/** Client composition root: everything that needs context or the browser. Kept deliberately small. */
export function Providers({ children }: { children: ReactNode }) {
  const t = useTranslations("common.actions");
  const mockReady = useMockBackendReady();
  return (
    <QueryClientProvider client={getQueryClient()}>
      {mockReady ? children : null}
      <PageViewTracker />
      <ToastViewport dismissLabel={t("dismiss")} />
    </QueryClientProvider>
  );
}
