"use client";

import { useTranslations } from "next-intl";
import { ErrorState } from "@/ui/ErrorState";
import { PageShell } from "@/ui/PageShell";

/** Segment error boundary. Next resets it on navigation; `reset` retries the render in place. */
export default function SegmentError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  const t = useTranslations("common");
  return (
    <PageShell>
      <ErrorState
        title={t("states.errorTitle")}
        description={t("states.errorDescription")}
        reference={error.digest}
        retryLabel={t("actions.retry")}
        onRetry={reset}
      />
    </PageShell>
  );
}
