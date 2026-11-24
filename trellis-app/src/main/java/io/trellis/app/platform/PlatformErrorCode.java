package io.trellis.app.platform;

import io.trellis.common.error.ErrorCategory;
import io.trellis.common.error.ErrorCode;

/** Stable wire codes for the operator APIs of this deployable. */
public enum PlatformErrorCode implements ErrorCode {
    INVALID_PAGE_SIZE(ErrorCategory.VALIDATION, "Invalid page size"),
    INVALID_TIME_RANGE(ErrorCategory.VALIDATION, "Invalid time range"),
    INVALID_EXPIRY(ErrorCategory.VALIDATION, "Invalid expiry"),
    ROLE_GRANT_NOT_FOUND(ErrorCategory.NOT_FOUND, "Role grant not found");

    private final ErrorCategory category;
    private final String title;

    PlatformErrorCode(ErrorCategory category, String title) {
        this.category = category;
        this.title = title;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public ErrorCategory category() {
        return category;
    }

    @Override
    public String title() {
        return title;
    }
}
