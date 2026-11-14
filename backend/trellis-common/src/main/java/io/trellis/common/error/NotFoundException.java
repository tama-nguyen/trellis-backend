package io.trellis.common.error;

/** The addressed resource does not exist, or the caller may not know that it exists. */
public class NotFoundException extends TrellisException {
    public NotFoundException(String message) {
        super(PlatformErrorCode.RESOURCE_NOT_FOUND, message);
    }

    public NotFoundException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
