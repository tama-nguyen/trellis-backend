package io.trellis.notification.domain.policy;

/**
 * What the visibility rule needs to know about the caller, already resolved from permissions by the
 * application layer (the domain does not know about tokens or the authorization service).
 *
 * @param userId the caller's user id, or null for services and the system (they own no
 *     notifications)
 * @param manager holds {@code notification:manage}: sees every notification in every status
 * @param staffReader holds {@code notification:read-staff} (or manage): sees STAFF notifications
 */
public record NotificationReader(String userId, boolean manager, boolean staffReader) {}
