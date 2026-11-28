"""The edits to files that already exist, so the application picks the new module up."""

from __future__ import annotations

from pathlib import Path

from names import Names, fail

REPO = Path(__file__).resolve().parents[4]
SETTINGS = REPO / "settings.gradle.kts"
APP_BUILD = REPO / "trellis-app" / "build.gradle.kts"
APP_RESOURCES = REPO / "trellis-app" / "src" / "main" / "resources"
MASTER_CHANGELOG = APP_RESOURCES / "db" / "changelog" / "db.changelog-schema.yaml"
RATE_LIMIT_CONFIG = APP_RESOURCES / "config" / "rate-limit.yml"

SETTINGS_ANCHOR = '    "trellis-app",'
APP_BUILD_ANCHOR = "  // Business modules\n"
CHANGELOG_ANCHOR = "  # Business modules: add `- include: { file: db/changelog/<module>/NNN-<change>.sql }` below.\n"


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

    policy_key = f"      {names.pkg}-create:"
    new = insert_once(RATE_LIMIT_CONFIG, "    policies:\n", f"{policy_key} 30/PT1M\n", policy_key)
    if new is not None:
        edits.append((RATE_LIMIT_CONFIG, new))

    return edits
