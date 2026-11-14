"use client";

import { LogIn, LogOut } from "lucide-react";
import { useTranslations } from "next-intl";
import { PERMISSIONS } from "@/entities/auth/auth.constants";
import { useCan, useLogout, useMe } from "@/entities/auth/auth.queries";
import { useSession } from "@/entities/auth/auth.session";
import { Link, useRouter } from "@/i18n/navigation";
import { ROUTES } from "@/shared/constants/routes";
import { EventType, track } from "@/shared/lib/analytics";
import { Button } from "@/ui/Button";
import { Icon } from "@/ui/Icon";
import { Skeleton } from "@/ui/Skeleton";

/** Header area: sign-in link when signed out; account, admin (if permitted) and sign-out when in. */
export function AuthMenu() {
  const t = useTranslations("common.nav");
  const { tokens, hydrated } = useSession();
  const me = useMe();
  const { can } = useCan();
  const logout = useLogout();
  const router = useRouter();

  if (!hydrated) return <Skeleton className="h-8 w-24" />;
  if (!tokens) {
    return (
      <Button asChild variant="secondary" size="sm">
        <Link href={ROUTES.login}>
          <Icon icon={LogIn} size="sm" />
          {t("signIn")}
        </Link>
      </Button>
    );
  }

  const onLogout = () => {
    track({ type: EventType.LoggedOut });
    logout.mutate(undefined, { onSettled: () => router.replace(ROUTES.home) });
  };

  return (
    <div className="flex min-w-0 items-center gap-1">
      {can(PERMISSIONS.NOTICE_MANAGE) ? (
        <Button asChild variant="ghost" size="sm">
          <Link href={ROUTES.admin}>{t("admin")}</Link>
        </Button>
      ) : null}
      <Button asChild variant="ghost" size="sm">
        <Link href={ROUTES.account} className="max-w-28 sm:max-w-48">
          <span className="truncate">{me.data?.displayName ?? t("account")}</span>
        </Link>
      </Button>
      <Button
        variant="ghost"
        size="sm"
        onClick={onLogout}
        isLoading={logout.isPending}
        icon={<Icon icon={LogOut} size="sm" />}
        aria-label={t("signOut")}
      />
    </div>
  );
}
