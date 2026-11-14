import { mergeTests } from "@playwright/test";
import { cleanupFixture } from "./cleanup.fixture";
import { loggerFixture } from "./logger.fixture";
import { loginFixture } from "./login.fixture";
import { seedingFixture } from "./seeding.fixture";

/** The `test` every spec imports. Each fixture is independent; mergeTests composes them. */
export const test = mergeTests(loggerFixture, loginFixture, seedingFixture, cleanupFixture);
export { expect } from "@playwright/test";
