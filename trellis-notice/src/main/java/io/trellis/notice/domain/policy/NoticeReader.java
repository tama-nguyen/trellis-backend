package io.trellis.notice.domain.policy;

/**
 * What the visibility rule needs to know about the caller, already resolved from permissions by the
 * application layer (the domain does not know about tokens or the authorization service).
 *
 * @param userId the caller's user id, or null for services and the system (they own no notices)
 * @param manager holds {@code notice:manage}: sees every notice in every status
 * @param staffReader holds {@code notice:read-staff} (or manage): sees STAFF notices
 */
public record NoticeReader(String userId, boolean manager, boolean staffReader) {}
