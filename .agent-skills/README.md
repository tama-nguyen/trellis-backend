# Portable agent skills

Skills that any agent can use (Claude Code, Cursor, Codex, ...) live here, one folder per skill:

```text
.agent-skills/<skill>/
  SKILL.md        what the skill does, when to use it, the steps (judgement for the model)
  standards/      rules the skill applies
  templates/      files the skill fills in
  scripts/        mechanical work (listing, validating, generating) - the model never does this by hand
  references/     background reading
```

Rules:

- **One copy.** A skill lives only here. `.claude/commands/<name>.md` may invoke it, Cursor rules may
  point at it, but nobody keeps a second copy that can drift.
- **Split judgement from mechanics.** Anything a script can do deterministically goes in `scripts/`;
  `SKILL.md` covers only the choices that need a model.
- **Say what is agent-specific.** If a step needs a Claude Code-only feature (hooks, subagents), mark it
  in a "Multi-agent compatibility" section of `SKILL.md`.

No skills yet; they are added as the platform grows (pattern taken from DataHub's `.agent-skills/`).
