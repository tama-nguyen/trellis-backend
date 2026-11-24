package io.trellis.notice.domain.policy;

import io.trellis.notice.api.dto.NoticeAudience;
import io.trellis.notice.api.dto.NoticeStatus;
import io.trellis.notice.domain.model.Notice;
import java.util.EnumSet;
import java.util.Set;

/**
 * Who may see a notice:
 *
 * <ul>
 *   <li>a manager sees every notice in every status;
 *   <li>everyone else sees PUBLISHED notices for ALL, plus STAFF ones as a staff reader;
 *   <li>authors always see their own notices.
 * </ul>
 *
 * A notice the reader may not see must be reported exactly like a missing one (404), so its
 * existence leaks nothing. List queries apply the same rule in SQL ({@code NoticeFilter}).
 */
public final class NoticeVisibility {
  /** The only status a non-manager sees (besides their own notices). */
  public static final Set<NoticeStatus> READER_STATUSES = EnumSet.of(NoticeStatus.PUBLISHED);

  private NoticeVisibility() {}

  public static boolean canSee(NoticeReader reader, Notice notice) {
    if (reader.manager() || (reader.userId() != null && notice.isAuthoredBy(reader.userId()))) {
      return true;
    }
    return READER_STATUSES.contains(notice.status())
        && audiencesFor(reader).contains(notice.content().audience());
  }

  public static Set<NoticeAudience> audiencesFor(NoticeReader reader) {
    return reader.staffReader()
        ? EnumSet.allOf(NoticeAudience.class)
        : EnumSet.of(NoticeAudience.ALL);
  }
}
