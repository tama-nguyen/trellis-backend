"""Trellis new-module generator: writes a working business module and wires it into the build. Stdlib only.

The module it writes is a copy of `trellis-notification` with the names changed and the sample-only parts
(event payload, read model, projection, rebuilder) left out: the fixed T17 sub-packages
in every one of them, and a minimal CRUD that compiles, boots and answers HTTP on the first run - list with
cursor paging, get by code, create, update with ETag/If-Match, archive (the platform never hard-deletes).

    build-logic/scripts/dev/new-module.sh --name course --aggregate Course --plural courses
    build-logic/scripts/dev/new-module.sh --name course --dry-run

What it writes and changes:

  trellis-<name>/                         the module (build file, 17 packages, resources, README)
  settings.gradle.kts                     the project in `modules`
  trellis-app/build.gradle.kts            implementation(project(":trellis-<name>"))
  .../db/changelog/db.changelog-schema.yaml   the include of the module's changeset

It does NOT grant the new permissions to anyone: a `role_permission` changeset of your own maps them to the roles of one
environment, so it belongs in that environment's profile file (`application-dev.yml` locally), not in the base
`application.yml`, which holds only what every profile shares. Until a role your users hold names
`<name>:read` / `<name>:manage`, every call to the new module answers 403.

Afterwards it runs `:trellis-<name>:spotlessApply` on the new module (`--skip-format` to skip): Google Java Format
wraps on line length, so the canonical formatting of generated code depends on how long the names are.

Nothing is written until every file has been rendered: the module goes to a temp directory next to the
repo root and is moved into place at the end, so a failure never leaves half a module behind.

Both APIs are generated (REST and GraphQL), so `contracts/openapi/openapi.json` and
`contracts/graphql/schema.graphqls` become stale - they are committed, drift-checked files (decision T12).
The script therefore runs `./gradlew :trellis-app:exportApiContracts` for you at the end (needs Docker);
`--skip-contracts` leaves it to you, and then `./gradlew build` fails on `checkApiContracts` until you run it.
"""

from __future__ import annotations

import argparse
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

REPO = Path(__file__).resolve().parents[3]
SETTINGS = REPO / "settings.gradle.kts"
APP_BUILD = REPO / "trellis-app" / "build.gradle.kts"
APP_RESOURCES = REPO / "trellis-app" / "src" / "main" / "resources"
MASTER_CHANGELOG = APP_RESOURCES / "db" / "changelog" / "db.changelog-schema.yaml"

NAME_PATTERN = re.compile(r"^[a-z][a-z0-9]*$")
AGGREGATE_PATTERN = re.compile(r"^[A-Z][A-Za-z0-9]*$")
PREFIX_PATTERN = re.compile(r"^[A-Z]{2,4}$")
SLUG_PATTERN = re.compile(r"^[a-z][a-z0-9]*(-[a-z0-9]+)*$")

# The reference code is <prefix>-XXXX-XXXX-XXXX: the prefix plus three separators and twelve symbols.
CODE_BODY_LENGTH = 15

SETTINGS_ANCHOR = '    "trellis-app",'
APP_BUILD_ANCHOR = "  // Business modules\n"
CHANGELOG_ANCHOR = "  # Business modules: add `- include: { file: db/changelog/<module>/NNN-<change>.sql }` below.\n"


def fail(msg: str) -> None:
    print(f"new-module: {msg}", file=sys.stderr)
    sys.exit(2)


class Names:
    """Every spelling of the module's and the aggregate's name the templates need.

    A module is a group of business features with one lifecycle, not one aggregate (decision T26), so the
    module's name and the aggregate's name are two different things:

    * ``slug`` is the module: the directory, the Gradle project (minus the prefix), the changelog and GraphQL
      resource folders. It may contain hyphens (``learning-content``).
    * ``pkg`` is the same module as a Java package segment, hyphens removed (``learningcontent``). Nested
      packages are deliberately not used: ArchUnit tells modules apart by this one segment, so
      ``io.trellis.learning.content`` would be read as part of ``io.trellis.learning``.
    * ``name`` is the aggregate in lower case (``course``): the table, the permission prefix, audit actions,
      metric names, local variables and prose.
    * ``features`` groups ``domain/`` and ``application/`` when a module holds more than one feature. The
      first one owns the generated skeleton; the others are packages to fill in.
    """

    def __init__(
        self,
        name: str,
        aggregate: str,
        plural: str,
        prefix: str,
        slug: str | None = None,
        features: list[str] | None = None,
    ) -> None:
        self.name = name  # course     table, permission prefix, audit actions, prose
        self.agg = aggregate  # Course     class-name prefix, GraphQL type, aggregate type
        self.plural = plural  # courses    REST path, GraphQL list field
        self.prefix = prefix  # CO         reference-code prefix
        self.slug = slug or name  # learning-content   directory, Gradle project, resource folders
        self.pkg = self.slug.replace("-", "")  # learningcontent   the Java package segment
        self.features = features or []  # [course, lesson, ...]  grouping of domain/ and application/
        self.feature = self.features[0] if self.features else None
        self.mod = "".join(part[:1].upper() + part[1:] for part in self.slug.split("-"))
        self.project = f"trellis-{self.slug}"
        self.code_column = len(prefix) + CODE_BODY_LENGTH

    def render(self, text: str) -> str:
        return (
            text.replace("__NAME__", self.name)
            .replace("__AGG__", self.agg)
            .replace("__PLURAL__", self.plural)
            .replace("__PREFIX__", self.prefix)
            .replace("__UPPER__", self.name.upper())
            .replace("__CODE_COLUMN__", str(self.code_column))
            .replace("__SLUG__", self.slug)
            .replace("__PKG__", self.pkg)
            .replace("__MOD__", self.mod)
            .replace("__TB__", '"""')
        )

    def featurize(self, text: str) -> str:
        """Insert the feature segment into ``domain`` and ``application``, in paths and in package names."""
        if self.feature is None:
            return text
        for layer in ("domain", "application"):
            text = text.replace(f"io.trellis.{self.pkg}.{layer}.", f"io.trellis.{self.pkg}.{layer}.{self.feature}.")
            text = text.replace(f"/{layer}/", f"/{layer}/{self.feature}/")
        return text


# --------------------------------------------------------------------------- templates
# Keys are paths under the module directory; `__AGG__` in a path is replaced like in the content.
# The generator runs Spotless so generated sources follow the pinned Google Java Format version.

BUILD_GRADLE = """\
// Business module __NAME__ (generated by build-logic/scripts/dev/new-module.sh from trellis-notification).
// Packages: api -> domain -> application -> infrastructure / web.
plugins {
  id("trellis.spring-module")
}

dependencies {
  implementation(project(":trellis-common"))
  implementation(project(":trellis-platform"))
  implementation(project(":trellis-platform-events"))
  implementation(project(":trellis-platform-web"))
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("io.micrometer:micrometer-observation")
  implementation("org.springframework:spring-tx")
}
"""

MODULE_CLASS = """\
package io.trellis.__PKG__;

import io.trellis.common.validation.CommandValidator;
import io.trellis.common.validation.ValidationPipeline;
import io.trellis.__PKG__.domain.model.__AGG__Details;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * Registers the module: adding the Gradle dependency to {@code trellis-app} is all the application needs. Scans
 * only the sub-packages, so this class is not picked up a second time.
 */
@AutoConfiguration
@ComponentScan(
        basePackages = {
            "io.trellis.__PKG__.application",
            "io.trellis.__PKG__.infrastructure",
            "io.trellis.__PKG__.web"
        })
public class __MOD__Module {
    @Bean
    ValidationPipeline<__AGG__Details> __NAME__ValidationPipeline(
            final List<CommandValidator<__AGG__Details>> validators) {
        return new ValidationPipeline<>(validators);
    }
}
"""



