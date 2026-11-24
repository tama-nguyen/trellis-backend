---
name: review
description: >
  Review a change in trellis-backend against this repo's own rules: layering and ArchUnit
  boundaries, explicit RequestContext, no magic strings, transactions and the outbox, AOP
  annotation placement and self-invocation, native SQL, configuration classification,
  contracts, migrations and behavior-preserving Java/JUnit conventions. Use when reviewing a
  branch, an MR, or staged work before a commit.
---

# Trellis backend review

Review the Java change against [standards/java.md](standards/java.md),
[AGENTS.md](../../AGENTS.md) and the relevant sections of
[Java and JUnit conventions](../../docs/guide/java-testing-conventions.md).
Use the repository's current rules; do not import conflicting conventions from another project.

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

For test refactors, compare the original scenarios, assertions, persisted effects and transaction/
concurrency boundaries with their new locations. Check existing repository APIs before accepting
probe SQL. For review-only requests, keep the first pass read-only.

---

## 3. Check mechanically what can be checked mechanically

Do not reason about what a command can answer:

```bash
./gradlew lintCheck            # Spotless (Google Java Format, ktlint)
./gradlew build                # javac -Werror, ArchUnit, context load, config classification,
                               # contract drift, NoticeFlowTest  (needs Docker and JDK 21)
./gradlew :trellis-app:checkApiContracts  # only when a separate contract check is needed
```

Use JDK 21. If formatting is authorized, run `./gradlew spotlessApply` separately and wait for it
to finish before targeted tests or `build`; a review-only request uses checks, not the formatter.
Record exact commands/results and any unverified behavior. A targeted test or a successful
format check does not prove that the full build passed.

JPA is supported for business persistence. ArchUnit checks layer boundaries, entity placement,
time access and AOP rules. A passing build is evidence for the rules it actually enforces;
still review permission semantics, transaction side effects, test isolation, naming and assertion
coverage. Formatting alone does not establish those properties.

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
say so and name what you checked. Review platform suites and owner-authorized feature tests;
new feature tests still follow AGENTS.md's testing policy.
