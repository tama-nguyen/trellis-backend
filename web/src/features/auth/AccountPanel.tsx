"use client";

import { useTranslations } from "next-intl";
import { useMe } from "@/entities/auth/auth.queries";
import { PageShell } from "@/ui/PageShell";

/** Rendered inside RequireAuth, so the user is loaded. */
export function AccountPanel() {
  const t = useTranslations("account");
  const { data: me } = useMe();
  if (!me) return null;

  return (
    <PageShell title={t("title")} description={t("description")}>
      <dl className="grid max-w-2xl grid-cols-1 gap-x-6 gap-y-4 rounded-card border border-border bg-surface p-6 sm:grid-cols-[12rem_1fr]">
        <dt className="text-sm text-fg-muted">{t("fields.displayName")}</dt>
        <dd className="text-sm text-fg">{me.displayName}</dd>
        <dt className="text-sm text-fg-muted">{t("fields.email")}</dt>
        <dd className="text-sm break-all text-fg">{me.email}</dd>
        <dt className="text-sm text-fg-muted">{t("fields.permissions")}</dt>
        <dd className="text-sm text-fg">
          {me.permissions.length === 0 ? (
            t("noPermissions")
          ) : (
            <ul className="flex flex-wrap gap-2">
              {me.permissions.map((permission) => (
                <li
                  key={permission}
                  className="rounded-control bg-surface-sunken px-2 py-0.5 font-mono text-xs"
                >
                  {permission}
                </li>
              ))}
            </ul>
          )}
        </dd>
      </dl>
    </PageShell>
  );
}
