"""Turns the files under templates/ into the files of one module.

A template's path under templates/, without the .tmpl suffix, is the path of the generated file inside
the module; `__NAME__`-style placeholders are replaced in both the path and the content. The generator
runs Spotless afterwards, so generated sources follow the pinned Google Java Format version.
"""

from __future__ import annotations

from pathlib import Path

from names import Names

TEMPLATES = Path(__file__).resolve().parent / "templates"
TEMPLATE_SUFFIX = ".tmpl"


def read_templates() -> dict[str, str]:
    """Generated path (still holding placeholders) -> template content."""
    return {
        path.relative_to(TEMPLATES).as_posix()[: -len(TEMPLATE_SUFFIX)]: path.read_text()
        for path in sorted(TEMPLATES.rglob(f"*{TEMPLATE_SUFFIX}"))
    }


def module_files(names: Names) -> dict[str, str]:
    """Every file of the module, as a path relative to the module directory -> content."""
    rendered = {names.render(path): names.render(content) for path, content in read_templates().items()}
    rendered = {names.featurize(path): names.featurize(text) for path, text in rendered.items()}
    if names.feature is not None:
        rendered["README.md"] = feature_readme(rendered["README.md"], names)
    return {path: sort_imports(text) if path.endswith(".java") else text for path, text in rendered.items()}


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
