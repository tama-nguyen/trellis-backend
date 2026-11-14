/**
 * `@trellis/contracts`: everything the backend publishes for TypeScript consumers.
 *
 * - `createTrellisClient` / `TrellisClient`: the typed REST client (openapi-fetch over `paths`).
 * - `./openapi`: the raw generated `paths`, `components` and `operations` types.
 * - `./graphql`: TypeScript types for every type in the GraphQL schema.
 * - `./schema.graphqls`: the schema itself, for a consumer that generates its own operation types.
 */
export { createTrellisClient, type TrellisClient } from "./client.js";
export type { components, operations, paths } from "./openapi.js";
//# sourceMappingURL=index.d.ts.map