import type { Locale } from "./routing";

/**
 * One JSON file per namespace per locale: messages/<locale>/<namespace>.json.
 * Namespaces are named by area (`common`, `auth`, `notice`), never by page.
 * `pnpm i18n:check` keeps the three locales in step.
 */
export const NAMESPACES = ["common", "errors", "auth", "account", "admin", "notice"] as const;
export type Namespace = (typeof NAMESPACES)[number];

export async function loadMessages(locale: Locale): Promise<Record<Namespace, Record<string, unknown>>> {
  const entries = await Promise.all(
    NAMESPACES.map(
      async (namespace) =>
        [
          namespace,
          (await import(`./messages/${locale}/${namespace}.json`)) as { default: Record<string, unknown> },
        ] as const,
    ),
  );
  return Object.fromEntries(entries.map(([namespace, module]) => [namespace, module.default])) as Record<
    Namespace,
    Record<string, unknown>
  >;
}
