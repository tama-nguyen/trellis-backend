/**
 * Write use cases (decision T16): {@code NoticeCommands}, plus one class per large or distinct use case named after
 * it ({@code PublishNotice}). Transactional, emit events through the outbox, carry the AOP annotations,
 * {@code RequestContext} first. Depends on {@code domain}, {@code port}, {@code view}, {@code validation} and
 * {@code query.NoticeAccess}; never on infrastructure or web.
 */
package io.trellis.notice.application.command;
