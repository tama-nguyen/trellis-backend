"use client";

import * as RadixSelect from "@radix-ui/react-select";
import { Check, ChevronDown } from "lucide-react";
import type { ReactNode } from "react";
import { Icon } from "./Icon";
import { cn } from "./cn";

export type SelectOption<V extends string> = {
  value: V;
  label: ReactNode;
  disabled?: boolean;
};

export type SelectProps<V extends string> = {
  options: readonly SelectOption<V>[];
  value?: V;
  defaultValue?: V;
  onValueChange?: (value: V) => void;
  placeholder?: ReactNode;
  disabled?: boolean;
  /** Set by `Field`. */
  id?: string;
  isInvalid?: boolean;
  "aria-describedby"?: string;
  "aria-label"?: string;
  className?: string;
  contentClassName?: string;
};

export function Select<V extends string>({
  options,
  value,
  defaultValue,
  onValueChange,
  placeholder,
  disabled,
  id,
  isInvalid = false,
  className,
  contentClassName,
  ...aria
}: SelectProps<V>) {
  return (
    <RadixSelect.Root
      value={value}
      defaultValue={defaultValue}
      // Radix types the callback with `string`; the options list is the only source of values.
      onValueChange={(next) => onValueChange?.(next as V)}
      disabled={disabled}
    >
      <RadixSelect.Trigger
        id={id}
        aria-invalid={isInvalid || undefined}
        {...aria}
        className={cn(
          "inline-flex h-10 w-full items-center justify-between gap-2 rounded-control border border-border-strong bg-surface px-3 text-sm whitespace-nowrap text-fg",
          "focus-visible:border-ring focus-visible:outline-none disabled:opacity-60 data-[placeholder]:text-fg-subtle",
          isInvalid && "border-danger",
          className,
        )}
      >
        {/* One line whatever the font metrics: a long value truncates instead of wrapping out of h-*. */}
        <span className="min-w-0 truncate">
          <RadixSelect.Value placeholder={placeholder} />
        </span>
        <RadixSelect.Icon>
          <Icon icon={ChevronDown} size="sm" />
        </RadixSelect.Icon>
      </RadixSelect.Trigger>
      <RadixSelect.Portal>
        <RadixSelect.Content
          position="popper"
          sideOffset={4}
          className={cn(
            "z-dropdown max-h-72 min-w-[var(--radix-select-trigger-width)] overflow-hidden rounded-control border border-border bg-surface-raised shadow-raised",
            contentClassName,
          )}
        >
          <RadixSelect.Viewport className="p-1">
            {options.map((option) => (
              <RadixSelect.Item
                key={option.value}
                value={option.value}
                disabled={option.disabled}
                className="relative flex cursor-default items-center rounded-control py-2 pr-8 pl-3 text-sm text-fg outline-none select-none data-[disabled]:opacity-50 data-[highlighted]:bg-surface-hover"
              >
                <RadixSelect.ItemText>{option.label}</RadixSelect.ItemText>
                <RadixSelect.ItemIndicator className="absolute right-2">
                  <Icon icon={Check} size="sm" />
                </RadixSelect.ItemIndicator>
              </RadixSelect.Item>
            ))}
          </RadixSelect.Viewport>
        </RadixSelect.Content>
      </RadixSelect.Portal>
    </RadixSelect.Root>
  );
}
