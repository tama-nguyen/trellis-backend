package io.trellis.platform.web.graphql;

import graphql.ErrorClassification;
import io.trellis.common.error.ErrorCategory;

/**
 * Value of {@code extensions.code} on a GraphQL error: one per HTTP-like class, so a client decides
 * "retry, sign in again, show not found" without parsing messages (plan 07 A3.2). The module's own
 * code ({@code NOTICE_NOT_FOUND}) travels next to it in {@code extensions.errorCode}.
 */
public enum GraphQlErrorCode implements ErrorClassification {
  BAD_REQUEST,
  UNAUTHENTICATED,
  FORBIDDEN,
  NOT_FOUND,
  CONFLICT,
  RATE_LIMITED,
  UNAVAILABLE,
  INTERNAL;

  /** Exhaustive on purpose: a new category must choose its GraphQL code here. */
  public static GraphQlErrorCode of(ErrorCategory category) {
    return switch (category) {
      case VALIDATION,
              METHOD_NOT_ALLOWED,
              NOT_ACCEPTABLE,
              PAYLOAD_TOO_LARGE,
              UNSUPPORTED_MEDIA_TYPE ->
          BAD_REQUEST;
      case UNAUTHENTICATED -> UNAUTHENTICATED;
      case FORBIDDEN -> FORBIDDEN;
      case NOT_FOUND -> NOT_FOUND;
      case CONFLICT, PRECONDITION_FAILED -> CONFLICT;
      case RATE_LIMITED -> RATE_LIMITED;
      case UNAVAILABLE -> UNAVAILABLE;
      case INTERNAL -> INTERNAL;
    };
  }
}
