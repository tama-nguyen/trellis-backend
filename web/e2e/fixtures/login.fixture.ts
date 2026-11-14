import { existsSync, mkdirSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { test as base } from "@playwright/test";
import { TEST_USERS, type UserKey } from "../data/users";

/** e2e/ root; the package is ESM, so there is no __dirname. */
const E2E_DIR = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const AUTH_DIR = path.join(E2E_DIR, ".auth");
const LOGIN_PATH = "/en/login";
const ACCOUNT_URL_PATTERN = /\/account$/;

type LoginOptions = {
  /** Who the test runs as; set per describe with `test.use({ user: USER_KEYS.ADMIN })`. Undefined = signed out. */
  user: UserKey | undefined;
};

/**
 * Signs each user in once per worker and reuses the saved storage state (cookies and
 * localStorage, where the session lives), instead of logging in through the UI in every test.
 */
export const loginFixture = base.extend<LoginOptions, { authFile: (user: UserKey) => Promise<string> }>({
  user: [undefined, { option: true }],
  authFile: [
    async ({ browser }, use, workerInfo) => {
      const files = new Map<UserKey, string>();
      await use(async (user) => {
        const cached = files.get(user);
        if (cached) return cached;
        mkdirSync(AUTH_DIR, { recursive: true });
        const file = path.join(AUTH_DIR, `${user}-${workerInfo.workerIndex}.json`);
        if (!existsSync(file)) {
          const page = await browser.newPage({ baseURL: workerInfo.project.use.baseURL });
          await page.goto(LOGIN_PATH);
          await page.getByLabel("Email").fill(TEST_USERS[user].email);
          await page.getByLabel("Password").fill(TEST_USERS[user].password);
          await page.getByRole("button", { name: "Sign in" }).last().click();
          await page.waitForURL(ACCOUNT_URL_PATTERN);
          await page.context().storageState({ path: file });
          await page.close();
        }
        files.set(user, file);
        return file;
      });
    },
    { scope: "worker" },
  ],
  storageState: async ({ user, authFile, storageState }, use) => {
    await use(user ? await authFile(user) : storageState);
  },
});
