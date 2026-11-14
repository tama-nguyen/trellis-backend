"use client";

import { CircleAlert, CircleCheck, Info, TriangleAlert, X, type LucideIcon } from "lucide-react";
import { useSyncExternalStore, type ReactNode } from "react";
import { Icon } from "./Icon";
import { Spinner } from "./Spinner";
import { cn } from "./cn";

/**
 * Toasts through a module-level store, so any code (a mutation callback, `handleApiError`) can
 * raise one without a context. `<ToastViewport />` is mounted once in the app providers.
 *
 * Messages are always already translated. Pass a `key` to replace a toast in place: show
 * `toast.loading(msg, { key })`, then `toast.success(done, { key })` swaps it rather than stacking.
 */

export const TOAST_KINDS = {
  SUCCESS: "success",
  ERROR: "error",
  INFO: "info",
  WARNING: "warning",
  LOADING: "loading",
} as const;
export type ToastKind = (typeof TOAST_KINDS)[keyof typeof TOAST_KINDS];

/** Milliseconds on screen per kind; 0 keeps the toast until replaced or dismissed. */
const DEFAULT_DURATION_MS: Record<ToastKind, number> = {
  success: 3000,
  info: 3000,
  warning: 4000,
  error: 5000,
  loading: 0,
};
const MAX_TOASTS = 5;

export type ToastOptions = {
  key?: string;
  durationMs?: number;
};

type ToastItem = {
  key: string;
  kind: ToastKind;
  message: ReactNode;
};

let toasts: readonly ToastItem[] = [];
const listeners = new Set<() => void>();
const timers = new Map<string, ReturnType<typeof setTimeout>>();
let sequence = 0;

function emit(next: readonly ToastItem[]): void {
  toasts = next;
  for (const listener of listeners) listener();
}

function dismiss(key?: string): void {
  if (key === undefined) {
    for (const timer of timers.values()) clearTimeout(timer);
    timers.clear();
    emit([]);
    return;
  }
  const timer = timers.get(key);
  if (timer) clearTimeout(timer);
  timers.delete(key);
  emit(toasts.filter((item) => item.key !== key));
}

function show(kind: ToastKind, message: ReactNode, options: ToastOptions = {}): string {
  sequence += 1;
  const key = options.key ?? `toast-${sequence}`;
  const item: ToastItem = { key, kind, message };
  const existing = toasts.findIndex((toastItem) => toastItem.key === key);
  const next =
    existing >= 0 ? toasts.map((toastItem) => (toastItem.key === key ? item : toastItem)) : [...toasts, item];
  emit(next.slice(-MAX_TOASTS));

  const previousTimer = timers.get(key);
  if (previousTimer) clearTimeout(previousTimer);
  timers.delete(key);
  const duration = options.durationMs ?? DEFAULT_DURATION_MS[kind];
  if (duration > 0)
    timers.set(
      key,
      setTimeout(() => dismiss(key), duration),
    );
  return key;
}

export const toast = {
  success: (message: ReactNode, options?: ToastOptions) => show(TOAST_KINDS.SUCCESS, message, options),
  error: (message: ReactNode, options?: ToastOptions) => show(TOAST_KINDS.ERROR, message, options),
  info: (message: ReactNode, options?: ToastOptions) => show(TOAST_KINDS.INFO, message, options),
  warning: (message: ReactNode, options?: ToastOptions) => show(TOAST_KINDS.WARNING, message, options),
  loading: (message: ReactNode, options?: ToastOptions) => show(TOAST_KINDS.LOADING, message, options),
  dismiss,
};

const subscribe = (listener: () => void) => {
  listeners.add(listener);
  return () => listeners.delete(listener);
};
const getSnapshot = () => toasts;
const EMPTY: readonly ToastItem[] = [];
const getServerSnapshot = () => EMPTY;

const KIND_ICON: Record<Exclude<ToastKind, "loading">, LucideIcon> = {
  success: CircleCheck,
  error: CircleAlert,
  info: Info,
  warning: TriangleAlert,
};

const KIND_ICON_CLASS: Record<ToastKind, string> = {
  success: "text-success",
  error: "text-danger",
  info: "text-info",
  warning: "text-warning",
  loading: "text-fg-muted",
};

export type ToastViewportProps = {
  /** Accessible name of each toast's close button (already translated). */
  dismissLabel: string;
  className?: string;
};

export function ToastViewport({ dismissLabel, className }: ToastViewportProps) {
  const items = useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);
  return (
    <div
      className={cn(
        "pointer-events-none fixed right-4 bottom-4 z-toast flex w-80 max-w-[calc(100vw-2rem)] flex-col gap-2",
        className,
      )}
    >
      {items.map((item) => (
        <div
          key={item.key}
          // Errors and warnings interrupt; the rest wait for a pause in speech.
          role={item.kind === TOAST_KINDS.ERROR || item.kind === TOAST_KINDS.WARNING ? "alert" : "status"}
          aria-live={
            item.kind === TOAST_KINDS.ERROR || item.kind === TOAST_KINDS.WARNING ? "assertive" : "polite"
          }
          className="pointer-events-auto flex animate-slide-in items-start gap-3 rounded-card border border-border bg-surface-raised p-3 text-sm text-fg shadow-raised"
        >
          <span className={cn("mt-0.5", KIND_ICON_CLASS[item.kind])}>
            {item.kind === TOAST_KINDS.LOADING ? (
              <Spinner size="md" />
            ) : (
              <Icon icon={KIND_ICON[item.kind]} size="md" />
            )}
          </span>
          <div className="min-w-0 flex-1 break-words">{item.message}</div>
          <button
            type="button"
            aria-label={dismissLabel}
            onClick={() => dismiss(item.key)}
            className="rounded-control p-0.5 text-fg-subtle hover:bg-surface-hover"
          >
            <Icon icon={X} size="sm" />
          </button>
        </div>
      ))}
    </div>
  );
}
