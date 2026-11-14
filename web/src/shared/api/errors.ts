"use client";

import { useTranslations } from "next-intl";
import { useCallback } from "react";
import type { MessageKey } from "@/i18n/i18n.types";
import { toast } from "@/ui/Toast";
import { ApiError, NETWORK_ERROR_STATUS } from "@/shared/api/apiError";
import { HTTP_STATUS, PROBLEM_TYPES } from "@/shared/constants/http";

/**
 * Turning any thrown value into a message the user may see.
 *
 * Rule (taken from DataHub's handleGraphQLError): the server's own text is shown only for a
 * validation problem, whose detail is written for the user. Every other message may carry
 * server internals, so it is replaced by copy chosen from the error code or the HTTP status.
 */

/** Per-code messages an entity owns, e.g. `AUTH_ERROR_MESSAGES` in `auth.constants.ts`. */
export type ErrorMessageMap = Readonly<Partial<Record<string, MessageKey>>>;

const STATUS_MESSAGES: ReadonlyArray<readonly [number, MessageKey]> = [
  [NETWORK_ERROR_STATUS, "errors.network"],
  [HTTP_STATUS.UNAUTHORIZED, "errors.unauthorized"],
  [HTTP_STATUS.FORBIDDEN, "errors.forbidden"],
  [HTTP_STATUS.NOT_FOUND, "errors.notFound"],
  [HTTP_STATUS.CONFLICT, "errors.conflict"],
  [HTTP_STATUS.TOO_MANY_REQUESTS, "errors.rateLimited"],
];

export function isValidationError(error: unknown): boolean {
  return (
    error instanceof ApiError &&
    (error.status === HTTP_STATUS.BAD_REQUEST || error.status === HTTP_STATUS.UNPROCESSABLE_ENTITY) &&
    error.body?.type === PROBLEM_TYPES.VALIDATION
  );
}

/** Translator shape accepted here; next-intl's root `useTranslations()` satisfies it. */
type Translate = (key: MessageKey, values?: Record<string, string | number>) => string;

export type ApiErrorMessageOptions = {
  codeMessages?: ErrorMessageMap;
  /** Used when nothing more specific matches. Defaults to `errors.generic`. */
  fallback?: MessageKey;
};

export function getApiErrorMessage(
  error: unknown,
  t: Translate,
  options: ApiErrorMessageOptions = {},
): string {
  const fallback = t(options.fallback ?? "errors.generic");
  if (!(error instanceof ApiError)) return fallback;

  if (isValidationError(error)) {
    // The validator's own text is more specific than anything mapped here, so it wins.
    return error.body?.detail?.trim() || t("errors.validation");
  }
  const byCode = options.codeMessages?.[error.code];
  if (byCode) return t(byCode);

  if (error.status >= HTTP_STATUS.INTERNAL_SERVER_ERROR) {
    return error.correlationId
      ? t("errors.serverWithReference", { reference: error.correlationId })
      : t("errors.server");
  }
  const byStatus = STATUS_MESSAGES.find(([status]) => status === error.status)?.[1];
  return byStatus ? t(byStatus) : fallback;
}

/**
 * Returns `handleApiError(error, options)`: shows the mapped message as an error toast.
 * Use it in mutation `onError`; for whole-page failures render `ErrorState` with
 * `getApiErrorMessage` instead.
 */
export function useApiErrorHandler(): (error: unknown, options?: ApiErrorMessageOptions) => void {
  const t = useTranslations();
  return useCallback(
    (error: unknown, options?: ApiErrorMessageOptions) => {
      toast.error(getApiErrorMessage(error, t as Translate, options));
    },
    [t],
  );
}
