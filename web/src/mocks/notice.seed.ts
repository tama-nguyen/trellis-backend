import { NOTICE_AUDIENCES, NOTICE_SEVERITIES, NOTICE_STATUSES } from "@/entities/notice/notice.constants";
import type { NoticeView } from "@/shared/api/generated/model/noticeView";

/**
 * A notice as the backend stores it: the generated REST view plus its author, which is server state
 * and never crosses the wire, and its publication count (the `notice_stats` read model).
 */
export type StoredNotice = NoticeView & { authorId: string; publishCount: number };

const SAMPLE_TOPICS = [
  "Scheduled maintenance window",
  "New study materials available",
  "Holiday timetable",
  "Updated terms of use",
  "Mobile app release",
  "Exam registration opens",
  "Library hours extended",
  "Password policy change",
  "Community event this weekend",
  "Survey: help us improve",
  "Service restored",
  "Course catalogue refreshed",
] as const;

const SEVERITY_CYCLE = [
  NOTICE_SEVERITIES.INFO,
  NOTICE_SEVERITIES.INFO,
  NOTICE_SEVERITIES.WARNING,
  NOTICE_SEVERITIES.CRITICAL,
];
const DAY_MS = 24 * 3600 * 1000;
/** Fixed origin so the seed is the same on every load. */
const SEED_EPOCH = Date.UTC(2026, 10, 1, 9, 0, 0);
/** Valid reference codes (Crockford base32: S, E, D, T, A, F are all in the alphabet). */
const SEED_CODE_PREFIX = "NT-SEED-0000-";
const SEED_AUTHOR = "u-admin";

const isoDaysBefore = (days: number) => new Date(SEED_EPOCH - days * DAY_MS).toISOString();

export const SEED_NOTICES: readonly StoredNotice[] = [
  ...SAMPLE_TOPICS.flatMap((topic, index) =>
    [0, 1].map((round): StoredNotice => {
      const number = index * 2 + round + 1;
      const at = isoDaysBefore(number);
      return {
        code: `${SEED_CODE_PREFIX}${String(number).padStart(4, "0")}`,
        title: round === 0 ? topic : `${topic} (follow-up)`,
        body: `${topic}. This is sample notice number ${number}, used to show the list, detail and paging states.`,
        severity: SEVERITY_CYCLE[number % SEVERITY_CYCLE.length] ?? NOTICE_SEVERITIES.INFO,
        audience: NOTICE_AUDIENCES.ALL,
        status: NOTICE_STATUSES.PUBLISHED,
        version: 2,
        createdAt: at,
        updatedAt: at,
        publishedAt: at,
        authorId: SEED_AUTHOR,
        publishCount: 1,
      };
    }),
  ),
  {
    code: "NT-SEED-STAF-0001",
    title: "Staff briefing: next quarter roadmap",
    body: "Addressed to staff. Readers without notice:read-staff get 404, exactly like a missing notice.",
    severity: NOTICE_SEVERITIES.INFO,
    audience: NOTICE_AUDIENCES.STAFF,
    status: NOTICE_STATUSES.PUBLISHED,
    version: 2,
    createdAt: new Date(SEED_EPOCH).toISOString(),
    updatedAt: new Date(SEED_EPOCH).toISOString(),
    publishedAt: new Date(SEED_EPOCH).toISOString(),
    authorId: SEED_AUTHOR,
    publishCount: 2,
  },
];
