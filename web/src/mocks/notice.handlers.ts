import { PERMISSIONS } from "@/entities/auth/auth.constants";
import {
  NOTICE_API,
  NOTICE_AUDIENCES,
  NOTICE_ERRORS,
  NOTICE_QUERY,
  NOTICE_STATUSES,
} from "@/entities/notice/notice.constants";
import { NoticeDetailDocument } from "@/entities/notice/notice.graphql";
import type { NoticeDetail, NoticePage, NoticeSummary } from "@/entities/notice/notice.types";
import { HTTP_METHODS, HTTP_STATUS } from "@/shared/constants/http";
import { CURSOR_QUERY } from "@/shared/constants/pagination";
import type { NoticeDetailQuery, NoticeDetailQueryVariables } from "@/shared/graphql/generated/graphql";
import {
  GRAPHQL_ENDPOINT,
  GRAPHQL_ERROR_CODES,
  type GraphqlResponse,
} from "@/shared/graphql/graphql.constants";
import type { SeedUser } from "./auth.seed";
import { guard } from "./guard";
import { SEED_NOTICES, type StoredNotice } from "./notice.seed";
import { json, problem, type MockRoute } from "./router";

const DEFAULT_FIRST = 20;
const MAX_FIRST = 100;
/** Field of the GraphQL detail query, used as the error `path`. */
const NOTICE_FIELD = "notice";

/**
 * The backend's visibility rule (`NoticeAccess`): managers see everything; others see PUBLISHED
 * notices for ALL, plus STAFF ones with `notice:read-staff`; authors see their own. Anything else
 * answers 404 like a missing notice, so a 403 never confirms that it exists.
 */
function visibleTo(notice: StoredNotice, user: SeedUser): boolean {
  const can = (permission: string) => user.permissions.some((granted) => granted === permission);
  if (can(PERMISSIONS.NOTICE_MANAGE) || notice.authorId === user.id) return true;
  return (
    notice.status === NOTICE_STATUSES.PUBLISHED &&
    (notice.audience === NOTICE_AUDIENCES.ALL || can(PERMISSIONS.NOTICE_READ_STAFF))
  );
}

const toSummary = (notice: StoredNotice): NoticeSummary => ({
  code: notice.code,
  title: notice.title,
  severity: notice.severity,
  audience: notice.audience,
  status: notice.status,
  createdAt: notice.createdAt,
  publishedAt: notice.publishedAt,
});

const toDetail = (notice: StoredNotice): NoticeDetail => ({
  code: notice.code,
  title: notice.title,
  body: notice.body,
  severity: notice.severity,
  audience: notice.audience,
  status: notice.status,
  publishedAt: notice.publishedAt ?? null,
  stats: { publishCount: notice.publishCount, lastPublishedAt: notice.publishedAt ?? null },
});

/** Opaque to the client like the real HMAC cursor; here just the offset of the next row. */
const encodeCursor = (offset: number) => btoa(String(offset));
function decodeCursor(cursor: string | null): number {
  if (!cursor) return 0;
  const offset = Number.parseInt(atob(cursor), 10);
  return Number.isInteger(offset) && offset >= 0 ? offset : 0;
}

function firstParam(query: URLSearchParams): number {
  const value = Number.parseInt(query.get(CURSOR_QUERY.FIRST) ?? "", 10);
  return Number.isInteger(value) && value > 0 ? Math.min(value, MAX_FIRST) : DEFAULT_FIRST;
}

function findVisible(code: string | undefined, user: SeedUser): StoredNotice | undefined {
  const notice = SEED_NOTICES.find((candidate) => candidate.code === code);
  return notice && visibleTo(notice, user) ? notice : undefined;
}

function graphqlNotFound(code: string): GraphqlResponse<NoticeDetailQuery> {
  return {
    data: { notice: null },
    errors: [
      {
        message: `Notice ${code} not found`,
        path: [NOTICE_FIELD],
        extensions: { code: GRAPHQL_ERROR_CODES.NOT_FOUND, errorCode: NOTICE_ERRORS.NOT_FOUND },
      },
    ],
  };
}

type GraphqlRequestBody = { query?: string; variables?: Partial<NoticeDetailQueryVariables> };

export const noticeRoutes: MockRoute[] = [
  {
    method: HTTP_METHODS.GET,
    pattern: NOTICE_API.LIST,
    handler: ({ request, query, path }) => {
      const auth = guard(request, path, PERMISSIONS.NOTICE_READ);
      if ("error" in auth) return auth.error;
      const search = query.get(NOTICE_QUERY.SEARCH)?.trim().toLowerCase() ?? "";
      const audience = query.get(NOTICE_QUERY.AUDIENCE);
      const matches = SEED_NOTICES.filter(
        (notice) =>
          visibleTo(notice, auth.user) &&
          (!audience || notice.audience === audience) &&
          (!search || notice.title.toLowerCase().includes(search)),
      ).toSorted((a, b) => b.createdAt.localeCompare(a.createdAt));
      const offset = decodeCursor(query.get(CURSOR_QUERY.AFTER));
      const first = firstParam(query);
      const hasNext = offset + first < matches.length;
      const page: NoticePage = {
        items: matches.slice(offset, offset + first).map(toSummary),
        hasNext,
        ...(hasNext ? { nextCursor: encodeCursor(offset + first) } : {}),
      };
      return json(page);
    },
  },
  {
    method: HTTP_METHODS.GET,
    pattern: NOTICE_API.DETAIL,
    handler: ({ request, params, path }) => {
      const auth = guard(request, path, PERMISSIONS.NOTICE_READ);
      if ("error" in auth) return auth.error;
      const notice = findVisible(params.noticeCode, auth.user);
      if (!notice) {
        return problem(path, HTTP_STATUS.NOT_FOUND, NOTICE_ERRORS.NOT_FOUND, "Notice not found.");
      }
      const { authorId: _authorId, publishCount: _publishCount, ...view } = notice;
      return json(view);
    },
  },
  {
    // The mock GraphQL endpoint knows the notice detail document only; that is all the UI sends.
    method: HTTP_METHODS.POST,
    pattern: GRAPHQL_ENDPOINT,
    handler: async ({ request, path }) => {
      const auth = guard(request, path, PERMISSIONS.NOTICE_READ);
      if ("error" in auth) return auth.error;
      const body = (await request.json()) as GraphqlRequestBody;
      if (body.query !== NoticeDetailDocument.toString()) {
        return json({
          errors: [
            { message: "Unknown operation (mock)", extensions: { code: GRAPHQL_ERROR_CODES.BAD_REQUEST } },
          ],
        });
      }
      const code = String(body.variables?.code ?? "");
      const notice = findVisible(code, auth.user);
      const response: GraphqlResponse<NoticeDetailQuery> = notice
        ? { data: { notice: toDetail(notice) } }
        : graphqlNotFound(code);
      return json(response);
    },
  },
];
