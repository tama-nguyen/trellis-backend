"use client";

import { useTranslations } from "next-intl";
import { useEffect, type ReactNode } from "react";
import { useMe } from "@/entities/auth/auth.queries";
import { useSession } from "@/entities/auth/auth.session";
import { usePathname, useRouter } from "@/i18n/navigation";
import { getApiErrorMessage } from "@/shared/api/errors";
import { REDIRECT_PARAM, ROUTES } from "@/shared/constants/routes";
import { screenStateOf } from "@/shared/lib/screenState";
import { ErrorState } from "@/ui/ErrorState";
import { PageShell, SCREEN_STATES } from "@/ui/PageShell";
import { Skeleton } from "@/ui/Skeleton";

/**
 * Client-side gate for signed-in areas. UX only: the backend authorizes every request. With a
 * cookie-based session this check should move to middleware so the page never renders.
 */
export function RequireAuth({ children }: { children: ReactNode }) {
  const t = useTranslations();
  const { tokens, hydrated } = useSession();
  const me = useMe();
  const router = useRouter();
  const pathname = usePathname();
  const signedOut = hydrated && tokens === null;

  // Navigation is an external system: this is a legitimate effect, not derived state.
  useEffect(() => {
    if (signedOut) router.replace({ pathname: ROUTES.login, query: { [REDIRECT_PARAM]: pathname } });
  }, [signedOut, router, pathname]);

  const state = !hydrated || signedOut ? SCREEN_STATES.LOADING : screenStateOf(me);
  if (state === SCREEN_STATES.READY) return children;

  return (
    <PageShell
      state={state}
      loading={<Skeleton className="h-40 w-full rounded-card" />}
      error={
        <ErrorState
          title={t("common.states.errorTitle")}
          description={getApiErrorMessage(me.error, t)}
          retryLabel={t("common.actions.retry")}
          onRetry={() => void me.refetch()}
        />
      }
    />
  );
}