JAVA_FILES = {
    # ------------------------------------------------------------------ api
    "api/dto/__AGG__Status.java": """\
package io.trellis.__PKG__.api.dto;

/** Lifecycle: DRAFT -> ACTIVE -> ARCHIVED, and ARCHIVED -> ACTIVE again. Replace with the real states. */
public enum __AGG__Status {
    DRAFT,
    ACTIVE,
    ARCHIVED
}
""",
    "api/error/__AGG__ErrorCode.java": """\
package io.trellis.__PKG__.api.error;

import io.trellis.common.error.ErrorCategory;
import io.trellis.common.error.ErrorCode;

/** Error codes of the __AGG__ module; part of the API contract (clients branch on them). */
public enum __AGG__ErrorCode implements ErrorCode {
    /** Missing, or not visible to the caller (both answer the same, so existence is never confirmed). */
    __UPPER___NOT_FOUND(ErrorCategory.NOT_FOUND, "__AGG__ not found"),
    /** The requested status change is not allowed from the current status. */
    __UPPER___INVALID_TRANSITION(ErrorCategory.CONFLICT, "__AGG__ status does not allow this change"),
    /** If-Match does not name the current version: someone else changed the __NAME__. */
    __UPPER___VERSION_MISMATCH(ErrorCategory.PRECONDITION_FAILED, "__AGG__ was changed by someone else"),
    /** If-Match is not a version ETag of this API. */
    __UPPER___INVALID_PRECONDITION(ErrorCategory.VALIDATION, "If-Match must be an ETag returned by this API");

    private final ErrorCategory category;
    private final String title;

    __AGG__ErrorCode(ErrorCategory category, String title) {
        this.category = category;
        this.title = title;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public ErrorCategory category() {
        return category;
    }

    @Override
    public String title() {
        return title;
    }
}
""",
    "api/error/__AGG__Errors.java": """\
package io.trellis.__PKG__.api.error;

import io.trellis.common.error.NotFoundException;

/** Exceptions of the __AGG__ module, so every entry point reports the same code and wording. */
public final class __AGG__Errors {
    private __AGG__Errors() {}

    /** Missing or not visible: both answer the same 404, never 403. */
    public static NotFoundException notFound(String code) {
        return new NotFoundException(__AGG__ErrorCode.__UPPER___NOT_FOUND, "__AGG__ " + code + " not found");
    }
}
""",
    "api/security/__AGG__Permissions.java": """\
package io.trellis.__PKG__.api.security;

import io.trellis.common.security.PermissionCatalog;

/**
 * Permissions of the __AGG__ module. Roles are seeded through {@code
 * role_permission}, seeded by a changeset of this module; a permission no catalog declares is simply never granted, and a {@code
 * @RequiresPermission} typo fails startup.
 */
@PermissionCatalog
public final class __AGG__Permissions {
    /** Read the __PLURAL__ a caller is allowed to see. */
    public static final String READ = "__NAME__:read";

    /** Create, edit and archive __PLURAL__; see drafts and archived __PLURAL__. */
    public static final String MANAGE = "__NAME__:manage";

    private __AGG__Permissions() {}
}
""",
    # ------------------------------------------------------------------ domain
    "domain/model/__AGG__.java": """\
package io.trellis.__PKG__.domain.model;

import io.trellis.common.error.ConflictException;
import io.trellis.__PKG__.api.dto.__AGG__Status;
import io.trellis.__PKG__.api.error.__AGG__ErrorCode;
import io.trellis.__PKG__.domain.value.__AGG__Code;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The __AGG__ aggregate. Immutable: each transition returns the next state with {@code version + 1}; the
 * repository writes it only if the stored version is still the one that was read (optimistic concurrency).
 *
 * <p>Generated skeleton: add the fields and transitions of the real aggregate here, and keep every rule that can
 * be decided from the aggregate alone in this class (not in the use case, not in the controller).
 *
 * @param version starts at 1, increases by one per change; exposed as the ETag
 */
public record __AGG__(
        UUID id,
        __AGG__Code code,
        __AGG__Details details,
        __AGG__Status status,
        String ownerId,
        long version,
        Instant createdAt,
        Instant updatedAt,
        Instant activatedAt,
        Instant archivedAt) {
    public static final String AGGREGATE_TYPE = "__AGG__";
    static final long FIRST_VERSION = 1;

    public __AGG__ {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(details, "details");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(ownerId, "ownerId");
    }

    public static __AGG__ draft(UUID id, __AGG__Code code, __AGG__Details details, String ownerId, Instant now) {
        return new __AGG__(id, code, details, __AGG__Status.DRAFT, ownerId, FIRST_VERSION, now, now, null, null);
    }

    /** Drafts and active __PLURAL__ can be edited; an archived one is frozen until it is activated again. */
    public __AGG__ edit(__AGG__Details newDetails, Instant now) {
        require(status != __AGG__Status.ARCHIVED, "An archived __NAME__ cannot be edited");
        return new __AGG__(
                id, code, newDetails, status, ownerId, version + 1, createdAt, now, activatedAt, archivedAt);
    }

    public __AGG__ activate(Instant now) {
        require(status != __AGG__Status.ACTIVE, "The __NAME__ is already active");
        return new __AGG__(
                id,
                code,
                details,
                __AGG__Status.ACTIVE,
                ownerId,
                version + 1,
                createdAt,
                now,
                activatedAt == null ? now : activatedAt,
                null);
    }

    /** The platform archives instead of deleting: the row stays auditable and a cursor page never shifts. */
    public __AGG__ archive(Instant now) {
        require(status != __AGG__Status.ARCHIVED, "The __NAME__ is already archived");
        return new __AGG__(
                id, code, details, __AGG__Status.ARCHIVED, ownerId, version + 1, createdAt, now, activatedAt, now);
    }

    public boolean isOwnedBy(String actorId) {
        return ownerId.equals(actorId);
    }

    private static void require(boolean allowed, String message) {
        if (!allowed) {
            throw new ConflictException(__AGG__ErrorCode.__UPPER___INVALID_TRANSITION, message);
        }
    }
}
""",
    "domain/model/__AGG__Details.java": """\
package io.trellis.__PKG__.domain.model;

/**
 * The editable part of a __NAME__; the command of both create and update. Checked by the application's validation
 * pipeline, not here, so every violation is reported at once.
 *
 * <p>Generated skeleton: {@code name} and {@code description} are a placeholder - replace them with the fields the
 * real __NAME__ has, and keep their limits next to the column widths of the changeset.
 */
public record __AGG__Details(String name, String description) {
    /** Limits shared with the database columns and the API contract. */
    public static final int NAME_MAX = 200;

    public static final int DESCRIPTION_MAX = 20_000;
}
""",
    "domain/value/__AGG__Code.java": """\
package io.trellis.__PKG__.domain.value;

import io.trellis.common.id.ReferenceCode;
import io.trellis.common.id.ReferenceCodeGenerator;
import io.trellis.__PKG__.api.error.__AGG__Errors;
import java.util.Optional;

/**
 * Public identifier of a __NAME__: a reference code {@code __PREFIX__-XXXX-XXXX-XXXX}. The primary key stays a
 * UUIDv7; this is the id in URLs, event payloads and support conversations.
 *
 * @param value canonical form
 */
public record __AGG__Code(String value) {
    public static final String PREFIX = "__PREFIX__";

    public __AGG__Code {
        if (!new ReferenceCode(value).hasPrefix(PREFIX)) {
            throw new IllegalArgumentException("Not a __NAME__ code: " + value);
        }
    }

    public static __AGG__Code generate(ReferenceCodeGenerator generator) {
        return new __AGG__Code(generator.next().value());
    }

    /** Lenient parse of user input (case, O/0, I/L/1); empty when it cannot be a __NAME__ code. */
    public static Optional<__AGG__Code> parse(String input) {
        return ReferenceCode.parse(PREFIX, input).map(code -> new __AGG__Code(code.value()));
    }

    /** {@link #parse}, or the module's 404 when the input cannot be a __NAME__ code (it then cannot exist). */
    public static __AGG__Code parseOrNotFound(String input) {
        return parse(input).orElseThrow(() -> __AGG__Errors.notFound(String.valueOf(input)));
    }

    @Override
    public String toString() {
        return value;
    }
}
""",
    "domain/policy/__AGG__Reader.java": """\
package io.trellis.__PKG__.domain.policy;

/**
 * What the visibility rule needs to know about the caller, already resolved from permissions by the application
 * layer (the domain does not know about tokens or the authorization service).
 *
 * @param userId the caller's user id, or null for services and the system (they own no __PLURAL__)
 * @param manager holds {@code __NAME__:manage}: sees every __NAME__ in every status
 */
public record __AGG__Reader(String userId, boolean manager) {}
""",
    "domain/policy/__AGG__Visibility.java": """\
package io.trellis.__PKG__.domain.policy;

import io.trellis.__PKG__.api.dto.__AGG__Status;
import io.trellis.__PKG__.domain.model.__AGG__;
import java.util.EnumSet;
import java.util.Set;

/**
 * Who may see a __NAME__: a manager sees every status, everyone else sees ACTIVE ones plus their own. A __NAME__
 * the reader may not see must be reported exactly like a missing one (404), so its existence leaks nothing. List
 * queries apply the same rule in SQL ({@code __AGG__Filter}), never as a filter after the page.
 */
public final class __AGG__Visibility {
    /** The only status a non-manager sees (besides their own __PLURAL__). */
    public static final Set<__AGG__Status> READER_STATUSES = EnumSet.of(__AGG__Status.ACTIVE);

    private __AGG__Visibility() {}

    public static boolean canSee(__AGG__Reader reader, __AGG__ __NAME__) {
        if (reader.manager() || (reader.userId() != null && __NAME__.isOwnedBy(reader.userId()))) {
            return true;
        }
        return READER_STATUSES.contains(__NAME__.status());
    }
}
""",
    # ------------------------------------------------------------------ application
    "application/port/__AGG__Repository.java": """\
package io.trellis.__PKG__.application.port;

import io.trellis.common.page.CursorPage;
import io.trellis.__PKG__.domain.model.__AGG__;
import io.trellis.__PKG__.domain.value.__AGG__Code;
import java.util.Optional;

/** Port to the {@code __NAME__} table. Implementations run inside the caller's transaction and never open one. */
public interface __AGG__Repository {
    Optional<__AGG__> findByCode(__AGG__Code code);

    void insert(__AGG__ __NAME__);

    /**
     * Writes {@code next} if the stored row still has {@code next.version() - 1}.
     *
     * @throws io.trellis.common.error.ConcurrentModificationException when another writer won the race
     */
    void update(__AGG__ next);

    /** One keyset page, newest first. {@code after} is the opaque cursor of the previous page, or null. */
    CursorPage<__AGG__> page(__AGG__Filter filter, String after, int limit);
}
""",
    "application/port/__AGG__Filter.java": """\
package io.trellis.__PKG__.application.port;

import io.trellis.__PKG__.api.dto.__AGG__Status;
import java.util.Set;

/**
 * What a list may return. Visibility is part of the query, not a filter after it, so pages are full and the
 * cursor never skips rows: {@code status IN visibleStatuses OR owner = ownerId}, then narrowed by the client's
 * own filters.
 *
 * @param unrestricted managers see everything; the visibility set is ignored
 * @param ownerId the caller's own __PLURAL__ are always visible to them; null for non-user actors
 * @param status client filter, or null
 * @param nameContains case-insensitive name search, or null
 */
public record __AGG__Filter(
        boolean unrestricted,
        Set<__AGG__Status> visibleStatuses,
        String ownerId,
        __AGG__Status status,
        String nameContains) {
    public __AGG__Filter {
        visibleStatuses = Set.copyOf(visibleStatuses);
    }
}
""",
    "application/view/__AGG__View.java": """\
package io.trellis.__PKG__.application.view;

import io.trellis.__PKG__.api.dto.__AGG__Status;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * A __NAME__ as clients see it (REST detail, GraphQL {@code __AGG__}). {@code version} is also the ETag. {@code
 * @NotNull} marks the fields that are always present: the OpenAPI document (and the clients generated from it)
 * declares them required; the others may be null.
 *
 * @param code public identifier, {@code __PREFIX__-XXXX-XXXX-XXXX}
 * @param activatedAt first activation, null while a draft
 * @param archivedAt null unless archived
 */
public record __AGG__View(
        @NotNull String code,
        @NotNull String name,
        @NotNull String description,
        @NotNull __AGG__Status status,
        @NotNull long version,
        @NotNull Instant createdAt,
        @NotNull Instant updatedAt,
        Instant activatedAt,
        Instant archivedAt) {}
""",
    "application/view/__AGG__Summary.java": """\
package io.trellis.__PKG__.application.view;

import io.trellis.__PKG__.api.dto.__AGG__Status;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** List row: a __NAME__ without its long fields. {@code @NotNull} fields are required in the OpenAPI document. */
public record __AGG__Summary(
        @NotNull String code,
        @NotNull String name,
        @NotNull __AGG__Status status,
        @NotNull Instant createdAt,
        Instant activatedAt) {}
""",
    "application/view/__AGG__Views.java": """\
package io.trellis.__PKG__.application.view;

import io.trellis.__PKG__.domain.model.__AGG__;

/** Aggregate to views. Kept in one place so REST and GraphQL can never disagree on a field. */
public final class __AGG__Views {
    private __AGG__Views() {}

    public static __AGG__View view(__AGG__ c) {
        return new __AGG__View(
                c.code().value(),
                c.details().name(),
                c.details().description(),
                c.status(),
                c.version(),
                c.createdAt(),
                c.updatedAt(),
                c.activatedAt(),
                c.archivedAt());
    }

    public static __AGG__Summary summary(__AGG__ c) {
        return new __AGG__Summary(
                c.code().value(), c.details().name(), c.status(), c.createdAt(), c.activatedAt());
    }
}
""",
    "application/validation/__AGG__Validator.java": """\
package io.trellis.__PKG__.application.validation;

import io.trellis.common.context.RequestContext;
import io.trellis.common.validation.CommandValidator;
import io.trellis.common.validation.Violations;
import io.trellis.__PKG__.domain.model.__AGG__Details;
import org.springframework.stereotype.Component;

/**
 * Business rules of __NAME__ details, shared by create and update and by every entry point (REST, GraphQL, a
 * future consumer). Bean Validation on the request DTO only checks shape; these rules are the contract. Every
 * rule reports a violation instead of throwing, so the caller gets all of them at once.
 *
 * <p>Generated skeleton: the rules below are the required/length checks every field needs. Add the real ones
 * (uniqueness, cross-field, state-dependent) here, not in the controller.
 */
@Component
public class __AGG__Validator implements CommandValidator<__AGG__Details> {
    static final String FIELD_NAME = "name";
    static final String FIELD_DESCRIPTION = "description";
    static final String REQUIRED = "REQUIRED";
    static final String TOO_LONG = "TOO_LONG";
    static final String CONTROL_CHARACTERS = "CONTROL_CHARACTERS";

    @Override
    public void validate(RequestContext ctx, __AGG__Details details, Violations violations) {
        String name = details.name();
        violations.check(name != null && !name.isBlank(), FIELD_NAME, REQUIRED, "name is required");
        if (name != null) {
            violations.check(
                    name.length() <= __AGG__Details.NAME_MAX,
                    FIELD_NAME,
                    TOO_LONG,
                    "name must be at most " + __AGG__Details.NAME_MAX + " characters");
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
                    description.length() <= __AGG__Details.DESCRIPTION_MAX,
                    FIELD_DESCRIPTION,
                    TOO_LONG,
                    "description must be at most " + __AGG__Details.DESCRIPTION_MAX + " characters");
        }
    }
}
""",
    "application/support/__AGG__Access.java": """\
package io.trellis.__PKG__.application.support;

import io.trellis.common.context.Actor;
import io.trellis.common.context.RequestContext;
import io.trellis.common.security.AuthorizationService;
import io.trellis.common.security.PermissionRequirement;
import io.trellis.common.security.ResourceScope;
import io.trellis.__PKG__.api.error.__AGG__Errors;
import io.trellis.__PKG__.api.security.__AGG__Permissions;
import io.trellis.__PKG__.application.port.__AGG__Filter;
import io.trellis.__PKG__.application.usecase.reading.List__AGG__sQuery;
import io.trellis.__PKG__.domain.model.__AGG__;
import io.trellis.__PKG__.domain.policy.__AGG__Reader;
import io.trellis.__PKG__.domain.policy.__AGG__Visibility;
import org.springframework.stereotype.Component;

/**
 * Applies the domain's {@link __AGG__Visibility} rule to a request: resolves the caller into a
 * {@link __AGG__Reader} through the one {@link AuthorizationService} (an explicit call, because the answer
 * depends on the loaded row, which an annotation cannot see), then checks one __NAME__ or builds the list filter.
 * Used by the queries and by the commands that load a __NAME__.
 */
@Component
public class __AGG__Access {
    private static final PermissionRequirement MANAGE = PermissionRequirement.anyOf(__AGG__Permissions.MANAGE);

    private final AuthorizationService authorization;

    public __AGG__Access(AuthorizationService authorization) {
        this.authorization = authorization;
    }

    public __AGG__Reader reader(RequestContext ctx) {
        String userId = ctx.actor() instanceof Actor.UserActor user ? user.id() : null;
        return new __AGG__Reader(
                userId, authorization.decide(ctx, MANAGE, ResourceScope.global()).allowed());
    }

    /** @throws io.trellis.common.error.NotFoundException (never 403) when the caller may not see it */
    public __AGG__ requireVisible(RequestContext ctx, __AGG__ __NAME__) {
        if (!__AGG__Visibility.canSee(reader(ctx), __NAME__)) {
            throw __AGG__Errors.notFound(__NAME__.code().value());
        }
        return __NAME__;
    }

    public __AGG__Filter listFilter(RequestContext ctx, List__AGG__sQuery query) {
        __AGG__Reader reader = reader(ctx);
        return new __AGG__Filter(
                reader.manager(),
                __AGG__Visibility.READER_STATUSES,
                reader.userId(),
                query.status(),
                query.nameContains());
    }
}
""",
    "application/usecase/reading/List__AGG__sQuery.java": """\
package io.trellis.__PKG__.application.usecase.reading;

import io.trellis.__PKG__.api.dto.__AGG__Status;

/**
 * Criteria of the list use case. A record, so REST query parameters and GraphQL arguments build the same object.
 *
 * @param first page size, clamped to {@link #MAX_FIRST}
 * @param after cursor of the previous page, or null for the first page
 * @param status only this status, or null for every status the caller may see
 * @param nameContains case-insensitive name search, or null
 */
public record List__AGG__sQuery(int first, String after, __AGG__Status status, String nameContains) {
    public static final int DEFAULT_FIRST = 20;
    public static final int MAX_FIRST = 100;

    public List__AGG__sQuery {
        first = first <= 0 ? DEFAULT_FIRST : Math.min(first, MAX_FIRST);
        nameContains = nameContains == null || nameContains.isBlank() ? null : nameContains.trim();
        after = after == null || after.isBlank() ? null : after;
    }
}
""",
    "application/support/__AGG__Loader.java": """\
package io.trellis.__PKG__.application.support;

import io.trellis.common.context.RequestContext;
import io.trellis.__PKG__.api.error.__AGG__Errors;
import io.trellis.__PKG__.application.port.__AGG__Repository;
import io.trellis.__PKG__.domain.model.__AGG__;
import io.trellis.__PKG__.domain.value.__AGG__Code;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Loads a __NAME__ the caller is allowed to see, or reports it missing.
 *
 * <p>A collaborator, not a use case: most of them start this way, and they must agree on what "not found"
 * means. A caller who may not see one gets the same answer as a caller asking about one that does not exist,
 * so the error cannot be used to enumerate them.
 */
@Service
@RequiredArgsConstructor
public class __AGG__Loader {

    private final __AGG__Repository __PLURAL__;
    private final __AGG__Access access;

    public __AGG__ loadVisible(RequestContext ctx, String code) {
        __AGG__Code parsed = __AGG__Code.parseOrNotFound(code);
        return access.requireVisible(
                ctx, __PLURAL__.findByCode(parsed).orElseThrow(() -> __AGG__Errors.notFound(parsed.value())));
    }
}
""",
    "application/usecase/reading/Get__AGG__UseCase.java": """\
package io.trellis.__PKG__.application.usecase.reading;

import io.micrometer.observation.annotation.Observed;
import io.trellis.common.aop.RequiresPermission;
import io.trellis.common.context.RequestContext;
import io.trellis.__PKG__.api.security.__AGG__Permissions;
import io.trellis.__PKG__.application.support.__AGG__Loader;
import io.trellis.__PKG__.application.view.__AGG__View;
import io.trellis.__PKG__.application.view.__AGG__Views;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads one __NAME__. One the caller may not see answers 404, never 403. */
@Service
@RequiredArgsConstructor
public class Get__AGG__UseCase {

    private final __AGG__Loader loader;

    @RequiresPermission(anyOf = {__AGG__Permissions.READ, __AGG__Permissions.MANAGE})
    @Transactional(readOnly = true)
    @Observed(name = "__NAME__.get")
    public __AGG__View execute(RequestContext ctx, String code) {
        return __AGG__Views.view(loader.loadVisible(ctx, code));
    }
}
""",
    "application/usecase/reading/List__AGG__sUseCase.java": """\
package io.trellis.__PKG__.application.usecase.reading;

import io.micrometer.observation.annotation.Observed;
import io.trellis.common.aop.RequiresPermission;
import io.trellis.common.context.RequestContext;
import io.trellis.common.page.CursorPage;
import io.trellis.__PKG__.api.security.__AGG__Permissions;
import io.trellis.__PKG__.application.port.__AGG__Repository;
import io.trellis.__PKG__.application.support.__AGG__Access;
import io.trellis.__PKG__.application.view.__AGG__Summary;
import io.trellis.__PKG__.application.view.__AGG__Views;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** One keyset page. Visibility is part of the query, so a page is never short of rows the caller may see. */
@Service
@RequiredArgsConstructor
public class List__AGG__sUseCase {

    private final __AGG__Repository __PLURAL__;
    private final __AGG__Access access;

    @RequiresPermission(anyOf = {__AGG__Permissions.READ, __AGG__Permissions.MANAGE})
    @Transactional(readOnly = true)
    @Observed(name = "__NAME__.list")
    public CursorPage<__AGG__Summary> execute(RequestContext ctx, List__AGG__sQuery query) {
        return __PLURAL__
                .page(access.listFilter(ctx, query), query.after(), query.first())
                .map(__AGG__Views::summary);
    }
}
""",
    "application/usecase/authoring/Create__AGG__UseCase.java": """\
package io.trellis.__PKG__.application.usecase.authoring;

import io.micrometer.observation.annotation.Observed;
import io.trellis.common.aop.Audited;
import io.trellis.common.aop.RateLimited;
import io.trellis.common.aop.RequiresPermission;
import io.trellis.common.context.RequestContext;
import io.trellis.common.id.ReferenceCodeGenerator;
import io.trellis.common.id.UuidV7;
import io.trellis.common.validation.ValidationPipeline;
import io.trellis.__PKG__.api.security.__AGG__Permissions;
import io.trellis.__PKG__.application.port.__AGG__Repository;
import io.trellis.__PKG__.application.view.__AGG__View;
import io.trellis.__PKG__.application.view.__AGG__Views;
import io.trellis.__PKG__.domain.model.__AGG__;
import io.trellis.__PKG__.domain.model.__AGG__Details;
import io.trellis.__PKG__.domain.value.__AGG__Code;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates a draft owned by the caller. */
@Service
@RequiredArgsConstructor
public class Create__AGG__UseCase {
    static final String AUDIT_ACTION = "__NAME__.created";

    private final __AGG__Repository __PLURAL__;
    private final ValidationPipeline<__AGG__Details> validation;
    private final Clock clock;
    private final ReferenceCodeGenerator codes = new ReferenceCodeGenerator(__AGG__Code.PREFIX);

    @RateLimited(permits = 30, per = "PT1M")
    @RequiresPermission(anyOf = __AGG__Permissions.MANAGE)
    @Transactional
    @Audited(action = AUDIT_ACTION, aggregateType = __AGG__.AGGREGATE_TYPE, aggregateId = "#result.code()")
    @Observed(name = "__NAME__.create")
    public __AGG__View execute(RequestContext ctx, __AGG__Details details) {
        validation.validate(ctx, details);
        // 60 random bits: a code collision is a once-in-a-billion event. The UNIQUE constraint still catches it
        // (500, the client retries); retrying here would need a savepoint, since Postgres aborts the transaction.
        __AGG__ draft = __AGG__.draft(
                UuidV7.next(clock), __AGG__Code.generate(codes), details, ctx.actor().id(), clock.instant());
        __PLURAL__.insert(draft);
        return __AGG__Views.view(draft);
    }
}
""",
    "application/usecase/authoring/Update__AGG__UseCase.java": """\
package io.trellis.__PKG__.application.usecase.authoring;

import io.micrometer.observation.annotation.Observed;
import io.trellis.common.aop.Audited;
import io.trellis.common.aop.RequiresPermission;
import io.trellis.common.context.RequestContext;
import io.trellis.common.error.TrellisException;
import io.trellis.common.validation.ValidationPipeline;
import io.trellis.__PKG__.api.error.__AGG__ErrorCode;
import io.trellis.__PKG__.api.security.__AGG__Permissions;
import io.trellis.__PKG__.application.port.__AGG__Repository;
import io.trellis.__PKG__.application.support.__AGG__Loader;
import io.trellis.__PKG__.application.view.__AGG__View;
import io.trellis.__PKG__.application.view.__AGG__Views;
import io.trellis.__PKG__.domain.model.__AGG__;
import io.trellis.__PKG__.domain.model.__AGG__Details;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Edits one the client has read, refusing if it moved underneath them. */
@Service
@RequiredArgsConstructor
public class Update__AGG__UseCase {
    static final String AUDIT_ACTION = "__NAME__.updated";

    private final __AGG__Repository __PLURAL__;
    private final __AGG__Loader loader;
    private final ValidationPipeline<__AGG__Details> validation;
    private final Clock clock;

    /**
     * @param expectedVersion the version the client edited (from {@code If-Match}); a different current version
     *     is 412, a concurrent writer between our read and write is 409
     */
    @RequiresPermission(anyOf = __AGG__Permissions.MANAGE)
    @Transactional
    @Audited(action = AUDIT_ACTION, aggregateType = __AGG__.AGGREGATE_TYPE, aggregateId = "#code")
    @Observed(name = "__NAME__.update")
    public __AGG__View execute(RequestContext ctx, String code, long expectedVersion, __AGG__Details details) {
        __AGG__ current = loader.loadVisible(ctx, code);
        if (current.version() != expectedVersion) {
            throw new TrellisException(
                    __AGG__ErrorCode.__UPPER___VERSION_MISMATCH,
                    "__AGG__ " + code + " is at version " + current.version() + ", not " + expectedVersion);
        }
        validation.validate(ctx, details);
        __AGG__ next = current.edit(details, clock.instant());
        __PLURAL__.update(next);
        return __AGG__Views.view(next);
    }
}
""",
    "application/usecase/publishing/Activate__AGG__UseCase.java": """\
package io.trellis.__PKG__.application.usecase.publishing;

import io.micrometer.observation.annotation.Observed;
import io.trellis.common.aop.Audited;
import io.trellis.common.aop.RequiresPermission;
import io.trellis.common.context.RequestContext;
import io.trellis.__PKG__.api.security.__AGG__Permissions;
import io.trellis.__PKG__.application.port.__AGG__Repository;
import io.trellis.__PKG__.application.support.__AGG__Loader;
import io.trellis.__PKG__.application.view.__AGG__View;
import io.trellis.__PKG__.application.view.__AGG__Views;
import io.trellis.__PKG__.domain.model.__AGG__;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Makes a draft visible to everyone who may read it. */
@Service
@RequiredArgsConstructor
public class Activate__AGG__UseCase {
    static final String AUDIT_ACTION = "__NAME__.activated";

    private final __AGG__Repository __PLURAL__;
    private final __AGG__Loader loader;
    private final Clock clock;

    @RequiresPermission(anyOf = __AGG__Permissions.MANAGE)
    @Transactional
    @Audited(action = AUDIT_ACTION, aggregateType = __AGG__.AGGREGATE_TYPE, aggregateId = "#code")
    @Observed(name = "__NAME__.activate")
    public __AGG__View execute(RequestContext ctx, String code) {
        __AGG__ next = loader.loadVisible(ctx, code).activate(clock.instant());
        __PLURAL__.update(next);
        return __AGG__Views.view(next);
    }
}
""",
    "application/usecase/publishing/Archive__AGG__UseCase.java": """\
package io.trellis.__PKG__.application.usecase.publishing;

import io.micrometer.observation.annotation.Observed;
import io.trellis.common.aop.Audited;
import io.trellis.common.aop.RequiresPermission;
import io.trellis.common.context.RequestContext;
import io.trellis.__PKG__.api.security.__AGG__Permissions;
import io.trellis.__PKG__.application.port.__AGG__Repository;
import io.trellis.__PKG__.application.support.__AGG__Loader;
import io.trellis.__PKG__.application.view.__AGG__View;
import io.trellis.__PKG__.application.view.__AGG__Views;
import io.trellis.__PKG__.domain.model.__AGG__;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Archive, not delete: the row stays for the audit trail and for anything that still references the code. */
@Service
@RequiredArgsConstructor
public class Archive__AGG__UseCase {
    static final String AUDIT_ACTION = "__NAME__.archived";

    private final __AGG__Repository __PLURAL__;
    private final __AGG__Loader loader;
    private final Clock clock;

    @RequiresPermission(anyOf = __AGG__Permissions.MANAGE)
    @Transactional
    @Audited(action = AUDIT_ACTION, aggregateType = __AGG__.AGGREGATE_TYPE, aggregateId = "#code")
    @Observed(name = "__NAME__.archive")
    public __AGG__View execute(RequestContext ctx, String code) {
        __AGG__ next = loader.loadVisible(ctx, code).archive(clock.instant());
        __PLURAL__.update(next);
        return __AGG__Views.view(next);
    }
}
""",
    # ------------------------------------------------------------------ infrastructure
    "infrastructure/persistence/entity/__AGG__Entity.java": """\
package io.trellis.__PKG__.infrastructure.persistence.entity;

import io.trellis.__PKG__.api.dto.__AGG__Status;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Row of {@code __NAME__}.
 *
 * <p>{@code version} is a plain column, not {@code @Version}: the aggregate decides when it advances and the
 * update statement checks it, so Hibernate must not also own that number.
 */
@Entity
@Table(name = "__NAME__")
@Getter
@Setter
@NoArgsConstructor
public class __AGG__Entity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "code", nullable = false, length = __CODE_COLUMN__)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private __AGG__Status status;

    @Column(name = "owner_id", nullable = false, length = 200)
    private String ownerId;

    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;
}
""",
    "infrastructure/persistence/__AGG__Entities.java": """\
package io.trellis.__PKG__.infrastructure.persistence;

import io.trellis.__PKG__.domain.model.__AGG__;
import io.trellis.__PKG__.domain.model.__AGG__Details;
import io.trellis.__PKG__.domain.value.__AGG__Code;
import io.trellis.__PKG__.infrastructure.persistence.entity.__AGG__Entity;

/** The one place that converts between the immutable aggregate and the mutable row (ADR 0004). */
final class __AGG__Entities {

    private __AGG__Entities() {}

    static __AGG__ toDomain(__AGG__Entity e) {
        return new __AGG__(
                e.getId(),
                new __AGG__Code(e.getCode()),
                new __AGG__Details(e.getName(), e.getDescription()),
                e.getStatus(),
                e.getOwnerId(),
                e.getVersion(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                e.getActivatedAt(),
                e.getArchivedAt());
    }

    static __AGG__Entity toEntity(__AGG__ c) {
        __AGG__Entity e = new __AGG__Entity();
        e.setId(c.id());
        e.setCode(c.code().value());
        e.setName(c.details().name());
        e.setDescription(c.details().description());
        e.setStatus(c.status());
        e.setOwnerId(c.ownerId());
        e.setVersion(c.version());
        e.setCreatedAt(c.createdAt());
        e.setUpdatedAt(c.updatedAt());
        e.setActivatedAt(c.activatedAt());
        e.setArchivedAt(c.archivedAt());
        return e;
    }
}
""",
    "infrastructure/persistence/__AGG__JpaRepository.java": """\
package io.trellis.__PKG__.infrastructure.persistence;

import io.trellis.__PKG__.infrastructure.persistence.entity.__AGG__Entity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Native SQL over {@code __NAME__} (ADR 0004). Listing is a keyset page and lives in the adapter. */
public interface __AGG__JpaRepository extends JpaRepository<__AGG__Entity, UUID> {

    @Query(value = "SELECT c.* FROM __NAME__ c WHERE c.code = :code", nativeQuery = true)
    Optional<__AGG__Entity> findByCode(@Param("code") String code);

    /** Optimistic write: the row moves only if it still holds the version the caller read. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value =
                    __TB__
                    UPDATE __NAME__
                       SET name = :name,
                           description = :description,
                           status = :status,
                           version = :version,
                           updated_at = :updatedAt,
                           activated_at = :activatedAt,
                           archived_at = :archivedAt
                     WHERE id = :id
                       AND version = :expectedVersion
                    __TB__,
            nativeQuery = true)
    int updateIfVersionMatches(
            @Param("id") UUID id,
            @Param("name") String name,
            @Param("description") String description,
            @Param("status") String status,
            @Param("version") long version,
            @Param("updatedAt") Instant updatedAt,
            @Param("activatedAt") Instant activatedAt,
            @Param("archivedAt") Instant archivedAt,
            @Param("expectedVersion") long expectedVersion);
}
""",
    "infrastructure/persistence/Jpa__AGG__Repository.java": """\
package io.trellis.__PKG__.infrastructure.persistence;

import io.trellis.common.page.Cursor;
import io.trellis.common.page.CursorCodec;
import io.trellis.common.page.CursorPage;
import io.trellis.__PKG__.application.port.__AGG__Filter;
import io.trellis.__PKG__.application.port.__AGG__Repository;
import io.trellis.__PKG__.domain.model.__AGG__;
import io.trellis.__PKG__.domain.value.__AGG__Code;
import io.trellis.__PKG__.infrastructure.persistence.entity.__AGG__Entity;
import io.trellis.platform.persistence.jdbc.JdbcSupport;
import io.trellis.platform.persistence.keyset.KeyType;
import io.trellis.platform.persistence.keyset.KeysetQuery;
import io.trellis.platform.persistence.keyset.KeysetQuery.SortKey;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * Implements the module's port. Single-row work is a native statement on {@link __AGG__JpaRepository}; the list
 * is a keyset page, whose WHERE clause depends on who is asking, so it is built here and run as a native query
 * that still returns entities - one mapping, no second row mapper (ADR 0004).
 */
@Repository
@RequiredArgsConstructor
class Jpa__AGG__Repository implements __AGG__Repository {
    private static final String PAGE_SELECT = "SELECT c.* FROM __NAME__ c WHERE TRUE";
    private static final String VISIBLE = " AND (c.status IN (:visibleStatuses) OR c.owner_id = :ownerId) ";
    private static final String VISIBLE_NO_OWNER = " AND c.status IN (:visibleStatuses) ";
    private static final String STATUS_IS = " AND c.status = :status ";
    private static final String NAME_CONTAINS = " AND c.name ILIKE :namePattern ESCAPE '\\\\' ";
    private static final KeysetQuery KEYSET =
            KeysetQuery.descending(SortKey.of("created_at", KeyType.TIMESTAMP), SortKey.of("id", KeyType.UUID_KEY));

    @PersistenceContext
    private EntityManager entityManager;

    private final __AGG__JpaRepository __PLURAL__;
    private final CursorCodec cursors;

    @Override
    public Optional<__AGG__> findByCode(__AGG__Code code) {
        return __PLURAL__.findByCode(code.value()).map(__AGG__Entities::toDomain);
    }

    @Override
    public void insert(__AGG__ __NAME__) {
        entityManager.persist(__AGG__Entities.toEntity(__NAME__));
    }

    @Override
    public void update(__AGG__ next) {
        int rows = __PLURAL__.updateIfVersionMatches(
                next.id(),
                next.details().name(),
                next.details().description(),
                next.status().name(),
                next.version(),
                next.updatedAt(),
                next.activatedAt(),
                next.archivedAt(),
                next.version() - 1);
        JdbcSupport.requireUpdated(rows, __AGG__.AGGREGATE_TYPE, next.code().value());
    }

    @Override
    public CursorPage<__AGG__> page(__AGG__Filter filter, String after, int limit) {
        Cursor cursor = after == null ? null : cursors.decode(after);
        StringBuilder sql = new StringBuilder(PAGE_SELECT);
        Map<String, Object> params = new HashMap<>(KEYSET.parameters(cursor, limit));
        if (!filter.unrestricted()) {
            sql.append(filter.ownerId() == null ? VISIBLE_NO_OWNER : VISIBLE);
            params.put("visibleStatuses", names(filter.visibleStatuses()));
            if (filter.ownerId() != null) {
                params.put("ownerId", filter.ownerId());
            }
        }
        if (filter.status() != null) {
            sql.append(STATUS_IS);
            params.put("status", filter.status().name());
        }
        if (filter.nameContains() != null) {
            sql.append(NAME_CONTAINS);
            params.put("namePattern", "%" + escapeLike(filter.nameContains()) + "%");
        }
        sql.append(KEYSET.where(cursor, "AND")).append(KEYSET.orderBy()).append(KEYSET.limit());
        Query query = entityManager.createNativeQuery(sql.toString(), __AGG__Entity.class);
        params.forEach(query::setParameter);
        List<__AGG__> rows = new ArrayList<>();
        for (Object row : query.getResultList()) {
            rows.add(__AGG__Entities.toDomain((__AGG__Entity) row));
        }
        return KEYSET.page(rows, limit, c -> List.of(c.createdAt(), c.id()), cursors);
    }

    private static List<String> names(Set<? extends Enum<?>> values) {
        List<String> result = new ArrayList<>();
        values.forEach(value -> result.add(value.name()));
        return result;
    }

    /** User text inside LIKE must not act as a wildcard. */
    private static String escapeLike(String text) {
        return text.replace("\\\\", "\\\\\\\\").replace("%", "\\\\%").replace("_", "\\\\_");
    }
}
""",
    # ------------------------------------------------------------------ web
    "web/rest/dto/authoring/__AGG__DetailsRequest.java": """\
package io.trellis.__PKG__.web.rest.dto.authoring;

import io.trellis.__PKG__.domain.model.__AGG__Details;
import jakarta.validation.constraints.NotNull;

/**
 * Body of create and update. Bean Validation checks shape only (present, right type); the business limits are
 * {@code __AGG__Validator}'s, so GraphQL or a consumer calling the service get the same rules.
 */
public record __AGG__DetailsRequest(@NotNull String name, @NotNull String description) {
    public __AGG__Details toDetails() {
        return new __AGG__Details(name, description);
    }
}
""",
    "web/rest/dto/reading/__AGG__Page.java": """\
package io.trellis.__PKG__.web.rest.dto.reading;

import io.trellis.common.page.CursorPage;
import io.trellis.__PKG__.application.view.__AGG__Summary;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * One keyset page of __PLURAL__. Same shape as the GraphQL {@code __AGG__Page}: pass {@code nextCursor} as {@code
 * after} to get the next page; it is null exactly when {@code hasNext} is false.
 */
public record __AGG__Page(@NotNull List<__AGG__Summary> items, String nextCursor, @NotNull boolean hasNext) {
    public static __AGG__Page of(CursorPage<__AGG__Summary> page) {
        return new __AGG__Page(page.items(), page.nextCursor(), page.hasNext());
    }
}
""",
    "web/rest/__AGG__Controller.java": """\
package io.trellis.__PKG__.web.rest;

import io.trellis.common.context.RequestContext;
import io.trellis.__PKG__.api.dto.__AGG__Status;
import io.trellis.__PKG__.api.error.__AGG__ErrorCode;
import io.trellis.__PKG__.application.usecase.authoring.Create__AGG__UseCase;
import io.trellis.__PKG__.application.usecase.authoring.Update__AGG__UseCase;
import io.trellis.__PKG__.application.usecase.publishing.Activate__AGG__UseCase;
import io.trellis.__PKG__.application.usecase.publishing.Archive__AGG__UseCase;
import io.trellis.__PKG__.application.usecase.reading.Get__AGG__UseCase;
import io.trellis.__PKG__.application.usecase.reading.List__AGG__sQuery;
import io.trellis.__PKG__.application.usecase.reading.List__AGG__sUseCase;
import io.trellis.__PKG__.application.view.__AGG__View;
import io.trellis.__PKG__.web.rest.dto.authoring.__AGG__DetailsRequest;
import io.trellis.__PKG__.web.rest.dto.reading.__AGG__Page;
import io.trellis.platform.web.rest.VersionETags;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST for commands and integrations (decision T12). Every response that carries a __NAME__ carries its version
 * as {@code ETag}; {@code PUT} requires it back in {@code If-Match}. Errors are RFC 7807 problems from the
 * platform: this class throws nothing and builds no error body. One use case per endpoint.
 */
@RestController
@RequestMapping("/api/__PLURAL__")
class __AGG__Controller {

    private final Create__AGG__UseCase create__AGG__;
    private final Update__AGG__UseCase update__AGG__;
    private final Activate__AGG__UseCase activate__AGG__;
    private final Archive__AGG__UseCase archive__AGG__;
    private final Get__AGG__UseCase get__AGG__;
    private final List__AGG__sUseCase list__AGG__s;

    __AGG__Controller(
            Create__AGG__UseCase create__AGG__,
            Update__AGG__UseCase update__AGG__,
            Activate__AGG__UseCase activate__AGG__,
            Archive__AGG__UseCase archive__AGG__,
            Get__AGG__UseCase get__AGG__,
            List__AGG__sUseCase list__AGG__s) {
        this.create__AGG__ = create__AGG__;
        this.update__AGG__ = update__AGG__;
        this.activate__AGG__ = activate__AGG__;
        this.archive__AGG__ = archive__AGG__;
        this.get__AGG__ = get__AGG__;
        this.list__AGG__s = list__AGG__s;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ResponseEntity<__AGG__View> create__AGG__(
            RequestContext ctx, @Valid @RequestBody __AGG__DetailsRequest request) {
        __AGG__View created = create__AGG__.execute(ctx, request.toDetails());
        return ResponseEntity.created(URI.create("/api/__PLURAL__/" + created.code()))
                .eTag(VersionETags.of(created.version()))
                .body(created);
    }

    @GetMapping
    __AGG__Page list__AGG__s(
            RequestContext ctx,
            @RequestParam(defaultValue = "" + List__AGG__sQuery.DEFAULT_FIRST) int first,
            @RequestParam(required = false) String after,
            @RequestParam(required = false) __AGG__Status status,
            @RequestParam(required = false) String query) {
        return __AGG__Page.of(list__AGG__s.execute(ctx, new List__AGG__sQuery(first, after, status, query)));
    }

    @GetMapping("/{code}")
    ResponseEntity<__AGG__View> get__AGG__(RequestContext ctx, @PathVariable String code) {
        return withETag(get__AGG__.execute(ctx, code));
    }

    @PutMapping("/{code}")
    ResponseEntity<__AGG__View> update__AGG__(
            RequestContext ctx,
            @PathVariable String code,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody __AGG__DetailsRequest request) {
        return withETag(update__AGG__.execute(
                ctx,
                code,
                VersionETags.parse(ifMatch, __AGG__ErrorCode.__UPPER___INVALID_PRECONDITION),
                request.toDetails()));
    }

    @PostMapping("/{code}/activate")
    ResponseEntity<__AGG__View> activate__AGG__(RequestContext ctx, @PathVariable String code) {
        return withETag(activate__AGG__.execute(ctx, code));
    }

    @PostMapping("/{code}/archive")
    ResponseEntity<__AGG__View> archive__AGG__(RequestContext ctx, @PathVariable String code) {
        return withETag(archive__AGG__.execute(ctx, code));
    }

    private static ResponseEntity<__AGG__View> withETag(__AGG__View view) {
        return ResponseEntity.ok().eTag(VersionETags.of(view.version())).body(view);
    }
}
""",
    "web/graphql/__AGG__GraphQlController.java": """\
package io.trellis.__PKG__.web.graphql;

import io.trellis.common.context.RequestContext;
import io.trellis.common.page.CursorPage;
import io.trellis.__PKG__.api.dto.__AGG__Status;
import io.trellis.__PKG__.application.usecase.reading.Get__AGG__UseCase;
import io.trellis.__PKG__.application.usecase.reading.List__AGG__sQuery;
import io.trellis.__PKG__.application.usecase.reading.List__AGG__sUseCase;
import io.trellis.__PKG__.application.view.__AGG__Summary;
import io.trellis.__PKG__.application.view.__AGG__View;
import io.trellis.platform.web.graphql.GraphQlContextKeys;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

/**
 * GraphQL read model for the UI (decision T12): {@code graphql/__NAME__/__NAME__.graphqls}. Thin like the REST
 * controller and calling the same use cases as REST, which is where authorization happens. A field that
 * would be loaded once per parent row gets a {@code @BatchMapping} instead (see {@code trellis-notification}).
 */
@Controller
class __AGG__GraphQlController {
    private final Get__AGG__UseCase get__AGG__;
    private final List__AGG__sUseCase list__AGG__s;

    __AGG__GraphQlController(Get__AGG__UseCase get__AGG__, List__AGG__sUseCase list__AGG__s) {
        this.get__AGG__ = get__AGG__;
        this.list__AGG__s = list__AGG__s;
    }

    @QueryMapping
    __AGG__View __NAME__(
            @ContextValue(GraphQlContextKeys.REQUEST_CONTEXT) RequestContext ctx, @Argument String code) {
        return get__AGG__.execute(ctx, code);
    }

    @QueryMapping
    CursorPage<__AGG__Summary> __PLURAL__(
            @ContextValue(GraphQlContextKeys.REQUEST_CONTEXT) RequestContext ctx,
            @Argument Integer first,
            @Argument String after,
            @Argument __AGG__Status status,
            @Argument String query) {
        int size = first == null ? List__AGG__sQuery.DEFAULT_FIRST : first;
        return list__AGG__s.execute(ctx, new List__AGG__sQuery(size, after, status, query));
    }
}
""",
}

