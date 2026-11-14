import { test as base } from "@playwright/test";

export type Logger = {
  info: (message: string, data?: Record<string, unknown>) => void;
  warn: (message: string, data?: Record<string, unknown>) => void;
};

/** Structured log lines prefixed with the test title, so parallel output stays readable. */
export const loggerFixture = base.extend<{ log: Logger }>({
  log: async ({}, use, testInfo) => {
    const write = (level: string) => (message: string, data?: Record<string, unknown>) =>
      console.log(JSON.stringify({ level, test: testInfo.titlePath.join(" > "), message, ...data }));
    await use({ info: write("info"), warn: write("warn") });
  },
});
