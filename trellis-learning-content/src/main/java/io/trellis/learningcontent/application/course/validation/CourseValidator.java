package io.trellis.learningcontent.application.course.validation;

import io.trellis.common.context.RequestContext;
import io.trellis.common.validation.CommandValidator;
import io.trellis.common.validation.Violations;
import io.trellis.learningcontent.domain.course.model.CourseDetails;
import org.springframework.stereotype.Component;

/**
 * Business rules of course details, shared by create and update and by every entry point (REST, GraphQL, a
 * future consumer). Bean Validation on the request DTO only checks shape; these rules are the contract. Every
 * rule reports a violation instead of throwing, so the caller gets all of them at once.
 *
 * <p>Generated skeleton: the rules below are the required/length checks every field needs. Add the real ones
 * (uniqueness, cross-field, state-dependent) here, not in the controller.
 */
@Component
public class CourseValidator implements CommandValidator<CourseDetails> {
    static final String FIELD_NAME = "name";
    static final String FIELD_DESCRIPTION = "description";
    static final String REQUIRED = "REQUIRED";
    static final String TOO_LONG = "TOO_LONG";
    static final String CONTROL_CHARACTERS = "CONTROL_CHARACTERS";

    @Override
    public void validate(RequestContext ctx, CourseDetails details, Violations violations) {
        String name = details.name();
        violations.check(name != null && !name.isBlank(), FIELD_NAME, REQUIRED, "name is required");
        if (name != null) {
            violations.check(
                    name.length() <= CourseDetails.NAME_MAX,
                    FIELD_NAME,
                    TOO_LONG,
                    "name must be at most " + CourseDetails.NAME_MAX + " characters");
            // A name ends up in list rows, mail subjects and event payloads; line breaks there break layouts.
            violations.check(
                    name.chars().noneMatch(Character::isISOControl),
                    FIELD_NAME,
                    CONTROL_CHARACTERS,
                    "name must be a single line");
        }
        String description = details.description();
        violations.check(
                description != null && !description.isBlank(), FIELD_DESCRIPTION, REQUIRED, "description is required");
        if (description != null) {
            violations.check(
                    description.length() <= CourseDetails.DESCRIPTION_MAX,
                    FIELD_DESCRIPTION,
                    TOO_LONG,
                    "description must be at most " + CourseDetails.DESCRIPTION_MAX + " characters");
        }
    }
}
