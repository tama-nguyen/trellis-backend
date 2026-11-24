package io.trellis.platform.web.graphql;

import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * {@code scalar DateTime}: an ISO-8601 instant in UTC ({@code 2026-11-14T10:00:00Z}), the same text
 * REST and the event contracts use. Declared once by the platform; module schemas only reference
 * it.
 */
public final class DateTimeScalar {
  public static final String NAME = "DateTime";

  private DateTimeScalar() {}

  public static GraphQLScalarType create() {
    return GraphQLScalarType.newScalar()
        .name(NAME)
        .description("ISO-8601 instant in UTC, e.g. 2026-11-14T10:00:00Z")
        .coercing(new InstantCoercing())
        .build();
  }

  private static final class InstantCoercing implements Coercing<Instant, String> {
    @Override
    public String serialize(Object value, GraphQLContext context, Locale locale) {
      return switch (value) {
        case Instant instant -> instant.toString();
        case OffsetDateTime odt -> odt.toInstant().toString();
        default -> throw new CoercingSerializeException("Expected an Instant");
      };
    }

    @Override
    public Instant parseValue(Object input, GraphQLContext context, Locale locale) {
      if (input instanceof String text) {
        return parse(text, CoercingParseValueException::new);
      }
      throw new CoercingParseValueException("Expected an ISO-8601 string");
    }

    @Override
    public Instant parseLiteral(
        Value<?> input, CoercedVariables variables, GraphQLContext context, Locale locale) {
      if (input instanceof StringValue text) {
        return parse(text.getValue(), CoercingParseLiteralException::new);
      }
      throw new CoercingParseLiteralException("Expected an ISO-8601 string");
    }

    private static <E extends RuntimeException> Instant parse(
        String text, java.util.function.Function<String, E> error) {
      try {
        return OffsetDateTime.parse(text).toInstant();
      } catch (DateTimeParseException e) {
        throw error.apply("Not an ISO-8601 date-time");
      }
    }
  }
}
