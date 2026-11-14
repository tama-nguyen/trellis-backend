import * as Label from "@radix-ui/react-label";
import { cloneElement, isValidElement, useId, type ReactElement, type ReactNode } from "react";
import { cn } from "./cn";

type ControlProps = {
  id?: string;
  isInvalid?: boolean;
  "aria-describedby"?: string;
};

export type FieldProps = {
  label: ReactNode;
  /** A single control (Input, Select trigger...). Field wires id, aria-describedby and isInvalid into it. */
  children: ReactElement<ControlProps>;
  hint?: ReactNode;
  /** Already-translated message; its presence marks the control invalid. */
  error?: ReactNode;
  className?: string;
  labelClassName?: string;
};

export function Field({ label, children, hint, error, className, labelClassName }: FieldProps) {
  const generatedId = useId();
  const controlId = (isValidElement(children) && children.props.id) || generatedId;
  const hintId = `${controlId}-hint`;
  const errorId = `${controlId}-error`;
  const describedBy = [hint ? hintId : null, error ? errorId : null].filter(Boolean).join(" ") || undefined;

  return (
    <div className={cn("flex flex-col gap-1.5", className)}>
      <Label.Root htmlFor={controlId} className={cn("text-sm font-medium text-fg", labelClassName)}>
        {label}
      </Label.Root>
      {cloneElement(children, { id: controlId, isInvalid: Boolean(error), "aria-describedby": describedBy })}
      {hint && !error ? (
        <p id={hintId} className="text-xs text-fg-subtle">
          {hint}
        </p>
      ) : null}
      {error ? (
        <p id={errorId} className="text-xs text-danger" role="alert">
          {error}
        </p>
      ) : null}
    </div>
  );
}
