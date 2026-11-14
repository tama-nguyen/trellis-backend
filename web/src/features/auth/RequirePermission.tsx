"use client";

import { ShieldOff } from "lucide-react";
import { useTranslations } from "next-intl";
import type { ReactNode } from "react";
import type { Permission } from "@/entities/auth/auth.constants";
import { useCan } from "@/entities/auth/auth.queries";
import { EmptyState } from "@/ui/EmptyState";
import { PageShell, SCREEN_STATES } from "@/ui/PageShell";
import { Skeleton } from "@/ui/Skeleton";

/** Hides a whole surface from users without `permission`. UI only; the backend still checks. */
export function RequirePermission({ permission, children }: { permission: Permission; children: ReactNode }) {
  const t = useTranslations("common.states");
  const { can, isPending } = useCan();
  if (isPending)
    return (
      <PageShell state={SCREEN_STATES.LOADING} loading={<Skeleton className="h-40 w-full rounded-card" />} />
    );
  if (can(permission)) return children;
  return (
    <PageShell>
      <EmptyState icon={ShieldOff} title={t("forbiddenTitle")} description={t("forbiddenDescription")} />
    </PageShell>
  );
}