GRAPHQLS = """\
# __AGG__ module: UI read model. Owned by __PROJECT__/; exported with the rest of the schema to
# contracts/graphql/schema.graphqls by :trellis-app:exportApiContracts.

extend type Query {
  "One __NAME__ by code (__PREFIX__-XXXX-XXXX-XXXX). One the caller may not see is a NOT_FOUND error, like a missing one."
  __NAME__(code: ID!): __AGG__
  "Newest first. Pass nextCursor as `after` for the next page; same cursor format as GET /api/__PLURAL__."
  __PLURAL__(first: Int = 20, after: String, status: __AGG__Status, query: String): __AGG__Page!
}

enum __AGG__Status {
  DRAFT
  ACTIVE
  ARCHIVED
}

type __AGG__ {
  code: ID!
  name: String!
  description: String!
  status: __AGG__Status!
  "Optimistic-lock version; the REST ETag."
  version: Int!
  createdAt: DateTime!
  updatedAt: DateTime!
  activatedAt: DateTime
  archivedAt: DateTime
}

"A list row: a __NAME__ without its long fields."
type __AGG__Summary {
  code: ID!
  name: String!
  status: __AGG__Status!
  createdAt: DateTime!
  activatedAt: DateTime
}

type __AGG__Page {
  items: [__AGG__Summary!]!
  nextCursor: String
  hasNext: Boolean!
}
"""

