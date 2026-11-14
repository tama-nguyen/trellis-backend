"""Static checks for the GitLab CI configuration (.gitlab-ci.yml + local includes).

GitLab reports a broken `extends:` / `needs:` / `!reference` only when it tries to create a pipeline,
and a `needs:` on a job that the rules skipped only in the pipeline shape where that happens. This
script catches both before pushing:

1. every `extends`, `needs` and `!reference` target exists in some included file;
2. every job's stage (after `extends`) is declared in `stages`;
3. for a set of pipeline scenarios (MR touching one area, main push, release tag), it evaluates
   `workflow:rules` and each job's `rules` (a subset of GitLab's expression language: ==, !=, =~,
   &&, bare $VAR, `changes:`) and checks that every non-optional `needs` target is in the pipeline.

Run:  uv run --no-project --with pyyaml==6.0.3 python scripts/ci/check_ci_refs.py
Exit status 1 on any finding. Jobs coming from GitLab templates are treated as opaque.
"""

from __future__ import annotations

import fnmatch
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]
RESERVED = {
    "stages", "workflow", "variables", "default", "include", "image", "services", "cache",
    "before_script", "after_script", "spec",
}
IMPLICIT_STAGES = {".pre", ".post"}
# Jobs defined by GitLab templates we include; names only, their rules are GitLab's business.
TEMPLATE_JOBS = {"Jobs/Secret-Detection.gitlab-ci.yml": {"secret_detection", ".secret-analyzer"}}


@dataclass(frozen=True)
class Ref:
    path: tuple[str, ...]


class Loader(yaml.SafeLoader):
    pass


def _reference(loader: yaml.SafeLoader, node: yaml.Node) -> Ref:
    return Ref(tuple(str(p) for p in loader.construct_sequence(node)))  # type: ignore[arg-type]


Loader.add_constructor("!reference", _reference)


@dataclass
class Config:
    jobs: dict[str, dict] = field(default_factory=dict)
    origin: dict[str, str] = field(default_factory=dict)
    stages: list[str] = field(default_factory=list)
    workflow: dict = field(default_factory=dict)
    global_vars: dict[str, str] = field(default_factory=dict)
    external: set[str] = field(default_factory=set)


def load(errors: list[str]) -> Config:
    cfg = Config()
    queue = [ROOT / ".gitlab-ci.yml"]
    seen: set[Path] = set()
    while queue:
        path = queue.pop(0)
        if path in seen:
            continue
        seen.add(path)
        rel = str(path.relative_to(ROOT))
        if not path.exists():
            errors.append(f"include not found: {rel}")
            continue
        doc = yaml.load(path.read_text(), Loader=Loader) or {}
        includes = doc.get("include", [])
        for inc in includes if isinstance(includes, list) else [includes]:
            if isinstance(inc, str):
                inc = {"local": inc}
            if "local" in inc:
                queue.append(ROOT / inc["local"].lstrip("/"))
            elif "template" in inc:
                cfg.external |= TEMPLATE_JOBS.get(inc["template"], set())
        if "stages" in doc:
            cfg.stages = list(doc["stages"])
        if "workflow" in doc:
            cfg.workflow = doc["workflow"]
        for k, v in (doc.get("variables") or {}).items():
            cfg.global_vars[k] = v["value"] if isinstance(v, dict) else str(v)
        for name, body in doc.items():
            if name in RESERVED or not isinstance(body, dict):
                continue
            if name in cfg.jobs:
                errors.append(f"{rel}: job '{name}' also defined in {cfg.origin[name]} (later wins)")
            cfg.jobs[name] = body
            cfg.origin[name] = rel
    return cfg


def walk_refs(node: object, where: str, out: list[tuple[str, Ref]]) -> None:
    if isinstance(node, Ref):
        out.append((where, node))
    elif isinstance(node, dict):
        for k, v in node.items():
            walk_refs(v, f"{where}.{k}", out)
    elif isinstance(node, list):
        for i, v in enumerate(node):
            walk_refs(v, f"{where}[{i}]", out)


def resolve_ref(cfg: Config, ref: Ref) -> object:
    node: object = cfg.jobs.get(ref.path[0])
    for key in ref.path[1:]:
        if not isinstance(node, dict) or key not in node:
            return None
        node = node[key]
    return node


