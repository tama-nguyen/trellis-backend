package io.trellis.notice.application.port;

import io.trellis.common.page.CursorPage;
import io.trellis.notice.domain.model.Notice;
import io.trellis.notice.domain.value.NoticeCode;
import java.util.Optional;

/** Port to the notice table. Implementations run inside the caller's transaction and never open one. */
public interface NoticeRepository {
    Optional<Notice> findByCode(NoticeCode code);

    void insert(Notice notice);

    /**
     * Writes {@code next} if the stored row still has {@code next.version() - 1}.
     *
     * @throws io.trellis.common.error.ConcurrentModificationException when another writer won the race
     */
    void update(Notice next);

    /** One keyset page, newest first. {@code after} is the opaque cursor of the previous page, or null. */
    CursorPage<Notice> page(NoticeFilter filter, String after, int limit);
}
