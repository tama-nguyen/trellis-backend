package io.trellis.identity.domain.model;

/** Lifecycle of a native Trellis user. */
public enum UserStatus {
    PENDING_VERIFICATION,
    ACTIVE,
    DISABLED
}