CHANGESET = """\
--liquibase formatted sql

--changeset trellis:__NAME__-001-__NAME__
--comment: __AGG__ aggregate (generated by new-module.sh). code is the public id; version is the optimistic
--comment: lock and the ETag. No foreign keys between modules: a module owns its tables (decision T10).
CREATE TABLE __NAME__ (
    id           UUID          PRIMARY KEY,
    code         VARCHAR(__CODE_COLUMN__)   NOT NULL,
    name         VARCHAR(200)  NOT NULL,
    description  TEXT          NOT NULL,
    status       VARCHAR(16)   NOT NULL,
    owner_id     VARCHAR(200)  NOT NULL,
    version      BIGINT        NOT NULL,
    created_at   TIMESTAMPTZ   NOT NULL,
    updated_at   TIMESTAMPTZ   NOT NULL,
    activated_at TIMESTAMPTZ,
    archived_at  TIMESTAMPTZ,
    CONSTRAINT uq___NAME___code UNIQUE (code),
    CONSTRAINT ck___NAME___status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck___NAME___version CHECK (version >= 1)
);
-- Keyset order of the list API: (created_at DESC, id DESC).
CREATE INDEX ix___NAME___created ON __NAME__ (created_at DESC, id DESC);
--rollback DROP TABLE __NAME__;
"""

