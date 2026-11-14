import { useTranslations } from "next-intl";
import { NOTICE_SEVERITIES, type NoticeSeverity } from "@/entities/notice/notice.constants";
import { cn } from "@/ui/cn";

const SEVERITY_CLASS: Record<NoticeSeverity, string> = {
  [NOTICE_SEVERITIES.INFO]: "bg-info-subtle text-info",
  [NOTICE_SEVERITIES.WARNING]: "bg-warning-subtle text-warning",
  [NOTICE_SEVERITIES.CRITICAL]: "bg-danger-subtle text-danger",
};

export function NoticeSeverityBadge({
  severity,
  className,
}: {
  severity: NoticeSeverity;
  className?: string;
}) {
  const t = useTranslations("notice.severity");
  return (
    <span
      className={cn(
        "inline-flex rounded-control px-2 py-0.5 text-xs font-medium",
        SEVERITY_CLASS[severity],
        className,
      )}
    >
      {t(severity)}
    </span>
  );
}
