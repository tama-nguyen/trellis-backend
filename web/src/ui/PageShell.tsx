import type { ReactNode } from "react";
import { cn } from "./cn";

/**
 * The five states every screen ships. A screen with only its `ready` state is not done.
 * `notFound` covers "does not exist" and "not yours": both look the same to the user.
 */
export const SCREEN_STATES = {
  LOADING: "loading",
  EMPTY: "empty",
  ERROR: "error",
  NOT_FOUND: "notFound",
  READY: "ready",
} as const;
export type ScreenState = (typeof SCREEN_STATES)[keyof typeof SCREEN_STATES];

export type PageShellProps = {
  title?: ReactNode;
  description?: ReactNode;
  /** Buttons beside the title. */
  actions?: ReactNode;
  /** Defaults to `ready`; pages driven by a query derive it with `screenStateOf`. */
  state?: ScreenState;
  /** Skeleton in the shape of the real content. */
  loading?: ReactNode;
  empty?: ReactNode;
  error?: ReactNode;
  notFound?: ReactNode;
  /** Ready content. */
  children?: ReactNode;
  className?: string;
  headerClassName?: string;
  bodyClassName?: string;
};

/** Page container: the max-w-7xl column every page uses, a header, and the body for the current state. */
export function PageShell({
  title,
  description,
  actions,
  state = SCREEN_STATES.READY,
  loading,
  empty,
  error,
  notFound,
  children,
  className,
  headerClassName,
  bodyClassName,
}: PageShellProps) {
  const body: Record<ScreenState, ReactNode> = {
    loading,
    empty,
    error,
    notFound,
    ready: children,
  };
  return (
    <main className={cn("mx-auto w-full max-w-7xl px-4 py-8 md:px-6", className)}>
      {title || actions ? (
        <header className={cn("mb-6 flex flex-wrap items-start justify-between gap-4", headerClassName)}>
          <div className="flex min-w-0 flex-col gap-1">
            {title ? <h1 className="text-2xl font-semibold text-fg">{title}</h1> : null}
            {description ? <p className="text-sm text-fg-muted">{description}</p> : null}
          </div>
          {actions ? <div className="flex flex-wrap gap-2">{actions}</div> : null}
        </header>
      ) : null}
      <div aria-busy={state === SCREEN_STATES.LOADING || undefined} className={bodyClassName}>
        {body[state]}
      </div>
    </main>
  );
}