AUTOCONFIG_IMPORTS = "io.trellis.__PKG__.__MOD__Module\n"

README = """\
# __PROJECT__

Generated by `build-logic/scripts/dev/new-module.sh --name __NAME__ --aggregate __AGG__ --plural __PLURAL__`
from `trellis-notification`. What is here is a **working skeleton**, not the real use case: a __AGG__ with a name, a
description and a DRAFT -> ACTIVE -> ARCHIVED lifecycle, so the module compiles, boots and answers HTTP before
you have written a single business rule. Replace it; do not grow it by adding fields you do not need.

Tutorial (Vietnamese): [`docs/guide/new-module.md`](../docs/guide/new-module.md).

## Layout (decision T17: fixed sub-packages, even when they hold one file)

```text
io.trellis.__PKG__
  __MOD__Module                  auto-configuration: scans application/, infrastructure/, web/
  api/
    dto/          __AGG__Status                            wire vocabulary (enums)
    event/        -                                        (none yet: the module publishes no event)
    security/     __AGG__Permissions                       @PermissionCatalog: __NAME__:read, __NAME__:manage
    error/        __AGG__ErrorCode, __AGG__Errors          error codes + exception factories
  domain/
    model/        __AGG__, __AGG__Details                  immutable aggregate, transitions
    value/        __AGG__Code                              reference code __PREFIX__-XXXX-XXXX-XXXX
    policy/       __AGG__Visibility, __AGG__Reader         who may see one (pure)
  application/
    usecase/authoring/    Create__AGG__UseCase, Update__AGG__UseCase      one class per use case, one execute
    usecase/publishing/   Activate__AGG__UseCase, Archive__AGG__UseCase
    usecase/reading/      Get__AGG__UseCase, List__AGG__sUseCase, List__AGG__sQuery
    support/      __AGG__Access, __AGG__Loader             shared collaborators, no AOP annotations
    port/         __AGG__Repository, __AGG__Filter
    view/         __AGG__View, __AGG__Summary, __AGG__Views (mapper)
    validation/   __AGG__Validator                         CommandValidator
  infrastructure/
    persistence/  Jpa__AGG__Repository, __AGG__JpaRepository, entity/__AGG__Entity   native SQL, keyset
    messaging/    -                                        (none yet: no consumer, no read model)
    client/       -                                        (none yet: no external service)
  web/
    rest/         __AGG__Controller, __AGG__DetailsRequest, __AGG__Page
    graphql/      __AGG__GraphQlController                 @QueryMapping
resources/
  db/changelog/__SLUG__/001-__NAME__.sql    Liquibase (included from the app's master changelog)
  graphql/__SLUG__/__NAME__.graphqls        this module's part of the GraphQL schema
  META-INF/spring/...AutoConfiguration.imports   registers __MOD__Module
```

A sub-package exists once it holds a class; the layout above is the shape to grow into, not a set of empty
folders to create up front. The layer rules are enforced by ArchUnit, not by marker files. `trellis-notification` is
the live example.

## API

| REST | Notes |
| --- | --- |
| `POST /api/__PLURAL__` | 201, `Location`, `ETag`; needs `__NAME__:manage` |
| `GET /api/__PLURAL__?first=&after=&status=&query=` | `{items, nextCursor, hasNext}` |
| `GET /api/__PLURAL__/{code}` | `ETag` |
| `PUT /api/__PLURAL__/{code}` | `If-Match` required; stale = 412 `__UPPER___VERSION_MISMATCH` |
| `POST /api/__PLURAL__/{code}/activate` | DRAFT or ARCHIVED -> ACTIVE |
| `POST /api/__PLURAL__/{code}/archive` | soft delete: the platform keeps the row |

GraphQL (`POST /api/graphql`): `__NAME__(code)`, `__PLURAL__(first, after, status, query)`.

Both APIs are part of the committed contracts, so after any change to an endpoint or the schema run
`./gradlew :trellis-app:exportApiContracts` and commit `contracts/`.

## The five places to edit for the real use case

1. `domain/model/__AGG__.java` - the fields and the transitions. Everything decidable from the aggregate alone.
2. `application/validation/__AGG__Validator.java` - the rules; they report violations, so the caller gets all of
   them at once.
3. `api/security/__AGG__Permissions.java` plus a `role_permission` changeset mapping them to roles, in
   `trellis-app/src/main/resources/application.yml`, and the realm roles in
   `build-logic/docker/keycloak/trellis-realm.json` for local runs. A permission nobody holds is a 403 on every call.
4. `src/main/resources/db/changelog/__NAME__/001-__NAME__.sql` - while nothing has been applied anywhere you may
   still edit this changeset; afterwards add `002-...` instead.
5. `web/rest/__AGG__Controller.java` and `resources/graphql/__NAME__/__NAME__.graphqls` - the shape clients see.
   Controllers stay thin: no rules, no authorization, no transactions (ArchUnit enforces it).

Then: an event (`api/event` + outbox, write the JSON Schema first), a read model
(`infrastructure/messaging`), an external client (`infrastructure/client`) - each has a live example in
`trellis-notification`.

Tests (decision T5): none here. Only the platform and `trellis-notification`'s reference flow are tested; do not add
feature tests unless the owner asks.
"""


