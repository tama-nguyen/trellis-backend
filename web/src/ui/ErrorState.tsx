import { CircleAlert } from "lucide-react";
import type { ReactNode } from "react";
import { Button } from "./Button";
import { Icon } from "./Icon";
import { cn } from "./cn";

export type ErrorStateProps = {
  title: ReactNode;
  /** Already mapped from the error code; never a raw server message. */
  description?: ReactNode;
  /** Shown with `onRetry`; already translated. */
  retryLabel?: string;
  onRetry?: () => void;
  /** Support reference (correlation id) for server faults. */
  reference?: ReactNode;
  className?: string;
};

export function ErrorState({
  title,
  description,
  retryLabel,
  onRetry,
  reference,
  className,
}: ErrorStateProps) {
  return (
    <div
      role="alert"
      className={cn(
        "flex flex-col items-center gap-3 rounded-card border border-border bg-danger-subtle px-6 py-12 text-center",
        className,
      )}
    >
      <span className="text-danger">
        <Icon icon={CircleAlert} size="xl" />
      </span>
      <p className="text-base font-medium text-fg">{title}</p>
      {description ? <p className="max-w-md text-sm text-fg-muted">{description}</p> : null}
      {reference ? <p className="font-mono text-xs text-fg-subtle">{reference}</p> : null}
      {onRetry && retryLabel ? (
        <Button variant="secondary" size="sm" onClick={onRetry}>
          {retryLabel}
        </Button>
      ) : null}
    </div>
  );
}
