"""The names a module is generated from, and the rules they must follow."""

from __future__ import annotations

import re
import sys

NAME_PATTERN = re.compile(r"^[a-z][a-z0-9]*$")
AGGREGATE_PATTERN = re.compile(r"^[A-Z][A-Za-z0-9]*$")
PREFIX_PATTERN = re.compile(r"^[A-Z]{2,4}$")
SLUG_PATTERN = re.compile(r"^[a-z][a-z0-9]*(-[a-z0-9]+)*$")

# The reference code is <prefix>-XXXX-XXXX-XXXX: the prefix plus three separators and twelve symbols.
CODE_BODY_LENGTH = 15


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
            .replace("__PROJECT__", self.project)
        )

    def featurize(self, text: str) -> str:
        """Insert the feature segment into ``domain`` and ``application``, in paths and in package names."""
        if self.feature is None:
            return text
        for layer in ("domain", "application"):
            text = text.replace(f"io.trellis.{self.pkg}.{layer}.", f"io.trellis.{self.pkg}.{layer}.{self.feature}.")
            text = text.replace(f"/{layer}/", f"/{layer}/{self.feature}/")
        return text


def parse_names(
    name: str,
    slug: str | None = None,
    features: str | None = None,
    aggregate: str | None = None,
    plural: str | None = None,
    prefix: str | None = None,
) -> Names:
    """Applies the defaults to the command-line values and rejects the ones the build could not use."""
    name = name.strip()
    slug = (slug or name).strip()
    feature_list = [f.strip() for f in features.split(",")] if features else []
    aggregate = (aggregate or name[:1].upper() + name[1:]).strip()
    plural = (plural or name + "s").strip()
    prefix = (prefix or name[:2].upper()).strip()
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
    for feature in feature_list:
        if not NAME_PATTERN.match(feature):
            fail(f"--features entries must be lower-case letters and digits: {feature!r}")
    if len(set(feature_list)) != len(feature_list):
        fail(f"--features has a duplicate: {feature_list}")
    if feature_list and name not in feature_list:
        fail(
            f"--name {name!r} must be one of --features {feature_list}: the generated skeleton goes into a feature,\n"
            f"  and the first feature listed is the one that gets it."
        )
    if feature_list and feature_list[0] != name:
        fail(f"--features must start with {name!r} (the feature the skeleton is generated into): {feature_list}")
    if slug.replace("-", "") in ("app", "common", "platform"):
        fail(f"'{slug}' is a platform name, not a business module (ArchUnit would not treat it as one)")
    return Names(name, aggregate, plural, prefix, slug, feature_list)
