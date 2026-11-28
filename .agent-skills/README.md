# Portable agent skills

Skills that any agent can use (Claude Code, Cursor, Codex, Copilot, Gemini CLI, ...) live here,
one folder per skill. The database family lives under `skills/database-*/`.

```text
.agent-skills/<skill>/
  SKILL.md        what the skill does, when to use it, the steps (judgement for the model)
  standards/      rules the skill applies
  templates/      files the skill fills in
  scripts/        mechanical work (listing, validating, generating) - the model never does this by hand
  references/     background reading
```

`SKILL.md` opens with YAML frontmatter carrying `name` and `description`, so the file is
recognisable as a skill by the tools that look for that shape.

## Why here and not `.claude/skills/`

`.claude/` is Claude Code's own directory: settings, hooks, commands, subagents. A skill placed
there is invisible to every other agent. Keeping skills in `.agent-skills/` makes them plain
repository content — Markdown and shell scripts, no agent-specific API — so Cursor, Codex and a
human all read the same copy, and `.claude/` holds only what is genuinely Claude Code-specific.

## How another agent consumes a skill

Nothing needs installing: point the agent at the file.

- **Any agent, any time** — "Read `.agent-skills/review/SKILL.md` and follow it." The skill names
  the files it needs; the agent reads them with its normal file tools.
- **Cursor** — a rule in `.cursor/rules/` that says to read the `SKILL.md` for the matching kind
  of work, or `@`-mention the file in chat.
- **Codex / Copilot / Gemini CLI** — reference the path in the prompt, or from `AGENTS.md`, which
  every one of them reads.
- **Claude Code** — a thin `.claude/commands/<name>.md` may invoke a skill, and `AGENTS.md` points
  at this directory. The command file holds no rules of its own.

Each `SKILL.md` has a "Multi-agent compatibility" section saying which steps, if any, need a
Claude Code-only feature (hooks, subagents) and what to do instead.

## Rules

- **One copy.** A skill lives only here. A `.claude/commands/<name>.md` may invoke it and Cursor
  rules may point at it, but nobody keeps a second copy that can drift.
- **Link, do not restate.** A skill that repeats `AGENTS.md` is worse than a link to it: the copy
  goes stale. `SKILL.md` stays under ~120 lines.
- **Split judgement from mechanics.** Anything a script or a Gradle task can decide
  deterministically goes there; `SKILL.md` covers only the choices that need a model.
- **Only rules that already exist.** Every rule in a `standards/` file must already be stated in
  `AGENTS.md`, in `docs/`, or in a recorded decision. A skill is not where new policy is invented.

## Skills in this repo

| Skill | What it does |
| --- | --- |
| [`review/`](review/SKILL.md) | Review a change against this repo's Java rules ([`standards/java.md`](review/standards/java.md)) |
| [`java-refactor/`](java-refactor/SKILL.md) | Refactor Java workflows and JUnit tests using the [shared conventions](../docs/guide/java-testing-conventions.md), preserving behavior and integration coverage |
| [`new-module/`](new-module/SKILL.md) | Drive `build-logic/scripts/dev/new-module.sh` and the five edit points after it |
| [`task-planning/`](task-planning/SKILL.md) | Store Jira-backed plans under `docs/tasks/` and maintain the task impact index |

For database task activation, see the [project routing table](../AGENTS.md#database-engineering-skills).
The repository-local `.agents/skills` symlink exposes the same database skill directories to Codex.

Structure taken from DataHub's `.agent-skills/` (Apache-2.0); the contents are Trellis's own.
