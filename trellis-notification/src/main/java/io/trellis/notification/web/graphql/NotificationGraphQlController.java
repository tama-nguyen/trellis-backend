package io.trellis.notification.web.graphql;

import io.trellis.common.context.RequestContext;
import io.trellis.common.page.CursorPage;
import io.trellis.notification.api.dto.NotificationAudience;
import io.trellis.notification.application.usecase.reading.GetNotificationStatsUseCase;
import io.trellis.notification.application.usecase.reading.GetNotificationUseCase;
import io.trellis.notification.application.usecase.reading.ListNotificationsQuery;
import io.trellis.notification.application.usecase.reading.ListNotificationsUseCase;
import io.trellis.notification.application.view.NotificationStatsView;
import io.trellis.notification.application.view.NotificationSummary;
import io.trellis.notification.application.view.NotificationView;
import io.trellis.platform.web.graphql.GraphQlContextKeys;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

/**
 * GraphQL read model for the UI (decision T12): {@code graphql/notification/notification.graphqls}.
 * Thin like the REST controller and calling the same {@link NotificationQueries}, which is where
 * authorization happens. {@code stats} is a {@code @BatchMapping}: one stats query per request,
 * however many notifications are selected (no N+1).
 */
@Controller
class NotificationGraphQlController {
  private final GetNotificationUseCase getNotification;
  private final ListNotificationsUseCase listNotifications;
  private final GetNotificationStatsUseCase getNotificationStats;

  NotificationGraphQlController(
      GetNotificationUseCase getNotification,
      ListNotificationsUseCase listNotifications,
      GetNotificationStatsUseCase getNotificationStats) {
    this.getNotification = getNotification;
    this.listNotifications = listNotifications;
    this.getNotificationStats = getNotificationStats;
  }

  @QueryMapping
  NotificationView notification(
      @ContextValue(GraphQlContextKeys.REQUEST_CONTEXT) RequestContext ctx, @Argument String code) {
    return getNotification.execute(ctx, code);
  }

  @QueryMapping
  CursorPage<NotificationSummary> notifications(
      @ContextValue(GraphQlContextKeys.REQUEST_CONTEXT) RequestContext ctx,
      @Argument Integer first,
      @Argument String after,
      @Argument NotificationAudience audience,
      @Argument String query) {
    int size = first == null ? ListNotificationsQuery.DEFAULT_FIRST : first;
    return listNotifications.execute(ctx, new ListNotificationsQuery(size, after, audience, query));
  }

  @BatchMapping(typeName = "Notification", field = "stats")
  Map<NotificationView, NotificationStatsView> notificationStats(
      List<NotificationView> notifications,
      @ContextValue(GraphQlContextKeys.REQUEST_CONTEXT) RequestContext ctx) {
    return byKey(notifications, NotificationView::code, ctx);
  }

  @BatchMapping(typeName = "NotificationSummary", field = "stats")
  Map<NotificationSummary, NotificationStatsView> summaryStats(
      List<NotificationSummary> notifications,
      @ContextValue(GraphQlContextKeys.REQUEST_CONTEXT) RequestContext ctx) {
    return byKey(notifications, NotificationSummary::code, ctx);
  }

  /** Batch contract: one entry per source, in source order. */
  private <T> Map<T, NotificationStatsView> byKey(
      List<T> sources, Function<T, String> codeOf, RequestContext ctx) {
    Map<String, NotificationStatsView> byCode =
        getNotificationStats.execute(ctx, sources.stream().map(codeOf).toList());
    Map<T, NotificationStatsView> result = new LinkedHashMap<>();
    sources.forEach(source -> result.put(source, byCode.get(codeOf.apply(source))));
    return result;
  }
}
