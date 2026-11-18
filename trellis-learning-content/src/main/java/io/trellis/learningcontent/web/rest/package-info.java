/**
 * REST controllers and their request/response records: translate HTTP (ETag, Idempotency-Key, cursor pages) and
 * call {@code application}; never authorise, never open transactions, never touch infrastructure (ArchUnit).
 */
package io.trellis.learningcontent.web.rest;
