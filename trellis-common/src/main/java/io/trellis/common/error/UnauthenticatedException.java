package io.trellis.common.error;

/** No valid credentials were presented. */
public class UnauthenticatedException extends TrellisException {
    public UnauthenticatedException() {
        super(PlatformErrorCode.UNAUTHENTICATED, "Authentication is required");
    }
}
