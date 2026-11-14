import { ApiError } from "@/shared/api/apiError";
import { HTTP_STATUS } from "@/shared/constants/http";
import { SCREEN_STATES, type ScreenState } from "@/ui/PageShell";

type QueryLike = {
  isPending: boolean;
  isError: boolean;
  error: unknown;
};

/**
 * Derives the PageShell state from a query. Computed during render, never stored in state.
 * A 404 maps to `notFound`; the backend answers 404 (not 403) for another user's private
 * resource, so the UI cannot reveal that it exists.
 */
export function screenStateOf(
  query: QueryLike,
  { isEmpty = false }: { isEmpty?: boolean } = {},
): ScreenState {
  if (query.isPending) return SCREEN_STATES.LOADING;
  if (query.isError) {
    return query.error instanceof ApiError && query.error.status === HTTP_STATUS.NOT_FOUND
      ? SCREEN_STATES.NOT_FOUND
      : SCREEN_STATES.ERROR;
  }
  return isEmpty ? SCREEN_STATES.EMPTY : SCREEN_STATES.READY;
}
