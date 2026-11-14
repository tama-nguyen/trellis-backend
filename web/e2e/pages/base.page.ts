import type { Locator, Page } from "@playwright/test";

/** Shared behaviour for page objects: locale-aware navigation and app chrome. */
export abstract class BasePage {
  protected abstract readonly path: string;

  constructor(
    readonly page: Page,
    readonly locale: string = "en",
  ) {}

  /** Toasts (status and alert live regions) raised anywhere on the page. */
  get toasts(): Locator {
    return this.page.locator("[role=status], [role=alert]");
  }

  get header(): Locator {
    return this.page.locator("header").first();
  }

  async goto(): Promise<void> {
    await this.page.goto(`/${this.locale}${this.path}`);
    await this.waitForPageLoad();
  }

  async waitForPageLoad(): Promise<void> {
    await this.page.waitForLoadState("domcontentloaded");
  }
}
