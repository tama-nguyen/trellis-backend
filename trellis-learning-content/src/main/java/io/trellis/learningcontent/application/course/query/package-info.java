/**
 * Read use cases (decision T16): {@code CourseQueries} (read-only transactions returning views), their criteria,
 * and {@code CourseAccess}, which applies the domain visibility rule to a request. Never on infrastructure or web.
 */
package io.trellis.learningcontent.application.course.query;
