package io.trellis.common.error;

/** The request conflicts with the current state of the resource. */
public class ConflictException extends TrellisException {
  public ConflictException(ErrorCode errorCode, String message) {
    super(errorCode, message);
  }
}
