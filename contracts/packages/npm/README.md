# @trellis/contracts

Generated from the Trellis backend contracts: types and a typed client for the REST API, plus the
GraphQL schema. Published from the backend repo on a release tag; never edit the generated files,
and never copy the schema into a consumer.

```bash
# .npmrc in the consuming repo
@trellis:registry=https://gitlab.example.test/api/v4/projects/<backend-project-id>/packages/npm/
//gitlab.example.test/api/v4/projects/<backend-project-id>/packages/npm/:_authToken=${CI_JOB_TOKEN}

pnpm add @trellis/contracts@1.4.0
```

```ts
import { createTrellisClient } from "@trellis/contracts";
import type { components } from "@trellis/contracts/openapi";
import type { Notice } from "@trellis/contracts/graphql";

const api = createTrellisClient({ baseUrl: process.env.TRELLIS_BACKEND_URL });
const { data, error } = await api.GET("/api/notices", { params: { query: { first: 20 } } });

type NoticeView = components["schemas"]["NoticeView"];
```

| Entry point                      | What                                                                     |
| -------------------------------- | ------------------------------------------------------------------------ |
| `@trellis/contracts`             | `createTrellisClient`, `TrellisClient`, and the `paths`/`components`/`operations` types |
| `@trellis/contracts/openapi`     | the generated OpenAPI types on their own                                  |
| `@trellis/contracts/graphql`     | one TypeScript type per GraphQL schema type (scalars: `DateTime` is `string`) |
| `@trellis/contracts/schema.graphqls` | the schema file, so a consumer can generate types for *its own* operations |

Operation types (the result type of a specific query a UI writes) stay in the consumer: point
graphql-codegen at `node_modules/@trellis/contracts/schema.graphqls` and at your own documents.

The client is deliberately thin - no auth, no retries, no error mapping - so the consumer keeps
those. Release process and versioning: `docs/architecture/contracts.md` in the backend repo.
