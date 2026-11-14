/**
 * The typed client: `openapi-fetch` bound to the generated `paths` of the backend OpenAPI document,
 * so every path, method, body, query parameter and response is checked by TypeScript.
 *
 * It is deliberately thin. Authentication, retries, correlation headers and error mapping belong to
 * the application (in trellis-web: the BFF route handlers), which passes them in as `fetch`,
 * `headers` or middleware.
 */
import { type ClientOptions, type Client } from "openapi-fetch";
import type { paths } from "./openapi.js";
export type TrellisClient = Client<paths>;
export declare function createTrellisClient(options: ClientOptions): TrellisClient;
//# sourceMappingURL=client.d.ts.map