# --------------------------------------------------------------------------- rendering
def sort_imports(java: str) -> str:
    """Spotless sorts imports alphabetically; the rendered names decide the order, so sort them here.

    Without this, `Course` and `Notification` would need different template orders (`CourseQueries` sorts before
    `ListCoursesQuery`, `NotificationQueries` after `ListNotificationsQuery`) and `spotlessCheck` would fail for one of them.
    """
    lines = java.splitlines(keepends=True)
    block = [i for i, line in enumerate(lines) if line.startswith("import ")]
    if not block:
        return java
    first, last = block[0], block[-1]
    imports = sorted(lines[first : last + 1], key=lambda line: (not line.startswith("import static "), line))
    return "".join(lines[:first] + imports + lines[last + 1 :])


def module_files(names: Names) -> dict[str, str]:
    """Every file of the module, as a path relative to the module directory -> content."""
    out: dict[str, str] = {}
    java = f"src/main/java/io/trellis/{names.pkg}"
    out["build.gradle.kts"] = BUILD_GRADLE
    out["README.md"] = README.replace("__PROJECT__", names.project)
    out[f"{java}/{names.mod}Module.java"] = MODULE_CLASS
    for path, content in JAVA_FILES.items():
        out[f"{java}/{path}"] = content
    out["src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"] = (
        AUTOCONFIG_IMPORTS
    )
    out[f"src/main/resources/db/changelog/{names.slug}/001-{names.name}.sql"] = CHANGESET
    out[f"src/main/resources/graphql/{names.slug}/{names.name}.graphqls"] = GRAPHQLS.replace(
        "__PROJECT__", names.project
    )
    rendered = {names.render(path): names.render(content) for path, content in out.items()}
    rendered = {names.featurize(path): names.featurize(text) for path, text in rendered.items()}
    if names.feature is not None:
        rendered["README.md"] = feature_readme(rendered["README.md"], names)
    return {path: sort_imports(text) if path.endswith(".java") else text for path, text in rendered.items()}




