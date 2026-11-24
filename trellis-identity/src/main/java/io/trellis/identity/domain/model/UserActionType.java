package io.trellis.identity.domain.model;

/** One-time user action represented by a random emailed token. */
public enum UserActionType {
    VERIFY_EMAIL,
    RESET_PASSWORD
}
