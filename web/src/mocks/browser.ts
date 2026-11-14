import { mockedDomains } from "./domains";

/**
 * Installs the mock backend for the domains `NEXT_PUBLIC_MOCK_DOMAINS` names, and resolves at
 * once when it names none, so callers can always await it. Handlers are imported lazily so a
 * build with mocks off never loads them on first paint.
 */
export async function startMockBackend(): Promise<void> {
  const domains = mockedDomains();
  if (domains.size === 0 || typeof window === "undefined") return;
  const [{ installMockFetch }, { routeGroups }] = await Promise.all([import("./router"), import("./routes")]);
  installMockFetch([...domains].flatMap((domain) => routeGroups[domain]));
}
