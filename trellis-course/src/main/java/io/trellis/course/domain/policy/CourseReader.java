package io.trellis.course.domain.policy;

/**
 * What the visibility rule needs to know about the caller, already resolved from permissions by the application
 * layer (the domain does not know about tokens or the authorization service).
 *
 * @param userId the caller's user id, or null for services and the system (they own no courses)
 * @param manager holds {@code course:manage}: sees every course in every status
 */
public record CourseReader(String userId, boolean manager) {}
