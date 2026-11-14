import type { InputHTMLAttributes, Ref } from "react";
import { cn } from "./cn";

export type InputProps = InputHTMLAttributes<HTMLInputElement> & {
  ref?: Ref<HTMLInputElement>;
  /** Marks the control invalid for styling and assistive tech; `Field` sets it from its `error`. */
  isInvalid?: boolean;
};

export function Input({ isInvalid = false, className, ...props }: InputProps) {
  return (
    <input
      aria-invalid={isInvalid || undefined}
      className={cn(
        "h-10 w-full rounded-control border border-border-strong bg-surface px-3 text-sm text-fg placeholder:text-fg-subtle",
        "focus-visible:border-ring focus-visible:outline-none",
        "disabled:cursor-not-allowed disabled:opacity-60",
        isInvalid && "border-danger",
        className,
      )}
      {...props}
    />
  );
}
