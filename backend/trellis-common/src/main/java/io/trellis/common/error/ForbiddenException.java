package io.trellis.common.error;

/** The actor is known but lacks the permission. The message must not reveal which permission is missing. */
public class ForbiddenException extends TrellisException {
    public ForbiddenException() {
        super(PlatformErrorCode.ACCESS_DENIED, "You do not have permission to perform this action");
    }
}
