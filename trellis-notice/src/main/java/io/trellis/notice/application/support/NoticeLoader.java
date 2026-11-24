package io.trellis.notice.application.support;

import io.trellis.common.context.RequestContext;
import io.trellis.notice.api.error.NoticeErrors;
import io.trellis.notice.application.port.NoticeRepository;
import io.trellis.notice.domain.model.Notice;
import io.trellis.notice.domain.value.NoticeCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Loads a notice the caller is allowed to see, or reports it missing.
 *
 * <p>A collaborator, not a use case: four of them start this way, and they must agree on what "not
 * found" means. A caller who may not see a notice has to get the same answer as one asking about a
 * notice that does not exist, or the error itself becomes a way to enumerate them.
 */
@Service
@RequiredArgsConstructor
public class NoticeLoader {

  private final NoticeRepository noticeRepository;
  private final NoticeAccess noticeAccess;

  public Notice loadVisible(final RequestContext ctx, final String code) {
    final NoticeCode noticeCode = NoticeCode.parseOrNotFound(code);
    final Notice notice =
        noticeRepository
            .findByCode(noticeCode)
            .orElseThrow(() -> NoticeErrors.notFound(noticeCode.value()));

    return noticeAccess.requireVisible(ctx, notice);
  }
}
