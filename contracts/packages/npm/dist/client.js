/**
 * The typed client: `openapi-fetch` bound to the generated `paths` of the backend OpenAPI document,
 * so every path, method, body, query parameter and response is checked by TypeScript.
 *
 * It is deliberately thin. Authentication, retries, correlation headers and error mapping belong to
 * the application (in trellis-web: the BFF route handlers), which passes them in as `fetch`,
 * `headers` or middleware.
 */
import createClient, {} from "openapi-fetch";
export function createTrellisClient(options) {
    return createClient(options);
}
//# sourceMappingURL=client.js.map