package io.trellis.app.platform.dto.rolegrant;

import io.trellis.common.page.CursorPage;
import io.trellis.platform.security.authz.RoleGrant;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** One keyset page of role grants; pass {@code nextCursor} as {@code after} to get the next one. */
public record RoleGrantPage(
    @NotNull List<RoleGrantView> items, String nextCursor, @NotNull boolean hasNext) {
  public static RoleGrantPage of(CursorPage<RoleGrant> page) {
    CursorPage<RoleGrantView> views = page.map(RoleGrantView::of);
    return new RoleGrantPage(views.items(), views.nextCursor(), views.hasNext());
  }
}
