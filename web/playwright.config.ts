import { defineConfig, devices } from "@playwright/test";

/** Port for the e2e dev server; distinct from the default 3000 so it can run beside `pnpm dev`. */
const E2E_PORT = Number(process.env.E2E_PORT ?? 3100);
const BASE_URL = process.env.E2E_BASE_URL ?? `http://localhost:${E2E_PORT}`;

export default defineConfig({
  testDir: "./e2e/tests",
  fullyParallel: true,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [["list"], ["html", { open: "never" }]] : "list",
  use: {
    baseURL: BASE_URL,
    trace: "retain-on-failure",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  // Against the dev server with the in-browser mock backend, unless E2E_BASE_URL points elsewhere.
  webServer: process.env.E2E_BASE_URL
    ? undefined
    : {
        command: `pnpm dev --port ${E2E_PORT}`,
        url: `${BASE_URL}/vi`,
        reuseExistingServer: !process.env.CI,
        env: { NEXT_PUBLIC_MOCK_DOMAINS: "true" },
        timeout: 120_000,
      },
});