def feature_readme(readme: str, names: Names) -> str:
    """The layout tree in the README shows the primary feature; say so, and name the other features."""
    for layer in ("domain", "application"):
        readme = readme.replace(f"  {layer}/\n", f"  {layer}/{names.feature}/\n", 1)
    others = [f for f in names.features[1:]]
    note = (
        f"\nThis module groups {len(names.features)} features (decision T26): "
        + ", ".join(f"`{f}`" for f in names.features)
        + f". The tree above shows `{names.feature}`, which the generator filled in; "
        + (
            "the others are `domain/` and `application/` packages to fill in: "
            + ", ".join(f"`{f}`" for f in others)
            if others
            else "no other feature has code yet"
        )
        + ".\n`api/`, `infrastructure/` and `web/` are **not** split per feature: they are the module's one\n"
        + "published surface, one set of adapters and one set of entry points.\n"
    )
    anchor = "## Layout (decision T17: fixed sub-packages, even when they hold one file)\n"
    return readme.replace(anchor, anchor + note, 1)


# --------------------------------------------------------------------------- wiring of existing files
def insert_once(path: Path, anchor: str, addition: str, already: str) -> str | None:
    """Returns the new text of `path` with `addition` inserted after `anchor`, or None when already wired."""
    text = path.read_text()
    if already in text:
        return None
    if anchor not in text:
        fail(f"cannot wire {path.relative_to(REPO)}: the anchor line is gone\n  expected: {anchor.strip()}")
    return text.replace(anchor, anchor + addition, 1)


