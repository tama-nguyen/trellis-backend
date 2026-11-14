import rule from "../no-raw-fetch.mjs";
import { CWD, createRuleTester } from "./ruleTester.mjs";

const allow = [{ allow: ["src/shared/api/http.ts"] }];
const error = { messageId: "rawFetch" };

createRuleTester().run("no-raw-fetch", rule, {
  valid: [
    { code: `await fetch("/api/x");`, options: allow, filename: `${CWD}/src/shared/api/http.ts` },
    { code: `const fetch = makeClient(); fetch("/x");`, filename: `${CWD}/src/a.ts` },
    { code: `function load(fetch) { return fetch("/x"); }`, filename: `${CWD}/src/a.ts` },
    { code: `const real = globalThis.fetch.bind(globalThis);`, filename: `${CWD}/src/mocks/router.ts` },
    { code: `client.fetch("/x");`, filename: `${CWD}/src/a.ts` },
    {
      code: `fetch("/legacy");`,
      options: [{ baseline: { files: ["src/legacy.ts"] } }],
      filename: `${CWD}/src/legacy.ts`,
    },
  ],
  invalid: [
    {
      code: `await fetch("/api/x");`,
      options: allow,
      filename: `${CWD}/src/features/a.tsx`,
      errors: [error],
    },
    { code: `window.fetch("/api/x");`, filename: `${CWD}/src/a.ts`, errors: [error] },
    { code: `globalThis.fetch("/api/x");`, filename: `${CWD}/src/a.ts`, errors: [error] },
    {
      code: `fetch("/new");`,
      options: [{ baseline: { files: ["src/legacy.ts"] } }],
      filename: `${CWD}/src/new.ts`,
      errors: [error],
    },
  ],
});
