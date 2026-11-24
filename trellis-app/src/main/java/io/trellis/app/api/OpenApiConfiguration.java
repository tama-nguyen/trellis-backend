package io.trellis.app.api;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.trellis.common.context.RequestContext;
import io.trellis.common.error.ProblemFields;
import io.trellis.common.error.ProblemTypes;
import java.util.List;
import java.util.Locale;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Shapes the generated OpenAPI document: fixed metadata and server (so the committed file does not
 * depend on the port it was exported from), bearer auth, and the RFC 7807 {@code Problem} schema as
 * the {@code default} response of every operation, so generated clients (TypeScript, Python) get
 * typed errors.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {
  static final String TITLE = "Trellis API";
  static final String VERSION = "v1";
  static final String BEARER = "bearer";
  static final String PROBLEM = "Problem";
  static final String VIOLATION = "Violation";
  static final String DEFAULT_RESPONSE = "default";
  private static final String SCHEMA_REF = "#/components/schemas/";

  /** Clients group operations by tag; the tag is the controller's resource name. */
  private static final String CONTROLLER_SUFFIX = "Controller";

  static {
    // Resolved by the platform's argument resolver, not sent by clients: keep it out of the
    // document.
    SpringDocUtils.getConfig().addRequestWrapperToIgnore(RequestContext.class);
  }

  @Bean
  OpenAPI trellisOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title(TITLE)
                .version(VERSION)
                .description(
                    "Commands and integration API. Errors are RFC 7807 problems "
                        + "(application/problem+json). UI read models are served by GraphQL at /api/graphql."))
        .servers(List.of(new Server().url("/")))
        .components(
            new Components()
                .addSecuritySchemes(
                    BEARER,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme(BEARER)
                        .bearerFormat("JWT")))
        .addSecurityItem(new SecurityRequirement().addList(BEARER));
  }

  @Bean
  OpenApiCustomizer problemResponses() {
    return openApi -> {
      openApi
          .getComponents()
          .addSchemas(VIOLATION, violationSchema())
          .addSchemas(PROBLEM, problemSchema());
      ApiResponse problem =
          new ApiResponse()
              .description("Error (RFC 7807 problem details)")
              .content(
                  new Content()
                      .addMediaType(
                          ProblemTypes.MEDIA_TYPE,
                          new MediaType().schema(new Schema<>().$ref(SCHEMA_REF + PROBLEM))));
      openApi
          .getPaths()
          .values()
          .forEach(
              path ->
                  path.readOperations()
                      .forEach(op -> op.getResponses().addApiResponse(DEFAULT_RESPONSE, problem)));
    };
  }

  /** Tag = resource name ({@code notice}), not the class name ({@code notice-controller}). */
  @Bean
  OperationCustomizer resourceTags() {
    return (operation, handler) -> {
      String simpleName = handler.getBeanType().getSimpleName();
      String resource =
          simpleName.endsWith(CONTROLLER_SUFFIX)
              ? simpleName.substring(0, simpleName.length() - CONTROLLER_SUFFIX.length())
              : simpleName;
      return operation.tags(
          List.of(resource.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT)));
    };
  }

  private static Schema<?> violationSchema() {
    ObjectSchema schema = new ObjectSchema();
    schema
        .addProperty("field", new StringSchema())
        .addProperty("code", new StringSchema())
        .addProperty("message", new StringSchema());
    schema.setRequired(List.of("field", "code", "message"));
    return schema;
  }

  private static Schema<?> problemSchema() {
    ObjectSchema schema = new ObjectSchema();
    schema
        .description("RFC 7807 problem details plus Trellis extensions")
        .addProperty(ProblemFields.TYPE, new StringSchema())
        .addProperty(ProblemFields.TITLE, new StringSchema())
        .addProperty(ProblemFields.STATUS, new IntegerSchema())
        .addProperty(ProblemFields.DETAIL, new StringSchema())
        .addProperty(ProblemFields.INSTANCE, new StringSchema())
        .addProperty(ProblemFields.CODE, new StringSchema())
        .addProperty(ProblemFields.CORRELATION_ID, new StringSchema())
        .addProperty(ProblemFields.TRACE_ID, new StringSchema())
        .addProperty(
            ProblemFields.ERRORS,
            new ArraySchema().items(new Schema<>().$ref(SCHEMA_REF + VIOLATION)));
    schema.setRequired(
        List.of(ProblemFields.TYPE, ProblemFields.TITLE, ProblemFields.STATUS, ProblemFields.CODE));
    return schema;
  }
}
