package io.trellis.identity.application.support;

import static io.trellis.identity.IdentityTestFixtures.user;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.trellis.common.error.TrellisException;
import io.trellis.identity.api.error.IdentityErrorCode;
import io.trellis.identity.domain.user.UserStatus;
import org.junit.jupiter.api.Test;

class SignInGuardTest {
  @Test
  void allowsActiveUser() {
    assertDoesNotThrow(() -> SignInGuard.require(user(UserStatus.ACTIVE)));
  }

  @Test
  void requiresEmailVerificationForPendingUser() {
    TrellisException failure =
        assertThrows(
            TrellisException.class,
            () -> SignInGuard.require(user(UserStatus.PENDING_VERIFICATION)));

    assertEquals(IdentityErrorCode.EMAIL_NOT_VERIFIED, failure.errorCode());
  }

  @Test
  void rejectsDisabledUser() {
    TrellisException failure =
        assertThrows(TrellisException.class, () -> SignInGuard.require(user(UserStatus.DISABLED)));

    assertEquals(IdentityErrorCode.USER_DISABLED, failure.errorCode());
  }
}
