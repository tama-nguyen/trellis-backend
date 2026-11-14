import { fileURLToPath } from "node:url";
import { defineConfig } from "vitest/config";

/**
 * Unit tests. Per decision T5 only tooling is tested today (lint rules, scripts); feature tests
 * are written when the owner asks. Playwright specs live in e2e/ and are not picked up here.
 */
export default defineConfig({
  resolve: {
    alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) },
  },
  test: {
    include: [
      "eslint-rules/**/__tests__/*.test.mjs",
      "scripts/**/__tests__/*.test.mjs",
      "src/**/__tests__/*.test.{ts,tsx}",
    ],
    environment: "node",
    passWithNoTests: true,
    coverage: {
      provider: "v8",
      // Cobertura XML for the CI merge-request coverage widget; text summary for humans.
      reporter: ["text-summary", "cobertura"],
      reportsDirectory: "coverage",
      include: ["eslint-rules/**/*.mjs", "scripts/**/*.mjs", "src/**/*.{ts,tsx}"],
      exclude: ["**/__tests__/**"],
    },
  },
});
