# Contracts (decision T22)

`contracts/` is the single source of truth for everything the three repos must agree on. This repo
owns it, because the backend is the side that **produces** the agreement: it emits the events and it
exports the OpenAPI document and the GraphQL schema from its own code.

The other repos never copy these files. This repo's CI generates a client for each of them and
publishes it to **this project's GitLab Package Registry**.

## What is in `contracts/`

| Path | What | Produced by |
| --- | --- | --- |
| `events/*.schema.json` | JSON Schema per event payload, plus `envelope.schema.json` | hand-written; the wire contract |
| `openapi/openapi.json` | the REST surface | `./gradlew :trellis-app:exportApiContracts` |
| `graphql/schema.graphqls` | the GraphQL surface | same task |
| `testdata/` | vectors both languages must agree on (`backoff.json`, `stable-id.json`) | hand-written |
| `examples/` | example envelopes used by tests and the CLI | hand-written |
| `packages/npm`, `packages/pypi` | the publishable client packages | generated at release |

`openapi.json` and `schema.graphqls` are **generated and committed**. `./gradlew build` runs
`:trellis-app:checkApiContracts`, which fails when the committed copy is stale — so a change to a
controller or a `.graphqls` file forces you to re-export and commit it in the same change.

## The two published packages

| Package | Registry | Contents | Consumer |
| --- | --- | --- | --- |
| `@trellis/contracts` | npm, this project | OpenAPI types and typed client, plus the GraphQL schema | `trellis-web` |
| `trellis-contracts` | PyPI, this project | pydantic models for the event payloads, and an OpenAPI client | `trellis-ai` |

Version: the git tag on a release (`contracts-vX.Y.Z`); `main` publishes a prerelease of `0.0.0` so
consumers can test ahead of a tag. `scripts/contracts/version.sh` derives it. Consumers **pin an
exact version** — no range — so a contract change is always a visible commit in the consumer.

## Releasing a contract change

1. Change the schema, controller or `.graphqls` file.
2. `./gradlew :trellis-app:exportApiContracts` and commit `contracts/`.
3. Merge. `main` publishes a prerelease; consumers can try it.
4. Tag `contracts-vX.Y.Z`. CI publishes both packages at that version.
5. In `trellis-web` and `trellis-ai`, bump the pin and fix whatever the compiler or mypy now rejects.

Breaking changes follow the repo rule: `!` in the commit type and an entry in
`docs/how/breaking-changes.md`, written for someone who does not read code. Prefer additive changes;
for an event, add a new `eventVersion` and publish both until consumers have moved.

## Letting the consumers read the registry

Both packages live in **this project's** registry, so GitLab must be told that the other two
projects may read it: add `trellis-web` and `trellis-ai` to this project's **job-token allowlist**
(Settings → CI/CD → Job token permissions). Consumers then authenticate with their own
`CI_JOB_TOKEN`; locally a developer uses a `read_api` personal access token. No token is ever
committed.

Consumers need this project's numeric id (`TRELLIS_CONTRACTS_PROJECT_ID`) and host as CI variables.

## Until the first publish

Both consumers currently keep their generated client committed and check it with their own
`contracts:check` script, which flips to "installed package" mode once `0.1.0` exists. Publishing
`0.1.0` from here is what unblocks that flip.

## One open coupling

The GraphQL **operation documents** (the queries the UI sends) live in `trellis-web`, while the
generator lives here. Two ways to settle it, not yet decided:

- the package ships only the schema and scalar types, and web generates its operation types locally
  from its own documents; or
- the documents move into this repo next to the schema, and the package ships the operation types.

Until it is decided, web generates operation types locally and pins the schema from the package.
