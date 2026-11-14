import path from "node:path";
import { BASELINE_OPTION_SCHEMA, createBaselineCheck } from "./git-baseline.mjs";

/**
 * local/no-raw-fetch: `fetch` is called only by the HTTP client (src/shared/api/http.ts).
 * Everything else goes through it, so auth headers, the token refresh, error parsing and the
 * mock backend apply to every request. A local variable named `fetch` is not flagged.
 */
const GLOBAL_OBJECTS = new Set(["window", "globalThis", "self"]);
const FETCH = "fetch";

function isShadowed(sourceCode, node) {
  for (let scope = sourceCode.getScope(node); scope; scope = scope.upper) {
    const variable = scope.set.get(FETCH);
    if (variable && variable.defs.length > 0) return true;
  }
  return false;
}

export default {
  meta: {
    type: "problem",
    docs: { description: "Disallow fetch outside the HTTP client" },
    schema: [
      {
        type: "object",
        properties: {
          /** Files (relative to the lint cwd) allowed to call fetch. */
          allow: { type: "array", items: { type: "string" } },
          baseline: BASELINE_OPTION_SCHEMA,
        },
        additionalProperties: false,
      },
    ],
    messages: {
      rawFetch:
        "Do not call fetch directly. Use get/post/put/patch/del from @/shared/api/http, which adds auth, refreshes tokens and throws ApiError.",
    },
  },
  create(context) {
    const options = context.options[0] ?? {};
    const cwd = context.cwd;
    const filename = path.resolve(context.filename);
    const allowed = (options.allow ?? []).some((file) => path.resolve(cwd, file) === filename);
    if (allowed || createBaselineCheck(options.baseline, cwd)(filename)) return {};

    return {
      CallExpression(node) {
        const { callee } = node;
        const direct = callee.type === "Identifier" && callee.name === FETCH;
        const viaGlobal =
          callee.type === "MemberExpression" &&
          !callee.computed &&
          callee.property.type === "Identifier" &&
          callee.property.name === FETCH &&
          callee.object.type === "Identifier" &&
          GLOBAL_OBJECTS.has(callee.object.name);
        if (direct && isShadowed(context.sourceCode, node)) return;
        if (direct || viaGlobal) context.report({ node, messageId: "rawFetch" });
      },
    };
  },
};
