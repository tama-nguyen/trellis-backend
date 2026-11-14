"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useCallback, useState } from "react";
import {
  DEFAULT_PAGE_SIZE,
  FIRST_PAGE,
  FIRST_URL_PAGE,
  PAGE_QUERY,
  PAGE_SIZE_OPTIONS,
} from "@/shared/constants/pagination";
import type { PageParams } from "@/shared/types/api.types";

/**
 * Pagination state with both views a UI needs: `page`/`pageSize` for the pager and
 * `start` for "showing 21-40", plus `params` ready for a paged API call. `page` is 0-based,
 * like the API.
 */
export type Pagination = {
  page: number;
  pageSize: number;
  start: number;
  params: Required<Pick<PageParams, "page" | "size">>;
  setPage: (page: number) => void;
  /** Changing the size goes back to the first page; the old page number means nothing now. */
  setPageSize: (size: number) => void;
};

function paginationOf(
  page: number,
  pageSize: number,
  setPage: Pagination["setPage"],
  setPageSize: Pagination["setPageSize"],
): Pagination {
  return { page, pageSize, start: page * pageSize, params: { page, size: pageSize }, setPage, setPageSize };
}

/** In-memory pagination (dialogs, embedded lists). Pages users may share use `useUrlPagination`. */
export function usePagination({ pageSize: initialPageSize = DEFAULT_PAGE_SIZE } = {}): Pagination {
  const [page, setPage] = useState<number>(FIRST_PAGE);
  const [pageSize, setPageSizeState] = useState(initialPageSize);
  const setPageSize = useCallback((size: number) => {
    setPageSizeState(size);
    setPage(FIRST_PAGE);
  }, []);
  return paginationOf(page, pageSize, setPage, setPageSize);
}

function parsePositiveInt(raw: string | null): number | null {
  if (raw === null) return null;
  const value = Number.parseInt(raw, 10);
  return Number.isInteger(value) && value >= 1 ? value : null;
}

/**
 * Pagination kept in the URL (`?page=2&size=50`), so a page can be shared, bookmarked and
 * restored with Back. The URL shows a 1-based page for people; the hook returns 0-based.
 * Unknown sizes fall back to the default so a hand-edited URL cannot request 10 000 rows.
 *
 * Uses `useSearchParams`, so the page rendering it must wrap it in `<Suspense>`.
 */
export function useUrlPagination({ pageSize: defaultPageSize = DEFAULT_PAGE_SIZE } = {}): Pagination {
  const searchParams = useSearchParams();
  const pathname = usePathname();
  const router = useRouter();

  const urlPage = parsePositiveInt(searchParams.get(PAGE_QUERY.PAGE)) ?? FIRST_URL_PAGE;
  const urlSize = parsePositiveInt(searchParams.get(PAGE_QUERY.SIZE));
  const pageSize =
    urlSize !== null && (PAGE_SIZE_OPTIONS as readonly number[]).includes(urlSize)
      ? urlSize
      : defaultPageSize;

  const write = useCallback(
    (page: number, size: number) => {
      const next = new URLSearchParams(searchParams.toString());
      if (page === FIRST_PAGE) next.delete(PAGE_QUERY.PAGE);
      else next.set(PAGE_QUERY.PAGE, String(page + FIRST_URL_PAGE));
      if (size === defaultPageSize) next.delete(PAGE_QUERY.SIZE);
      else next.set(PAGE_QUERY.SIZE, String(size));
      const query = next.toString();
      router.replace(query ? `${pathname}?${query}` : pathname, { scroll: false });
    },
    [searchParams, pathname, router, defaultPageSize],
  );

  const setPage = useCallback(
    (page: number) => write(Math.max(FIRST_PAGE, page), pageSize),
    [write, pageSize],
  );
  const setPageSize = useCallback((size: number) => write(FIRST_PAGE, size), [write]);

  return paginationOf(urlPage - FIRST_URL_PAGE, pageSize, setPage, setPageSize);
}
