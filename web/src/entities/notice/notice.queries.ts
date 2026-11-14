"use client";

import { useInfiniteQuery, useQuery } from "@tanstack/react-query";
import { noticeApi } from "./notice.api";
import type { NoticeListParams } from "./notice.types";

/** Query keys for the notice domain; invalidate `noticeKeys.all` after any notice mutation. */
export const noticeKeys = {
  all: ["notice"] as const,
  lists: () => [...noticeKeys.all, "list"] as const,
  list: (params: NoticeListParams) => [...noticeKeys.lists(), params] as const,
  details: () => [...noticeKeys.all, "detail"] as const,
  detail: (noticeCode: string) => [...noticeKeys.details(), noticeCode] as const,
};

/** Keyset pages: `fetchNextPage()` appends the next page; there is no page number to jump to. */
export function useNotices(params: NoticeListParams) {
  return useInfiniteQuery({
    queryKey: noticeKeys.list(params),
    queryFn: ({ signal, pageParam }) => noticeApi.list(params, pageParam, signal),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) => (lastPage.hasNext ? lastPage.nextCursor : undefined),
  });
}

export function useNotice(noticeCode: string) {
  return useQuery({
    queryKey: noticeKeys.detail(noticeCode),
    queryFn: ({ signal }) => noticeApi.detail(noticeCode, signal),
  });
}
