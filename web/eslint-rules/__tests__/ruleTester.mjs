import { RuleTester } from "eslint";
import tseslint from "typescript-eslint";
import { afterAll, describe, it } from "vitest";

// RuleTester drives the test framework through these hooks; point them at vitest.
RuleTester.afterAll = afterAll;
RuleTester.describe = describe;
RuleTester.it = it;
RuleTester.itOnly = it.only;

/** A RuleTester that parses TSX, as the real config does. */
export function createRuleTester() {
  return new RuleTester({
    languageOptions: {
      parser: tseslint.parser,
      parserOptions: { ecmaFeatures: { jsx: true } },
    },
  });
}

/**
 * RuleTester lints with the process cwd (no per-case override), so path-based cases build
 * filenames from it. `pnpm test` runs from web/, matching the real lint run.
 */
export const CWD = process.cwd();
