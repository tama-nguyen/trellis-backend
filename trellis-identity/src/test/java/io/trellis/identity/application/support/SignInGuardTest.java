package io.trellis.identity.application.support;

import static io.trellis.identity.IdentityTestFixtures.user;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.trellis.common.error.TrellisException;
import io.trellis.identity.api.error.IdentityErrorCode;
import io.trellis.identity.domain.user.UserStatus;
import lombok.val;
import org.junit.jupiter.api.Test;

class SignInGuardTest {
  @Test
  void allowsActiveUser() {
    // Given
    val activeUser = user(UserStatus.ACTIVE);

    // When & Then
    assertDoesNotThrow(() -> SignInGuard.require(activeUser));
  }

  @Test
  void requiresEmailVerificationForPendingUser() {
    // Given
    val pendingUser = user(UserStatus.PENDING_VERIFICATION);

    // When
    val failure = assertThrows(TrellisException.class, () -> SignInGuard.require(pendingUser));

    // Then
    assertEquals(IdentityErrorCode.EMAIL_NOT_VERIFIED, failure.errorCode());
  }

  @Test
  void rejectsDisabledUser() {
    // Given
    val disabledUser = user(UserStatus.DISABLED);

    // When
    val failure = assertThrows(TrellisException.class, () -> SignInGuard.require(disabledUser));

    // Then
    assertEquals(IdentityErrorCode.USER_DISABLED, failure.errorCode());
  }
}
