/**
 * Every in-app path, without the locale prefix: link with `Link` from `@/i18n/navigation`,
 * which adds it. Build parameterised paths with the helpers, never by string concatenation.
 */
export const ROUTES = {
  home: "/",
  login: "/login",
  account: "/account",
  admin: "/admin",
  notices: "/notices",
  noticeDetail: (noticeCode: string) => `/notices/${encodeURIComponent(noticeCode)}`,
} as const;

/** Query parameter carrying the path to return to after sign-in. */
export const REDIRECT_PARAM = "redirect";