def deep_merge(base: dict, over: dict) -> dict:
    out = dict(base)
    for k, v in over.items():
        out[k] = deep_merge(out[k], v) if isinstance(v, dict) and isinstance(out.get(k), dict) else v
    return out


def expand(cfg: Config, name: str, stack: tuple[str, ...] = ()) -> dict:
    """The job after `extends` (hash deep-merge, arrays replaced), with !reference resolved."""
    body = cfg.jobs[name]
    parents = body.get("extends", [])
    merged: dict = {}
    for parent in [parents] if isinstance(parents, str) else parents:
        if parent in cfg.jobs and parent not in stack:
            merged = deep_merge(merged, expand(cfg, parent, (*stack, name)))
    merged = deep_merge(merged, {k: v for k, v in body.items() if k != "extends"})
    return resolve_all(cfg, merged)


def resolve_all(cfg: Config, node: object) -> object:
    if isinstance(node, Ref):
        return resolve_all(cfg, resolve_ref(cfg, node))
    if isinstance(node, dict):
        return {k: resolve_all(cfg, v) for k, v in node.items()}
    if isinstance(node, list):
        flat: list = []
        for item in node:
            value = resolve_all(cfg, item)
            # GitLab flattens a !reference that yields a list inside a list (rules, script).
            if isinstance(item, Ref) and isinstance(value, list):
                flat.extend(value)
            else:
                flat.append(value)
        return flat
    return node


# ------------------------------------------------------------------ rules evaluation
TOKEN = re.compile(r'\s*(\$\w+|"[^"]*"|\'[^\']*\'|/(?:\\.|[^/])*/|==|!=|=~|!~|&&|\|\||\(|\))')


def eval_if(expr: str, env: dict[str, str]) -> bool:
    """Tiny evaluator for the `if:` forms used in this repo. Unknown variables are empty."""

    def value(tok: str) -> str:
        if tok.startswith("$"):
            return env.get(tok[1:], "")
        return tok[1:-1]

    def atom(tokens: list[str]) -> bool:
        left = tokens.pop(0)
        if tokens and tokens[0] in ("==", "!=", "=~", "!~"):
            op, right = tokens.pop(0), tokens.pop(0)
            lv = value(left)
            if op in ("=~", "!~"):
                pattern = right[1:-1].replace("\\d", "[0-9]")
                hit = re.search(pattern, lv) is not None
                return hit if op == "=~" else not hit
            rv = value(right)
            return lv == rv if op == "==" else lv != rv
        return value(left) != ""

    tokens = [t for t in TOKEN.findall(expr) if t.strip()]
    result = atom(tokens)
    while tokens:
        op = tokens.pop(0)
        rhs = atom(tokens)
        result = (result and rhs) if op == "&&" else (result or rhs)
    return result


def changes_match(rule: dict, changed: list[str] | None) -> bool:
    if "changes" not in rule:
        return True
    if changed is None:  # branch/tag pipelines: GitLab treats `changes` as true on new refs; we only use it in MRs
        return True
    spec = rule["changes"]
    paths = spec.get("paths", []) if isinstance(spec, dict) else spec
    for f in changed:
        for p in paths:
            if fnmatch.fnmatch(f, p) or fnmatch.fnmatch(f, p.replace("/**/*", "/*")):
                return True
    return False


def rules_pass(rules: list | None, env: dict[str, str], changed: list[str] | None) -> tuple[bool, dict]:
    if rules is None:
        return True, {}
    for rule in rules:
        if not isinstance(rule, dict):
            continue
        if "if" in rule and not eval_if(rule["if"], env):
            continue
        if not changes_match(rule, changed):
            continue
        return rule.get("when", "on_success") != "never", rule
    return False, {}


@dataclass
class Scenario:
    name: str
    env: dict[str, str]
    changed: list[str] | None


