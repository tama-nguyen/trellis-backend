import type { Metadata } from "next";
import type { ReactNode } from "react";
import { RequireAuth } from "@/features/auth/RequireAuth";

/** Everything behind sign-in stays out of search results. */
export const metadata: Metadata = { robots: { index: false, follow: false } };

export default function ProtectedLayout({ children }: { children: ReactNode }) {
  return <RequireAuth>{children}</RequireAuth>;
}
