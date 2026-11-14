import type { ApiErrorBody } from "@/shared/types/api.types";

/** `ApiError.code` when the response carried no error body (proxy page, network failure). */
export const UNKNOWN_ERROR_CODE = "UNKNOWN";

/** `ApiError.status` when no HTTP response arrived at all. */
export const NETWORK_ERROR_STATUS = 0;

/**
 * The only error type the data layer throws. Branch on `code` through the owning entity's
 * `<DOMAIN>_ERRORS` constant, never a string literal; `status` is the coarse fallback.
 */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly correlationId: string | undefined;
  readonly body: ApiErrorBody | undefined;

  constructor(message: string, status: number, body?: ApiErrorBody) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = body?.code ?? UNKNOWN_ERROR_CODE;
    this.correlationId = body?.correlationId;
    this.body = body;
  }

  /** Narrowing helper: `if (ApiError.is(err, AUTH_ERRORS.INVALID_CREDENTIALS)) ...` */
  static is(err: unknown, code?: string): err is ApiError {
    return err instanceof ApiError && (code === undefined || err.code === code);
  }
}
