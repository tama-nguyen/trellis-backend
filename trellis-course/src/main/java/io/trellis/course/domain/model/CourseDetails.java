package io.trellis.course.domain.model;

/**
 * The editable part of a course; the command of both create and update. Checked by the application's validation
 * pipeline, not here, so every violation is reported at once.
 *
 * <p>Generated skeleton: {@code name} and {@code description} are a placeholder - replace them with the fields the
 * real course has, and keep their limits next to the column widths of the changeset.
 */
public record CourseDetails(String name, String description) {
    /** Limits shared with the database columns and the API contract. */
    public static final int NAME_MAX = 200;

    public static final int DESCRIPTION_MAX = 20_000;
}
