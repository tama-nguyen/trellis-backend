package io.trellis.notice.api.error;

import io.trellis.common.error.NotFoundException;

/** Exceptions of the Notice module, so every entry point reports the same code and wording. */
public final class NoticeErrors {
    private NoticeErrors() {}

    /** Missing or not visible: both answer the same 404, never 403. */
    public static NotFoundException notFound(String code) {
        return new NotFoundException(NoticeErrorCode.NOTICE_NOT_FOUND, "Notice " + code + " not found");
    }
}
