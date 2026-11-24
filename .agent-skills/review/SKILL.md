---
name: review
description: >
  Review a change in trellis-backend against this repo's own rules: layering and ArchUnit
  boundaries, explicit RequestContext, no magic strings, transactions and the outbox, AOP
  annotation placement and self-invocation, native SQL, configuration classification,
  contracts and migrations. Use when reviewing a branch, an MR, or staged work before a commit.
---

# Trellis backend review

Review the Java change in front of you against `standards/java.md`. The rules there are this
repo's rules, taken from `AGENTS.md` and `docs/architecture/backend.md`; do not invent new ones,
and do not import conventions from other projects.

Design rationale for anything that looks arbitrary: `../../docs/architecture/backend.md`.

---

## Multi-agent compatibility

Everything here works in any agent (Claude Code, Cursor, Codex, Copilot, Gemini CLI): the whole
skill is Markdown plus `git` and `./gradlew` commands. There is no agent-specific step.

Claude Code only, and safely ignorable: `.claude/settings.json` pre-allows the read-only commands
below, and `.claude/hooks/deny-git-no-verify.sh` blocks `git ... --no-verify`.

---

## 1. Scope the review

```bash
git diff --stat <base>...HEAD      # whole branch
git diff --stat                    # working tree
git diff --stat --cached           # staged
```

In scope: everything under `trellis-*/src/`, `build-logic/`, `contracts/`, `gradle/`.

Out of scope, unless the change touches them: `docs/` (prose, reviewed as prose), and anything
with a generated header — `contracts/openapi/openapi.json`, `contracts/graphql/schema.graphqls`.
A hand edit to a generated file **is** a finding ("Generated file edited by hand").

---

## 2. Read, in this order

1. The diff itself, in full.
2. For each touched module, its layer: a change in `web/` is read against the use-case class it
   calls, a change in `infrastructure/persistence/` against the port in `application/port/`.
3. `standards/java.md` — the rule list. Keep it open; cite rule titles in findings.
4. Only when the change adds a module, endpoint, event, migration, config property or feature
   flag: the matching entry of "How to add..." in `AGENTS.md`, which lists the edit points a
   change of that kind must touch. A missing edit point is a finding.

---

## 3. Check mechanically what can be checked mechanically

Do not reason about what a command can answer:

```bash
./gradlew lintCheck            # Spotless (Google Java Format, ktlint)
./gradlew build                # javac -Werror, ArchUnit, context load, config classification,
                               # contract drift, NoticeFlowIT  (needs Docker and JDK 21)
./gradlew checkApiContracts    # committed contracts vs the running app
```

ArchUnit already enforces layering, `Instant.now()`, JPA, AOP placement and
self-invocation. If `build` passes, do not file those as findings; review what it cannot see:
whether the right rule was expressed, whether context is threaded, whether the outbox call sits
in the business transaction, whether an error code is the right one.

---

## 4. Report

One section per finding, nothing else:

```text
### <Rule title from standards/java.md>
<path>:<line>
What is wrong, in one or two sentences. Then the fix, concretely.
Severity: blocking | high | medium
```

- **blocking** — wrong behaviour, a lost write, a missing authorization, a bypassed transaction,
  a broken contract or an edited applied changeset.
- **high** — would fail a gate, or breaks a rule with a runtime consequence (self-invocation,
  unclassified config property, missing outbox topic).
- **medium** — a real rule break with no runtime consequence yet (magic string, `@Data`, a fat
  `<Aggregate>Service`).

Rules: cite `file:line`; quote the offending line when it is short; never file a finding that
`./gradlew build` already reports; no style comments that Spotless owns; if the diff is clean,
say so and name what you checked. Note missing tests only for the platform surfaces listed in
`AGENTS.md` "Testing (decision T5)" — feature tests are deliberately not written here.
