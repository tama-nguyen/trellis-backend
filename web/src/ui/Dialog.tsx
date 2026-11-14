"use client";

import * as RadixDialog from "@radix-ui/react-dialog";
import { X } from "lucide-react";
import type { ReactNode } from "react";
import { Icon } from "./Icon";
import { cn } from "./cn";

export type DialogProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: ReactNode;
  description?: ReactNode;
  children?: ReactNode;
  /** Buttons, right-aligned. */
  footer?: ReactNode;
  /** Accessible name of the close button (already translated). Omit to hide the button. */
  closeLabel?: string;
  className?: string;
  bodyClassName?: string;
};

export function Dialog({
  open,
  onOpenChange,
  title,
  description,
  children,
  footer,
  closeLabel,
  className,
  bodyClassName,
}: DialogProps) {
  return (
    <RadixDialog.Root open={open} onOpenChange={onOpenChange}>
      <RadixDialog.Portal>
        <RadixDialog.Overlay className="fixed inset-0 z-overlay bg-overlay data-[state=open]:animate-fade-in" />
        <RadixDialog.Content
          className={cn(
            "fixed top-1/2 left-1/2 z-dialog flex max-h-[85dvh] w-[calc(100vw-2rem)] max-w-lg -translate-x-1/2 -translate-y-1/2 flex-col gap-4 rounded-dialog border border-border bg-surface-raised p-6 shadow-raised data-[state=open]:animate-scale-in",
            className,
          )}
        >
          <div className="flex items-start justify-between gap-4">
            <div className="flex flex-col gap-1">
              <RadixDialog.Title className="text-lg font-semibold text-fg">{title}</RadixDialog.Title>
              {description ? (
                <RadixDialog.Description className="text-sm text-fg-muted">
                  {description}
                </RadixDialog.Description>
              ) : null}
            </div>
            {closeLabel ? (
              <RadixDialog.Close
                aria-label={closeLabel}
                className="rounded-control p-1 text-fg-muted hover:bg-surface-hover"
              >
                <Icon icon={X} size="md" />
              </RadixDialog.Close>
            ) : null}
          </div>
          {children ? (
            <div className={cn("overflow-y-auto text-sm text-fg", bodyClassName)}>{children}</div>
          ) : null}
          {footer ? <div className="flex flex-wrap justify-end gap-2">{footer}</div> : null}
        </RadixDialog.Content>
      </RadixDialog.Portal>
    </RadixDialog.Root>
  );
}
