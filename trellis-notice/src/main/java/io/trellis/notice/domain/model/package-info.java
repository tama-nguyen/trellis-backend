/**
 * The aggregate and its entities: immutable records whose transitions return the next state. May depend on {@code
 * domain.value}, {@code api} and trellis-common; never on Spring, SQL or the application layer (ArchUnit).
 */
package io.trellis.notice.domain.model;
