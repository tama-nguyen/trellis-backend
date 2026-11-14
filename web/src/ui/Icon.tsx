import type { LucideIcon, LucideProps } from "lucide-react";
import { cn } from "./cn";

/** One size scale for every icon, so icons line up with text of the same size name. */
export const ICON_SIZES = { xs: 12, sm: 14, md: 16, lg: 20, xl: 24 } as const;
export type IconSize = keyof typeof ICON_SIZES;

/** Lucide renders 2px strokes by default; 1.75 reads better next to body text in both themes. */
const ICON_STROKE_WIDTH = 1.75;

export type IconProps = Omit<LucideProps, "size" | "ref"> & {
  /** The lucide component, imported by name so the bundler tree-shakes the rest. */
  icon: LucideIcon;
  size?: IconSize;
};

/** Icons inherit `currentColor`; colour them through the parent's text token, not a prop. */
export function Icon({ icon: Component, size = "md", className, ...props }: IconProps) {
  return (
    <Component
      size={ICON_SIZES[size]}
      strokeWidth={ICON_STROKE_WIDTH}
      className={cn("shrink-0", className)}
      aria-hidden={props["aria-label"] ? undefined : true}
      {...props}
    />
  );
}
