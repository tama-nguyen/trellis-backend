import type { NoticePage as NoticePageDto } from "@/shared/api/generated/model/noticePage";
import type { NoticeSummary as NoticeSummaryDto } from "@/shared/api/generated/model/noticeSummary";
import type { NoticeDetailQuery } from "@/shared/graphql/generated/graphql";
import type { NoticeAudience } from "./notice.constants";

/** A list row, exactly as the REST contract defines it (generated from contracts/openapi). */
export type NoticeSummary = NoticeSummaryDto;

/** One keyset page of the list. */
export type NoticePage = NoticePageDto;

/** The detail read model, exactly as the GraphQL document selects it (generated). */
export type NoticeDetail = NonNullable<NoticeDetailQuery["notice"]>;

/** List filters; the cursor is managed by the infinite query, not part of the key. */
export type NoticeListParams = {
  first: number;
  query?: string;
  audience?: NoticeAudience;
};
