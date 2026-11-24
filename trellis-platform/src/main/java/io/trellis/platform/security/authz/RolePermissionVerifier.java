package io.trellis.platform.security.authz;

import io.trellis.common.security.Permission;
import io.trellis.common.security.PermissionRegistry;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Reports {@code role_permission} rows that name a permission no {@code @PermissionCatalog} declares.
 *
 * <p>The table is filled by migrations and by operators, so nothing the compiler sees connects it to the
 * catalog; a typo such as {@code notice:raed} is a row that simply never matches, and the only symptom is a
 * 403 nobody can explain. This logs it once at startup, next to {@code PermissionUsageVerifier}, which does the
 * same job for {@code @RequiresPermission}.
 *
 * <p>It warns rather than failing. A rollback to an earlier build legitimately meets rows for permissions that
 * version does not declare yet, and refusing to start would turn a cosmetic mismatch into an outage.
 */
@Slf4j
@RequiredArgsConstructor
public class RolePermissionVerifier implements SmartInitializingSingleton {
    private static final String DISTINCT_PERMISSIONS = "SELECT DISTINCT permission FROM role_permission";

    private final JdbcClient jdbc;
    private final PermissionRegistry permissionRegistry;

    @Override
    public void afterSingletonsInstantiated() {
        List<String> mapped;
        try {
            mapped = jdbc.sql(DISTINCT_PERMISSIONS).query(String.class).list();
        } catch (DataAccessException unavailable) {
            // Setup mode runs before the schema exists, and a consumer deployable may not own this table.
            log.atDebug().setCause(unavailable).log("Skipping role_permission check: table not readable");
            return;
        }
        List<String> unknown = mapped.stream().filter(this::isNotDeclared).toList();
        if (!unknown.isEmpty()) {
            log.atWarn()
                    .addKeyValue("permissions", unknown)
                    .log("role_permission maps permissions that no catalog declares; those rows grant nothing");
        }
    }

    private boolean isNotDeclared(String value) {
        try {
            return !permissionRegistry.isDeclared(Permission.of(value));
        } catch (IllegalArgumentException malformed) {
            return true;
        }
    }
}
