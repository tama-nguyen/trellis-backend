package io.trellis.notice.application.usecase.reading;

import io.micrometer.observation.annotation.Observed;
import io.trellis.common.aop.RequiresPermission;
import io.trellis.common.context.RequestContext;
import io.trellis.notice.api.security.NoticePermissions;
import io.trellis.notice.application.port.NoticeStatsRepository;
import io.trellis.notice.application.view.NoticeStatsView;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stats of notices the caller already holds: they came back from a read that checked visibility, so
 * this does not check it again. One query for the whole batch, so the GraphQL {@code @BatchMapping}
 * costs one call per request whatever the number of notices.
 */
@Service
@RequiredArgsConstructor
public class GetNoticeStatsUseCase {

  private final NoticeStatsRepository stats;

  @RequiresPermission(anyOf = {NoticePermissions.READ, NoticePermissions.MANAGE})
  @Transactional(readOnly = true)
  @Observed(name = "notice.stats")
  public Map<String, NoticeStatsView> execute(RequestContext ctx, Collection<String> codes) {
    Map<String, NoticeStatsView> found = stats.findByCodes(codes);
    Map<String, NoticeStatsView> result = new LinkedHashMap<>();
    for (String code : codes) {
      result.put(code, found.getOrDefault(code, NoticeStatsView.empty(code)));
    }
    return result;
  }
}
