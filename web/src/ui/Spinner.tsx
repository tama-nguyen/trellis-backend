import { LoaderCircle } from "lucide-react";
import { Icon, type IconSize } from "./Icon";
import { cn } from "./cn";

export type SpinnerProps = {
  size?: IconSize;
  /** Accessible name; omit when the spinner sits inside a control that already says it is busy. */
  label?: string;
  className?: string;
};

export function Spinner({ size = "md", label, className }: SpinnerProps) {
  return (
    <span role={label ? "status" : undefined} aria-label={label} className={cn("inline-flex", className)}>
      <Icon icon={LoaderCircle} size={size} className="animate-spin" aria-hidden />
    </span>
  );
}