def scenarios() -> list[Scenario]:
    mr = {"CI_PIPELINE_SOURCE": "merge_request_event", "CI_MERGE_REQUEST_IID": "1",
          "CI_DEFAULT_BRANCH": "main", "CI_MERGE_REQUEST_TITLE": "feat: x"}
    out = [
        Scenario(f"MR touching {label}", mr, files)
        for label, files in {
            "java": ["app/src/main/java/A.java"],
            "a business module": ["notice/src/main/java/io/trellis/notice/A.java"],
            "contracts": ["contracts/events/envelope.schema.json"],
            "contract codegen": ["contracts/codegen/typescript/generate.mjs"],
            "backend Dockerfile only": ["docker/backend.Dockerfile"],
            "compose only": ["docker/compose.yml"],
            "docs only": ["docs/adr/0002.md"],
            "CI only": ["ci/common.yml"],
        }.items()
    ]
    branch = {"CI_PIPELINE_SOURCE": "push", "CI_DEFAULT_BRANCH": "main"}
    out.append(Scenario("push to main", {**branch, "CI_COMMIT_BRANCH": "main"}, None))
    out.append(Scenario("push to feature branch (no MR)", {**branch, "CI_COMMIT_BRANCH": "feat/x"}, None))
    out.append(Scenario("schedule on main", {**branch, "CI_PIPELINE_SOURCE": "schedule", "CI_COMMIT_BRANCH": "main"}, None))
    out.append(Scenario("tag v1.2.3", {**branch, "CI_COMMIT_TAG": "v1.2.3"}, None))
    out.append(Scenario("tag v1.2.3-rc.1", {**branch, "CI_COMMIT_TAG": "v1.2.3-rc.1"}, None))
    out.append(Scenario("tag contracts-v0.1.0", {**branch, "CI_COMMIT_TAG": "contracts-v0.1.0"}, None))
    return out


def main() -> int:
    errors: list[str] = []
    cfg = load(errors)
    known = set(cfg.jobs) | cfg.external

    # 1. references
    for name, body in cfg.jobs.items():
        where = f"{cfg.origin[name]}: {name}"
        parents = body.get("extends", [])
        for parent in [parents] if isinstance(parents, str) else parents:
            if parent not in known:
                errors.append(f"{where}: extends unknown '{parent}'")
        for need in body.get("needs", []) or []:
            target = need if isinstance(need, str) else need.get("job")
            if target and target not in known:
                errors.append(f"{where}: needs unknown job '{target}'")
        refs: list[tuple[str, Ref]] = []
        walk_refs(body, name, refs)
        for loc, ref in refs:
            if resolve_ref(cfg, ref) is None:
                errors.append(f"{cfg.origin[name]}: {loc}: !reference {list(ref.path)} does not resolve")
    if errors:  # expansion below assumes references resolve
        return report(errors, [])

    # 2. stages
    stages = set(cfg.stages) | IMPLICIT_STAGES
    real_jobs = [n for n in cfg.jobs if not n.startswith(".")]
    expanded = {n: expand(cfg, n) for n in real_jobs}
    for name, job in expanded.items():
        stage = job.get("stage", "test")
        if stage not in stages:
            errors.append(f"{cfg.origin[name]}: {name}: stage '{stage}' not in stages {cfg.stages}")
        if "script" not in job and "trigger" not in job:
            errors.append(f"{cfg.origin[name]}: {name}: no script")

    # 3. scenarios
    summary: list[str] = []
    for sc in scenarios():
        env = {**cfg.global_vars, **sc.env}
        ok, _ = rules_pass(cfg.workflow.get("rules"), env, sc.changed)
        if not ok:
            summary.append(f"{sc.name:34s} no pipeline")
            continue
        present = {n for n, job in expanded.items() if rules_pass(job.get("rules"), env, sc.changed)[0]}
        for name in sorted(present):
            for need in expanded[name].get("needs", []) or []:
                target, optional = (need, False) if isinstance(need, str) else (need.get("job"), need.get("optional", False))
                if target in cfg.external:
                    continue
                if target not in present and not optional:
                    errors.append(f"[{sc.name}] {name} needs '{target}', which is not in this pipeline")
        summary.append(f"{sc.name:34s} {len(present):2d} jobs: {' '.join(sorted(present))}")
    return report(errors, summary)


def report(errors: list[str], summary: list[str]) -> int:
    for line in summary:
        print(line)
    for e in errors:
        print(f"ERROR {e}")
    print(f"{len(errors)} problem(s)" if errors else "CI references and needs: OK")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
