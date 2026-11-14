import { listNotices } from "@/shared/api/generated/notice";
import { executeGraphql } from "@/shared/graphql/executeGraphql";
import { NoticeDetailDocument } from "./notice.graphql";
import type { NoticeDetail, NoticeListParams } from "./notice.types";
import { NOTICE_ERRORS } from "./notice.constants";
import { ApiError } from "@/shared/api/apiError";
import { HTTP_STATUS } from "@/shared/constants/http";

/**
 * Thin wrappers over the generated clients: the REST list (generated from OpenAPI) and the GraphQL
 * detail read model. No caching, no UI.
 */
export const noticeApi = {
  list: ({ first, query, audience }: NoticeListParams, after: string | undefined, signal?: AbortSignal) =>
    listNotices({ first, after, query, audience }, { signal }),

  detail: async (noticeCode: string, signal?: AbortSignal): Promise<NoticeDetail> => {
    const { notice } = await executeGraphql(NoticeDetailDocument, { code: noticeCode }, signal);
    // The backend reports a hidden or missing notice as a NOT_FOUND error; a bare null means the same.
    if (!notice) {
      throw new ApiError("Notice not found", HTTP_STATUS.NOT_FOUND, {
        status: HTTP_STATUS.NOT_FOUND,
        code: NOTICE_ERRORS.NOT_FOUND,
      });
    }
    return notice;
  },
};
