package io.trellis.platform.security.authz.sql;

import io.trellis.common.security.ResourceScope;
import io.trellis.platform.persistence.jdbc.JdbcSupport;
import io.trellis.platform.security.authz.GrantSubject;
import java.sql.Timestamp;
import java.util.Map;
import java.util.Objects;

/**
 * The one place that knows how a stored grant is read in SQL, so a business repository can push visibility into
 * its own query without naming the platform's tables.
 *
 * <p>The fragment carries every parameter it needs: a caller that merges {@link Fragment#params()} into its
 * query cannot forget one. Expiry is judged against the instant the caller passes in, never the database clock,
 * so a list query and a single-row check agree.
 *
 * <p>Neither half references the filtered row inside its own WHERE clause, and that is the whole point. Written
 * as one {@code EXISTS} correlated on the row id, PostgreSQL re-runs the subquery for every candidate row: on a
 * table of 100k rows with 60k grants that measured 4.2 s and 1.7M buffer hits, with the subquery reported at
 * {@code loops=95000}. Split this way the global check becomes an InitPlan evaluated once and the scoped ids a
 * hashed subplan built once, for the same rows in 18 ms. Keep any new condition uncorrelated for that reason.
 */
public final class GrantPredicate {
    private static final String GRANTED = """
            (
                EXISTS (
                    SELECT 1
                      FROM role_grant rg
                      JOIN role_permission rp ON rp.role_id = rg.role_id
                     WHERE rg.subject_type = :grantSubjectType
                       AND rg.subject_id = :grantSubjectId
                       AND rp.permission = :grantPermission
                       AND (rg.expires_at IS NULL OR rg.expires_at > :grantNow)
                       AND rg.scope_type = :grantGlobal
                       AND rg.scope_id = :grantGlobal
                )
                OR CAST(%s AS VARCHAR) IN (
                    SELECT rg.scope_id
                      FROM role_grant rg
                      JOIN role_permission rp ON rp.role_id = rg.role_id
                     WHERE rg.subject_type = :grantSubjectType
                       AND rg.subject_id = :grantSubjectId
                       AND rp.permission = :grantPermission
                       AND (rg.expires_at IS NULL OR rg.expires_at > :grantNow)
                       AND rg.scope_type = :grantScopeType
                )
            )
            """;

    private GrantPredicate() {}

    /**
     * @param resourceIdColumn column of the row being filtered, spliced as an identifier
     * @param permission the permission the grant must carry
     * @param scopeType scope type the grant must name, besides a global grant
     * @param subject who is asking, and as of when
     * @return a condition that is true when a grant covers the row, as a global grant or one naming its id
     */
    public static Fragment coveredByGrant(
            String resourceIdColumn, String permission, String scopeType, GrantSubject subject) {
        Objects.requireNonNull(permission, "permission");
        Objects.requireNonNull(scopeType, "scopeType");
        Objects.requireNonNull(subject, "subject");
        return new Fragment(
                GRANTED.formatted(JdbcSupport.identifier(resourceIdColumn)),
                Map.of(
                        "grantSubjectType",
                        subject.type().name(),
                        "grantSubjectId",
                        subject.id(),
                        "grantPermission",
                        permission,
                        "grantScopeType",
                        scopeType,
                        "grantGlobal",
                        ResourceScope.GLOBAL,
                        "grantNow",
                        Timestamp.from(subject.asOf())));
    }

    /** A SQL condition and every named parameter it binds. */
    public record Fragment(String sql, Map<String, Object> params) {
        public Fragment {
            params = Map.copyOf(params);
        }
    }
}
