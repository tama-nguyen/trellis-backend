---
name: java-refactor
description: >
  Refactor Trellis Java workflows, naming, fixtures and JUnit tests while preserving behavior.
  Use for requested code-quality cleanup, test restructuring or convention alignment; business
  and security policy changes require their own agreed scope.
---

# Refactor Trellis Java and tests

Read [AGENTS.md](../../AGENTS.md) and
[Java and JUnit conventions](../../docs/guide/java-testing-conventions.md) before editing.
The guide owns the rules and examples; this skill owns the refactoring workflow.

## Multi-agent compatibility

Use ordinary repository reads, git and Gradle. This workflow requires no agent-specific tool,
delegation or installation.

## Inspect the behavior

Read the affected production flow, callers, tests, fixtures and configuration. Identify actual
readability problems: ambiguous dependency/state names, mixed scenarios, hidden assertions,
complex transaction setup or resource nesting. A formatter-only change does not address them.

Map existing scenarios to inputs, observed outcomes, persisted effects and transaction/concurrency
boundaries. Check repository APIs before introducing fixture/probe SQL. Run the existing focused
test when practical. Follow a requested research-only scope or plan review boundary; otherwise
continue within the authorized refactor without inventing an approval gate.

## Refactor in cohesive slices

Use the guide's production/test variable rules, capability names and focused fixture helpers.
Keep meaningful assertions visible; split distinct behaviors while preserving integration
collaborators. Preserve API/DTO/schema/configuration names, bean wiring, SpEL parameters and
security semantics. Check evaluation order, exception handling and transaction side effects.

If the requested cleanup would change business behavior, identify that decision separately;
do not silently treat it as formatting. Preserve unrelated and concurrent user edits.

## Verify and finish

Run Spotless separately, then the affected tests and the broader checks justified by the slice.
Compare scenario/assertion coverage and inspect the diff; a green formatter does not prove
readability or behavioral equivalence. Record exact commands, outcomes and unverified cases.

When commits are requested, follow the phase/history rules in AGENTS.md. Keep corrections in the
authorized unpublished phase instead of adding fixup noise. Synchronize the existing plan and
relevant conventions/skills when the owner asks. Use the
[task planning workflow](../task-planning/SKILL.md) so the task README records affected areas and
final commit SHAs; link to the canonical guide rather than copying it.

For the approved Long entity refactor, follow ADR 0004 rather than obsolete UUID row-ID assumptions.
Preserve technical UUIDs and JDBC role/security IDs. Creation fixtures use returned persisted IDs;
do not extend an identifier refactor into a new business/security policy.
