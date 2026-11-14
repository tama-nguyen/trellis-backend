import { Slot } from "@radix-ui/react-slot";
import { cva, type VariantProps } from "class-variance-authority";
import type { ButtonHTMLAttributes, ReactNode, Ref } from "react";
import { Spinner } from "./Spinner";
import { cn } from "./cn";

const buttonVariants = cva(
  "inline-flex items-center justify-center gap-2 rounded-control font-medium whitespace-nowrap transition-colors duration-fast ease-standard disabled:pointer-events-none disabled:opacity-50",
  {
    variants: {
      variant: {
        primary: "bg-brand text-on-brand hover:bg-brand-hover",
        secondary: "border border-border-strong bg-surface text-fg hover:bg-surface-hover",
        ghost: "text-fg hover:bg-surface-hover",
        danger: "bg-danger text-on-danger hover:opacity-90",
      },
      size: {
        sm: "h-8 px-3 text-sm",
        md: "h-10 px-4 text-sm",
        lg: "h-12 px-5 text-base",
      },
    },
  },
);

type ButtonVariantProps = VariantProps<typeof buttonVariants>;

/** Props that have a default. `buttonDefaults` is the single source of those defaults. */
export type ButtonPropsDefaults = {
  variant: NonNullable<ButtonVariantProps["variant"]>;
  size: NonNullable<ButtonVariantProps["size"]>;
  isLoading: boolean;
  /** Render the child element (e.g. a Link) with button styling instead of a <button>. */
  asChild: boolean;
};

export const buttonDefaults: ButtonPropsDefaults = {
  variant: "primary",
  size: "md",
  isLoading: false,
  asChild: false,
};

export type ButtonProps = Partial<ButtonPropsDefaults> &
  ButtonHTMLAttributes<HTMLButtonElement> & {
    ref?: Ref<HTMLButtonElement>;
    /** Shown before the label; replaced by a spinner while `isLoading`. */
    icon?: ReactNode;
  };

export function Button({
  variant = buttonDefaults.variant,
  size = buttonDefaults.size,
  isLoading = buttonDefaults.isLoading,
  asChild = buttonDefaults.asChild,
  icon,
  className,
  children,
  disabled,
  type = "button",
  ...props
}: ButtonProps) {
  const classes = cn(buttonVariants({ variant, size }), className);
  if (asChild) {
    return (
      <Slot className={classes} {...props}>
        {children}
      </Slot>
    );
  }
  return (
    <button
      type={type}
      className={classes}
      disabled={disabled || isLoading}
      aria-busy={isLoading || undefined}
      {...props}
    >
      {isLoading ? <Spinner size="sm" /> : icon}
      {children}
    </button>
  );
}
