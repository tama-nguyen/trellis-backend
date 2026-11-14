/** API pages are 0-based (`Page.page`); URLs show 1-based numbers to people. */
export const FIRST_PAGE = 0;
export const FIRST_URL_PAGE = 1;
export const DEFAULT_PAGE_SIZE = 20;
export const PAGE_SIZE_OPTIONS = [10, 20, 50] as const;

/** Query-string names every paged endpoint and every paged URL use. */
export const PAGE_QUERY = {
  PAGE: "page",
  SIZE: "size",
  SORT: "sort",
} as const;

/** Query-string names of keyset (cursor) list endpoints. */
export const CURSOR_QUERY = {
  FIRST: "first",
  AFTER: "after",
} as const;
