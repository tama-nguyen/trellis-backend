package io.trellis.app.platform.application.support;

import io.trellis.app.platform.PlatformErrorCode;
import io.trellis.common.error.TrellisException;

/**
 * The page-size rule of the operator APIs, enforced below the controller so GraphQL and tests share
 * it.
 */
public final class PageSize {
  /** Kept as text as well, because a controller annotation needs a compile-time constant. */
  public static final int DEFAULT = 50;

  public static final String DEFAULT_TEXT = "50";
  public static final int MAX = 100;

  private PageSize() {}

  public static void require(int first) {
    if (first < 1 || first > MAX) {
      throw new TrellisException(
          PlatformErrorCode.INVALID_PAGE_SIZE, "first must be between 1 and " + MAX);
    }
  }
}
