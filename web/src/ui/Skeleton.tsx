import { cn } from "./cn";

export type SkeletonProps = {
  /** Size and shape come from className (`h-4 w-40`, `h-24 rounded-card`), matching the real content. */
  className?: string;
};

/** A placeholder in the shape of what is loading. Decorative: the region around it announces busy state. */
export function Skeleton({ className }: SkeletonProps) {
  return <div aria-hidden className={cn("animate-pulse rounded-control bg-surface-sunken", className)} />;
}
