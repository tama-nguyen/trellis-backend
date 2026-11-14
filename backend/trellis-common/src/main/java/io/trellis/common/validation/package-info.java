/**
 * Business validation below the API layer: {@link io.trellis.common.validation.CommandValidator}s run in a
 * {@link io.trellis.common.validation.ValidationPipeline} that collects every {@link
 * io.trellis.common.validation.Violation} and fails once, so clients see all problems in one response and every
 * entry point (REST, GraphQL, consumer) gets the same rules.
 */
package io.trellis.common.validation;
