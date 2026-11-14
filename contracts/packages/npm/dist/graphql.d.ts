export type Maybe<T> = T | null;
export type InputMaybe<T> = Maybe<T>;
/** All built-in and custom scalars, mapped to their actual values */
export type Scalars = {
    ID: {
        input: string;
        output: string;
    };
    String: {
        input: string;
        output: string;
    };
    Boolean: {
        input: boolean;
        output: boolean;
    };
    Int: {
        input: number;
        output: number;
    };
    Float: {
        input: number;
        output: number;
    };
    /** ISO-8601 instant in UTC, e.g. 2026-11-14T10:00:00Z */
    DateTime: {
        input: string;
        output: string;
    };
};
export type Notice = {
    archivedAt?: Maybe<Scalars['DateTime']['output']>;
    audience: NoticeAudience;
    body: Scalars['String']['output'];
    code: Scalars['ID']['output'];
    createdAt: Scalars['DateTime']['output'];
    publishedAt?: Maybe<Scalars['DateTime']['output']>;
    severity: NoticeSeverity;
    /** Publication statistics (read model fed by notice.published; eventually consistent). */
    stats: NoticeStats;
    status: NoticeStatus;
    title: Scalars['String']['output'];
    updatedAt: Scalars['DateTime']['output'];
    /** Optimistic-lock version; the REST ETag. */
    version: Scalars['Int']['output'];
};
export type NoticeAudience = 'ALL' | 'STAFF';
export type NoticePage = {
    hasNext: Scalars['Boolean']['output'];
    items: Array<NoticeSummary>;
    nextCursor?: Maybe<Scalars['String']['output']>;
};
export type NoticeSeverity = 'CRITICAL' | 'INFO' | 'WARNING';
export type NoticeStats = {
    firstPublishedAt?: Maybe<Scalars['DateTime']['output']>;
    lastPublishedAt?: Maybe<Scalars['DateTime']['output']>;
    publishCount: Scalars['Int']['output'];
};
export type NoticeStatus = 'ARCHIVED' | 'DRAFT' | 'PUBLISHED';
/** A list row: a notice without its body. */
export type NoticeSummary = {
    audience: NoticeAudience;
    code: Scalars['ID']['output'];
    createdAt: Scalars['DateTime']['output'];
    publishedAt?: Maybe<Scalars['DateTime']['output']>;
    severity: NoticeSeverity;
    stats: NoticeStats;
    status: NoticeStatus;
    title: Scalars['String']['output'];
};
export type Query = {
    /** One notice by code (NT-XXXX-XXXX-XXXX). A notice the caller may not see is a NOT_FOUND error, like a missing one. */
    notice?: Maybe<Notice>;
    /** Newest first. Pass nextCursor as `after` for the next page; same cursor format as GET /api/notices. */
    notices: NoticePage;
    /** Cheap probe that the GraphQL endpoint and schema are up; always returns PONG. */
    ping: Scalars['String']['output'];
};
export type QueryNoticeArgs = {
    code: Scalars['ID']['input'];
};
export type QueryNoticesArgs = {
    after?: InputMaybe<Scalars['String']['input']>;
    audience?: InputMaybe<NoticeAudience>;
    first?: InputMaybe<Scalars['Int']['input']>;
    query?: InputMaybe<Scalars['String']['input']>;
};
//# sourceMappingURL=graphql.d.ts.map