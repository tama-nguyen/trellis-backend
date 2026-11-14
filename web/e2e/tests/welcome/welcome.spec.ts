import { expect, test } from "../../fixtures/base-test";
import { WelcomePage } from "../../pages/welcome.page";
import { AreaTag, TestTag } from "../../utils/test-tags";

test.describe("welcome page", { tag: [TestTag.P0, AreaTag.SHELL] }, () => {
  for (const locale of ["vi", "en", "zh"]) {
    test(`renders in ${locale}`, async ({ page, log }) => {
      const welcome = new WelcomePage(page, locale);
      await welcome.goto();
      log.info("loaded", { url: page.url() });
      await expect(welcome.title).toBeVisible();
      await expect(page.locator("html")).toHaveAttribute("lang", new RegExp(`^${locale}`));
    });
  }

  test.describe("with a Vietnamese browser", () => {
    test.use({ locale: "vi-VN" });

    test("redirects the bare root to the browser's locale", async ({ page }) => {
      await page.goto("/");
      await expect(page).toHaveURL(/\/vi$/);
    });
  });
});
