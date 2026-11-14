# 0001. Record architecture decisions

- Status: accepted
- Date: 2026-11-14

## Context

Trellis is a template that several products will be generated from. A choice made here (a library, a
module boundary, a toolchain) is inherited by every product, and the reason behind it is lost unless it
is written down next to the code. People and agents both need to know whether a convention is deliberate
before changing it.

## Decision

Every significant decision is recorded as an Architecture Decision Record in `docs/adr/`, numbered in
order (`NNNN-kebab-title.md`), using the template below. An ADR is never edited after it is accepted,
except to change its status; a new ADR supersedes an old one and both link to each other.

"Significant" means: it changes how modules depend on each other, adds or replaces a framework or a
toolchain, changes a public contract, or is expensive to reverse.

A proposal that needs discussion before a decision (an RFC) is an ADR with status `proposed`.

## Consequences

- Reviewers can ask "where is the ADR?" for a structural change.
- Generated products keep the ADR history of the base and continue numbering from it.

## Template

```markdown
# NNNN. Title in the imperative

- Status: proposed | accepted | superseded by NNNN | deprecated
- Date: YYYY-MM-DD

## Context

What forces are at play: the problem, constraints, what we know.

## Decision

What we will do, stated plainly.

## Consequences

What becomes easier, what becomes harder, what we must now do.
```
