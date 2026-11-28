# @trellis/contracts

Generated from the Trellis backend contracts: types and a typed client for the REST API, plus the
GraphQL schema. Build CI uploads package artifacts; registry publication is deferred. Never edit the
generated files. After the first release, configure the selected registry and pin a version:

```bash
pnpm add @trellis/contracts@1.4.0
```

```ts
import { createTrellisClient } from "@trellis/contracts";
import type { components } from "@trellis/contracts/openapi";
import type { Notification } from "@trellis/contracts/graphql";

const api = createTrellisClient({ baseUrl: process.env.TRELLIS_BACKEND_URL });
const { data, error } = await api.GET("/api/notifications", { params: { query: { first: 20 } } });

type NotificationView = components["schemas"]["NotificationView"];
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
