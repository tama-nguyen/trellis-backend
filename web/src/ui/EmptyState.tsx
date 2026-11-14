import { Inbox, type LucideIcon } from "lucide-react";
import type { ReactNode } from "react";
import { Icon } from "./Icon";
import { cn } from "./cn";

export type EmptyStateProps = {
  title: ReactNode;
  /** Say how to move forward, not only that there is nothing here. */
  description?: ReactNode;
  icon?: LucideIcon;
  /** Usually one Button. */
  action?: ReactNode;
  className?: string;
};

export function EmptyState({ title, description, icon = Inbox, action, className }: EmptyStateProps) {
  return (
    <div
      className={cn(
        "flex flex-col items-center gap-3 rounded-card border border-dashed border-border-strong px-6 py-12 text-center",
        className,
      )}
    >
      <span className="text-fg-subtle">
        <Icon icon={icon} size="xl" />
      </span>
      <p className="text-base font-medium text-fg">{title}</p>
      {description ? <p className="max-w-md text-sm text-fg-muted">{description}</p> : null}
      {action}
    </div>
  );
}