def wiring(names: Names) -> list[tuple[Path, str]]:
    """The edits to existing files, as (path, new text). Only files that need a change are listed."""
    project = names.project
    edits: list[tuple[Path, str]] = []

    # settings.gradle.kts: business modules go before trellis-app, which depends on them.
    text = SETTINGS.read_text()
    if f'"{project}"' not in text:
        if SETTINGS_ANCHOR not in text:
            fail(f"cannot wire settings.gradle.kts: the `{SETTINGS_ANCHOR.strip()}` line is gone")
        edits.append((SETTINGS, text.replace(SETTINGS_ANCHOR, f'    "{project}",\n{SETTINGS_ANCHOR}', 1)))

    new = insert_once(
        APP_BUILD,
        APP_BUILD_ANCHOR,
        f'  implementation(project(":{project}"))\n',
        f'project(":{project}")',
    )
    if new is not None:
        edits.append((APP_BUILD, new))

    # The master changelog says it itself: the order never changes, new includes are appended at the end.
    text = MASTER_CHANGELOG.read_text()
    if f"db/changelog/{names.slug}/" not in text:
        if CHANGELOG_ANCHOR not in text:
            fail("cannot wire db.changelog-schema.yaml: the `# Business modules:` comment is gone")
        include = f"  - include:\n      file: db/changelog/{names.slug}/001-{names.name}.sql\n"
        edits.append((MASTER_CHANGELOG, (text if text.endswith("\n") else text + "\n") + include))

    return edits


# --------------------------------------------------------------------------- commands
def gradle(names: Names, tasks: list[str], what: str) -> None:
    print(f"\nnew-module: {what}")
    rc = subprocess.run(["./gradlew", "--console=plain", "-q", *tasks], cwd=REPO, check=False).returncode
    if rc == 0:
        return
    print(
        f"\nnew-module: `./gradlew {' '.join(tasks)}` failed. The module is written and wired; run it\n"
        f"yourself once the build works, or `./gradlew build` will fail.",
        file=sys.stderr,
    )


def generate(names: Names, dry_run: bool, fmt: bool, export_contracts: bool) -> None:
    target = REPO / names.project
    if target.exists():
        fail(
            f"{names.project}/ already exists - nothing was written.\n"
            f"  Pick another --name, or remove the module first "
            f"(docs/guide/new-module.md, \"Xoá một module\")."
        )
    files = module_files(names)
    edits = wiring(names)

    if dry_run:
        print(f"new-module: would create {len(files)} files in {names.project}/\n")
        for path in sorted(files):
            print(f"  {names.project}/{path}")
        print("\nand change:")
        for path, _text in edits:
            print(f"  {path.relative_to(REPO)}")
        if not edits:
            print("  (nothing: every wiring entry is already there)")
        print("\nNothing was written (--dry-run).")
        return

    # Written to a temp directory beside the repo and moved in one step: a failure halfway leaves no module.
    staging = Path(tempfile.mkdtemp(prefix=f".{names.project}-", dir=REPO))
    try:
        for path, content in files.items():
            file = staging / path
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_text(content)
        os.rename(staging, target)
    except Exception:
        shutil.rmtree(staging, ignore_errors=True)
        raise

    for path, text in edits:
        path.write_text(text)

    print(f"new-module: created {names.project}/ ({len(files)} files)")
    for path, _text in edits:
        print(f"new-module: wired {path.relative_to(REPO)}")

    if fmt:
        # Palantir wraps on line length, so the canonical formatting depends on how long the names are:
        # only the formatter knows it for sure. Cheap, and it keeps `spotlessCheck` green on the first run.
        gradle(names, [f":{names.project}:spotlessApply"], "formatting the module (--skip-format to skip)")
    if export_contracts:
        gradle(
            names,
            [":trellis-app:exportApiContracts"],
            "regenerating the committed API contracts (needs Docker; --skip-contracts to skip)",
        )
    else:
        print(
            "\nnew-module: contracts NOT regenerated (--skip-contracts). The new REST and GraphQL\n"
            "operations are part of the committed contracts, so run\n"
            "  ./gradlew :trellis-app:exportApiContracts\n"
            "before ./gradlew build, and commit contracts/."
        )

    print(
        f"\nNext:\n"
        f"  1. ./gradlew build                                     # compile, Spotless, ArchUnit, contracts\n"
        f"  2. {names.project}/README.md                        # the five places to edit for the real use case\n"
        f"  3. grant {names.name}:read / {names.name}:manage to a role your users hold, in the profile file\n"
        f"     by adding a role_permission changeset to this module, and\n"
        f"     add the realm role itself to your IdP - until then every call answers 403\n"
        f"  4. docs/guide/new-module.md                            # tutorial, in Vietnamese"
    )


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(
        prog="new-module",
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="Removing a module again: docs/guide/new-module.md, section \"Xoá một module\".",
    )
    parser.add_argument(
        "--name",
        required=True,
        help="the aggregate in lower case: table, permission prefix, audit actions (course)",
    )
    parser.add_argument(
        "--slug",
        help="module directory and Gradle project without the prefix; hyphens allowed "
        "(default: --name). trellis-<slug>/, package io.trellis.<slug without hyphens>",
    )
    parser.add_argument(
        "--features",
        help="comma-separated features sharing this module (decision T26): domain/ and application/ are "
        "grouped per feature and the first one gets the generated skeleton. Omit for a one-feature module.",
    )
    parser.add_argument("--aggregate", help="aggregate class name (default: --name capitalised): Course")
    parser.add_argument("--plural", help="plural for the REST path and list field (default: <name>s): courses")
    parser.add_argument("--prefix", help="reference-code prefix, 2-4 upper-case letters (default: first two): CO")
    parser.add_argument("--dry-run", action="store_true", help="print the file list and the wiring, write nothing")
    parser.add_argument(
        "--skip-format",
        action="store_true",
        help="do not run the new module's spotlessApply afterwards",
    )
    parser.add_argument(
        "--skip-contracts",
        action="store_true",
        help="do not run :trellis-app:exportApiContracts afterwards (then ./gradlew build fails until you do)",
    )
    a = parser.parse_args(argv)

    name = a.name.strip()
    slug = (a.slug or name).strip()
    features = [f.strip() for f in a.features.split(",")] if a.features else []
    aggregate = (a.aggregate or name[:1].upper() + name[1:]).strip()
    plural = (a.plural or name + "s").strip()
    prefix = (a.prefix or name[:2].upper()).strip()
    if not NAME_PATTERN.match(name):
        fail(f"--name must be lower-case letters and digits, starting with a letter: {name!r}")
    if not NAME_PATTERN.match(plural):
        fail(f"--plural must be lower-case letters and digits, starting with a letter: {plural!r}")
    if not AGGREGATE_PATTERN.match(aggregate):
        fail(f"--aggregate must be UpperCamelCase: {aggregate!r}")
    if not PREFIX_PATTERN.match(prefix):
        fail(f"--prefix must be 2-4 upper-case letters: {prefix!r}")
    if not SLUG_PATTERN.match(slug):
        fail(f"--slug must be lower-case words joined by single hyphens: {slug!r}")
    for feature in features:
        if not NAME_PATTERN.match(feature):
            fail(f"--features entries must be lower-case letters and digits: {feature!r}")
    if len(set(features)) != len(features):
        fail(f"--features has a duplicate: {features}")
    if features and name not in features:
        fail(
            f"--name {name!r} must be one of --features {features}: the generated skeleton goes into a feature,\n"
            f"  and the first feature listed is the one that gets it."
        )
    if features and features[0] != name:
        fail(f"--features must start with {name!r} (the feature the skeleton is generated into): {features}")
    if slug.replace("-", "") in ("app", "common", "platform"):
        fail(f"'{slug}' is a platform name, not a business module (ArchUnit would not treat it as one)")

    generate(
        Names(name, aggregate, plural, prefix, slug, features),
        a.dry_run,
        not a.skip_format,
        not a.skip_contracts,
    )


if __name__ == "__main__":
    main()
