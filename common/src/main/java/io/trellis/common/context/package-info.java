/**
 * {@link io.trellis.common.context.RequestContext}: who is acting, for which request, at what time. Passed as
 * the first parameter of application-service methods (explicit, testable), never looked up from a static
 * inside business code. {@link io.trellis.common.context.AmbientRequestContext} exists only for the edges
 * (filters, consumers, executors, aspects) that must hand the context across a framework boundary.
 */
package io.trellis.common.context;
