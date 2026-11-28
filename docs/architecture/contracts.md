# Contracts (decision T22)

`contracts/` is owned by the backend: event schemas, REST OpenAPI, GraphQL schema, shared test
vectors and publishable TypeScript/Python clients. Generated output is committed and never edited
by hand.

## Verification

`./gradlew build` includes `:trellis-app:checkApiContracts`, which boots the application with
Testcontainers and compares the exported API with `contracts/openapi/openapi.json` and
`contracts/graphql/schema.graphqls`.

`build-logic/scripts/contracts/generate.sh --check` verifies the generated package sources using
pinned generators. GitHub CI then compiles `contracts/packages/npm`, builds
`contracts/packages/python`, checks for changes to committed output, and uploads the resulting
packages as workflow artifacts.

| Package | Contents | Consumer |
| --- | --- | --- |
| `@trellis/contracts` | OpenAPI types/client and GraphQL schema | trellis-web |
| `trellis-contracts` | Event models and typed API client | trellis-ai |

## Updating contracts

1. Change the owning schema, controller or GraphQL source.
2. Run `./gradlew :trellis-app:exportApiContracts`.
3. Run `build-logic/scripts/contracts/generate.sh`, then `pnpm build` in `contracts/packages/npm`.
4. Commit the sources and generated output together; run the build and generation checks.
5. Update the consumer snapshot/pin according to that repository's rules.

Breaking changes use `!` and an entry in `docs/how/breaking-changes.md`. Events use a new
`eventVersion` while consumers transition.

## Publication is deferred

The GitHub workflow builds packages but does not publish them, including on tags or main. The
former GitLab registry jobs and job-token assumptions have been removed. A future publishing
change must choose the registry, credentials, versioning and consumer access together.

Until the first package release, trellis-web and trellis-ai retain committed generated snapshots.
Web CI fetches only the schema/compiler inputs at its pinned backend SHA from GitHub; it does not
build backend services. Python continues to use its reviewed `contracts-snapshot/` mirror.

## UI operation ownership

The backend exports the schema. Web owns composed UI operation documents and generates their
selection and variable types with the client preset. Run `npm run contracts:generate` in web after
changing an operation. Its `contracts:check` validates operations and compares generated TypeScript.
