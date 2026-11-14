"use client";

import { Search, Users } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { useState } from "react";
import { NOTICE_AUDIENCES, NOTICE_PAGE_SIZE } from "@/entities/notice/notice.constants";
import { useNotices } from "@/entities/notice/notice.queries";
import { Link } from "@/i18n/navigation";
import { getApiErrorMessage } from "@/shared/api/errors";
import { ROUTES } from "@/shared/constants/routes";
import { useDebouncedCallback } from "@/shared/hooks/useDebouncedCallback";
import { useFeatureFlag } from "@/shared/hooks/useFeatureFlag";
import { EventType, track } from "@/shared/lib/analytics";
import { formatDate } from "@/shared/lib/format";
import { screenStateOf } from "@/shared/lib/screenState";
import { Button } from "@/ui/Button";
import { EmptyState } from "@/ui/EmptyState";
import { ErrorState } from "@/ui/ErrorState";
import { Icon } from "@/ui/Icon";
import { Input } from "@/ui/Input";
import { PageShell } from "@/ui/PageShell";
import { Skeleton } from "@/ui/Skeleton";
import { NoticeSeverityBadge } from "./NoticeSeverityBadge";

const SEARCH_INPUT_TYPE = "search";

function NoticeListSkeleton() {
  return (
    <ul className="flex flex-col gap-3">
      {Array.from({ length: NOTICE_PAGE_SIZE }, (_, index) => (
        <li key={index}>
          <Skeleton className="h-16 w-full rounded-card" />
        </li>
      ))}
    </ul>
  );
}

/**
 * Sample feature: a keyset-paged, searchable list. Shows the full pipeline (component -> entity
 * hook -> entity api -> generated client -> http), "load more" over cursor pages, a feature flag,
 * debouncing, analytics and the five screen states.
 */
export function NoticeListPage() {
  const t = useTranslations("notice.list");
  const tAll = useTranslations();
  const locale = useLocale();
  const searchEnabled = useFeatureFlag("noticeSearch");

  // The input updates on every keystroke; only the request is debounced.
  const [searchInput, setSearchInput] = useState("");
  const [query, setQuery] = useState("");
  const applySearch = useDebouncedCallback((value: string) => {
    // A new search is a new query key, so the infinite query starts again from the first page.
    setQuery(value.trim());
    track({ type: EventType.NoticeSearched, queryLength: value.trim().length });
  });

  const notices = useNotices({ first: NOTICE_PAGE_SIZE, query: query || undefined });
  const items = notices.data?.pages.flatMap((page) => page.items);
  const state = screenStateOf(notices, { isEmpty: items?.length === 0 });

  return (
    <PageShell
      title={t("title")}
      description={t("description")}
      actions={
        searchEnabled ? (
          <label className="relative block w-full sm:w-72">
            <span className="sr-only">{t("searchLabel")}</span>
            <span className="pointer-events-none absolute top-1/2 left-3 -translate-y-1/2 text-fg-subtle">
              <Icon icon={Search} size="sm" />
            </span>
            <Input
              type={SEARCH_INPUT_TYPE}
              value={searchInput}
              placeholder={t("searchPlaceholder")}
              className="pl-9"
              onChange={(event) => {
                setSearchInput(event.target.value);
                applySearch(event.target.value);
              }}
            />
          </label>
        ) : null
      }
      state={state}
      loading={<NoticeListSkeleton />}
      empty={
        <EmptyState
          title={query ? t("emptySearchTitle") : t("emptyTitle")}
          description={query ? t("emptySearchDescription") : t("emptyDescription")}
        />
      }
      error={
        <ErrorState
          title={t("errorTitle")}
          description={getApiErrorMessage(notices.error, tAll)}
          retryLabel={tAll("common.actions.retry")}
          onRetry={() => void notices.refetch()}
        />
      }
    >
      {items ? (
        <div className="flex flex-col gap-4">
          <ul className="flex flex-col gap-3">
            {items.map((notice) => (
              <li key={notice.code}>
                <Link
                  href={ROUTES.noticeDetail(notice.code)}
                  onClick={() => track({ type: EventType.NoticeOpened, noticeCode: notice.code })}
                  className="flex flex-col gap-2 rounded-card border border-border bg-surface p-4 transition-colors hover:bg-surface-hover sm:flex-row sm:items-center sm:justify-between"
                >
                  <span className="flex min-w-0 items-center gap-2">
                    {notice.audience === NOTICE_AUDIENCES.STAFF ? (
                      <Icon icon={Users} size="sm" className="text-fg-subtle" aria-label={t("staffLabel")} />
                    ) : null}
                    <span className="truncate font-medium text-fg">{notice.title}</span>
                  </span>
                  <span className="flex shrink-0 items-center gap-3 text-sm text-fg-muted">
                    <NoticeSeverityBadge severity={notice.severity} />
                    {notice.publishedAt ? (
                      <time dateTime={notice.publishedAt}>{formatDate(notice.publishedAt, locale)}</time>
                    ) : null}
                  </span>
                </Link>
              </li>
            ))}
          </ul>
          <div className="flex justify-center">
            {notices.hasNextPage ? (
              <Button
                variant="secondary"
                size="sm"
                isLoading={notices.isFetchingNextPage}
                onClick={() => void notices.fetchNextPage()}
              >
                {t("loadMore")}
              </Button>
            ) : (
              <p className="text-sm text-fg-muted">{t("endOfList")}</p>
            )}
          </div>
        </div>
      ) : null}
    </PageShell>
  );
}
