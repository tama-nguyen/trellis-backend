/**
 * Write use cases (decision T16): {@code CourseCommands}, plus one class per large or distinct use case named
 * after it ({@code ArchiveCourse}). Transactional, emit events through the outbox, carry the AOP annotations,
 * {@code RequestContext} first. Depends on {@code domain}, {@code port}, {@code view}, {@code validation} and
 * {@code query.CourseAccess}; never on infrastructure or web.
 */
package io.trellis.course.application.command;
