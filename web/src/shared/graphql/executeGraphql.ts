import { ApiError } from "@/shared/api/apiError";
import { post } from "@/shared/api/http";
import { HTTP_STATUS } from "@/shared/constants/http";
import type { TypedDocumentString } from "@/shared/graphql/generated/graphql";
import {
  GRAPHQL_ENDPOINT,
  GRAPHQL_ERROR_STATUS,
  type GraphqlError,
  type GraphqlErrorCode,
  type GraphqlResponse,
} from "@/shared/graphql/graphql.constants";

function isKnownCode(code: string | undefined): code is GraphqlErrorCode {
  return code !== undefined && code in GRAPHQL_ERROR_STATUS;
}

/**
 * A GraphQL error as the `ApiError` the rest of the app already handles: status from
 * `extensions.code`, `code` from `extensions.errorCode` (the module's code, same as REST).
 */
function toApiError(error: GraphqlError): ApiError {
  const extensions = error.extensions ?? {};
  const status = isKnownCode(extensions.code)
    ? GRAPHQL_ERROR_STATUS[extensions.code]
    : HTTP_STATUS.INTERNAL_SERVER_ERROR;
  return new ApiError(error.message, status, {
    status,
    detail: error.message,
    code: extensions.errorCode ?? extensions.code,
    correlationId: extensions.correlationId,
  });
}

/**
 * Runs a generated GraphQL document through `http.ts` (token, refresh, BFF routing). Throws the first
 * error as an `ApiError`: the UI reads one document per screen and treats a failed one as failed,
 * rather than rendering half a read model.
 */
export async function executeGraphql<TResult, TVariables>(
  document: TypedDocumentString<TResult, TVariables>,
  variables: TVariables,
  signal?: AbortSignal,
): Promise<TResult> {
  const response = await post<GraphqlResponse<TResult>>(
    GRAPHQL_ENDPOINT,
    { query: document.toString(), variables },
    { signal },
  );
  const [firstError] = response.errors ?? [];
  if (firstError) throw toApiError(firstError);
  if (!response.data) {
    throw new ApiError("GraphQL response without data", HTTP_STATUS.INTERNAL_SERVER_ERROR);
  }
  return response.data;
}
