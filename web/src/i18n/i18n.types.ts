import type { MessageKeys, NestedKeyOf } from "next-intl";
import type account from "./messages/en/account.json";
import type admin from "./messages/en/admin.json";
import type auth from "./messages/en/auth.json";
import type common from "./messages/en/common.json";
import type errors from "./messages/en/errors.json";
import type notice from "./messages/en/notice.json";
import type { Locale } from "./routing";

/** English is the reference shape; the parity script holds vi and zh to it. */
export type AppMessages = {
  common: typeof common;
  errors: typeof errors;
  auth: typeof auth;
  account: typeof account;
  admin: typeof admin;
  notice: typeof notice;
};

/** Any full message key, `namespace.path.to.key`. Used where a key is chosen as data (error maps). */
export type MessageKey = MessageKeys<AppMessages, NestedKeyOf<AppMessages>>;

declare module "next-intl" {
  // Module augmentation needs an interface; this is what makes `t("...")` keys type-checked.
  // eslint-disable-next-line @typescript-eslint/consistent-type-definitions
  interface AppConfig {
    Locale: Locale;
    Messages: AppMessages;
  }
}
