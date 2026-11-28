"""Writes the module, wires it in and runs the Gradle follow-ups."""

from __future__ import annotations

import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

from names import Names, fail
from rendering import module_files
from wiring import REPO, wiring


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
