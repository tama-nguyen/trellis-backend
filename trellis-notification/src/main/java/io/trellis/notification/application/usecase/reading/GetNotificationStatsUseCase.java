package io.trellis.notification.application.usecase.reading;

import io.micrometer.observation.annotation.Observed;
import io.trellis.common.aop.RequiresPermission;
import io.trellis.common.aop.SecurityAudited;
import io.trellis.common.context.RequestContext;
import io.trellis.common.security.SecurityAction;
import io.trellis.notification.api.security.NotificationPermissions;
import io.trellis.notification.application.port.NotificationStatsRepository;
import io.trellis.notification.application.view.NotificationStatsView;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stats of notifications the caller already holds: they came back from a read that checked
 * visibility, so this does not check it again. One query for the whole batch, so the GraphQL
 * {@code @BatchMapping} costs one call per request whatever the number of notifications.
 */
@Service
@RequiredArgsConstructor
public class GetNotificationStatsUseCase {

  private final NotificationStatsRepository notificationStatsRepository;

  @SecurityAudited(value = SecurityAction.NOTIFICATION_ACCESS, recordSuccess = false)
  @RequiresPermission(anyOf = {NotificationPermissions.READ, NotificationPermissions.MANAGE})
  @Transactional(readOnly = true)
  @Observed(name = "notification.stats")
  public Map<String, NotificationStatsView> execute(
      final RequestContext ctx, final Collection<String> codes) {
    final Map<String, NotificationStatsView> storedStatsByCode =
        notificationStatsRepository.findByCodes(codes);
    final Map<String, NotificationStatsView> statsByCode = new LinkedHashMap<>();
    for (String code : codes) {
      statsByCode.put(
          code, storedStatsByCode.getOrDefault(code, NotificationStatsView.empty(code)));
    }
    return statsByCode;
  }
}
