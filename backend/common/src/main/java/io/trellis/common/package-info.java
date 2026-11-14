/**
 * Framework-free building blocks shared by every backend module: the error model, {@link
 * io.trellis.common.context.RequestContext}, actors, ids, backoff, cursors, validation and permission value types.
 *
 * <p>Rule: nothing in this module may import Spring, Jackson or any other framework, so it stays usable from
 * plain tests, tools and future non-Spring workers. ArchUnit enforces this.
 */
package io.trellis.common;
