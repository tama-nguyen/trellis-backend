package io.trellis.app.platform.application.support;

import io.trellis.app.platform.PlatformErrorCode;
import io.trellis.app.platform.application.port.GrantResourceLookup;
import io.trellis.common.error.TrellisException;
import io.trellis.common.id.EntityIds;
import io.trellis.common.security.ResourceScope;
import io.trellis.identity.application.port.UserRepository;
import io.trellis.platform.security.authz.GrantSubjectType;
import io.trellis.platform.security.jwt.OidcPrincipalIdEncoder;
import java.util.Map;
import lombok.RequiredArgsConstructor;

/** Validates new grants without treating external principals as native accounts. */
@RequiredArgsConstructor
public class GrantTargetValidator {

  private final UserRepository userRepository;

  private final Map<String, GrantResourceLookup> resourceLookups;

  public void requireValid(
      final GrantSubjectType subjectType, final String subjectId, final ResourceScope scope) {
    if (subjectType == null || subjectId == null) {
      throw invalidSubject();
    }

    switch (subjectType) {
      case USER -> requireUser(subjectId);
      case SERVICE -> {
        if (!OidcPrincipalIdEncoder.isServiceId(subjectId)) {
          throw invalidSubject();
        }
      }
    }

    requireScope(scope);
  }

  private void requireUser(final String subjectId) {
    if (OidcPrincipalIdEncoder.isUserId(subjectId)) {
      return;
    }

    final Long userId;
    try {
      userId = EntityIds.parse(subjectId);
    } catch (IllegalArgumentException malformed) {
      throw invalidSubject();
    }

    if (userRepository.userById(userId).isEmpty()) {
      throw new TrellisException(
          PlatformErrorCode.GRANT_SUBJECT_NOT_FOUND, "Native grant subject does not exist");
    }
  }

  private void requireScope(final ResourceScope scope) {
    if (ResourceScope.global().equals(scope)) {
      return;
    }

    if (scope == null
        || ResourceScope.GLOBAL.equals(scope.type())
        || ResourceScope.GLOBAL.equals(scope.id())) {
      throw invalidScope();
    }

    final GrantResourceLookup resourceLookup = resourceLookups.get(scope.type());
    if (resourceLookup == null) {
      throw invalidScope();
    }

    if (!resourceLookup.exists(scope.id())) {
      throw new TrellisException(
          PlatformErrorCode.GRANT_RESOURCE_NOT_FOUND, "Grant resource does not exist");
    }
  }

  private TrellisException invalidSubject() {
    return new TrellisException(
        PlatformErrorCode.INVALID_GRANT_SUBJECT,
        "Grant subject must be a canonical native user ID or a matching encoded external ID");
  }

  private TrellisException invalidScope() {
    return new TrellisException(
        PlatformErrorCode.INVALID_GRANT_SCOPE,
        "Grant scope must be global or use a registered resource type");
  }
}
