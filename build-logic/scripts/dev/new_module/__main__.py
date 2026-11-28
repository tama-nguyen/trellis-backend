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
  .../config/rate-limit.yml                  the REST create policy quota

It does NOT grant the new permissions to anyone: a `role_permission` changeset of your own maps them to the roles of one
environment, so it belongs in that environment's profile file (`application-dev.yml` locally), not in the base
`application.yml`, which holds only what every profile shares. Until a role your users hold names
`<name>:read` / `<name>:manage`, every call to the new module answers 403.

Afterwards it runs `:trellis-<name>:spotlessApply` on the new module (`--skip-format` to skip): Google Java Format
wraps on line length, so the canonical formatting of generated code depends on how long the names are.

The generated files come from new_module/templates/, which mirrors the layout of a module: edit a file there to
change what a new module looks like, or add one to generate one more file. names.py validates the input,
rendering.py fills the templates, wiring.py edits the existing files and generator.py writes everything.

Nothing is written until every file has been rendered: the module goes to a temp directory next to the
repo root and is moved into place at the end, so a failure never leaves half a module behind.

Both APIs are generated (REST and GraphQL), so `contracts/openapi/openapi.json` and
`contracts/graphql/schema.graphqls` become stale - they are committed, drift-checked files (decision T12).
The script therefore runs `./gradlew :trellis-app:exportApiContracts` for you at the end (needs Docker);
`--skip-contracts` leaves it to you, and then `./gradlew build` fails on `checkApiContracts` until you run it.
"""

from __future__ import annotations

import argparse

from generator import generate
from names import parse_names


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

    names = parse_names(a.name, a.slug, a.features, a.aggregate, a.plural, a.prefix)
    generate(names, a.dry_run, not a.skip_format, not a.skip_contracts)


if __name__ == "__main__":
    main()
