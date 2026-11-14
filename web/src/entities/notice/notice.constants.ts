import type { ErrorMessageMap } from "@/shared/api/errors";
import { NoticeSummaryAudience } from "@/shared/api/generated/model/noticeSummaryAudience";
import { NoticeSummarySeverity } from "@/shared/api/generated/model/noticeSummarySeverity";
import { NoticeSummaryStatus } from "@/shared/api/generated/model/noticeSummaryStatus";

/** REST paths of the domain; the generated client builds the same URLs. The mock backend matches these. */
export const NOTICE_API = {
  LIST: "/api/notices",
  DETAIL: "/api/notices/:noticeCode",
} as const;

/** Query-string names the list endpoint accepts besides the cursor (`first`, `after`). */
export const NOTICE_QUERY = {
  SEARCH: "query",
  AUDIENCE: "audience",
} as const;

/** Enum values come from the OpenAPI contract (generated); never retype them. */
export const NOTICE_SEVERITIES = NoticeSummarySeverity;
export type NoticeSeverity = (typeof NOTICE_SEVERITIES)[keyof typeof NOTICE_SEVERITIES];

/**
 * Who may read a published notice. STAFF notices need `notice:read-staff`; anyone else gets 404,
 * not 403, so the UI cannot reveal they exist.
 */
export const NOTICE_AUDIENCES = NoticeSummaryAudience;
export type NoticeAudience = (typeof NOTICE_AUDIENCES)[keyof typeof NOTICE_AUDIENCES];

/** Readers only ever see PUBLISHED; managers also see DRAFT and ARCHIVED. */
export const NOTICE_STATUSES = NoticeSummaryStatus;
export type NoticeStatus = (typeof NOTICE_STATUSES)[keyof typeof NOTICE_STATUSES];

/** Backend `NoticeErrorCode` values (REST `code`, GraphQL `extensions.errorCode`). */
export const NOTICE_ERRORS = {
  NOT_FOUND: "NOTICE_NOT_FOUND",
} as const;

export const NOTICE_ERROR_MESSAGES: ErrorMessageMap = {
  [NOTICE_ERRORS.NOT_FOUND]: "notice.errors.notFound",
};

export const NOTICE_PAGE_SIZE = 10;
