package io.trellis.identity.web.rest;

import io.trellis.common.context.RequestContext;
import io.trellis.identity.application.usecase.authentication.LoginUseCase;
import io.trellis.identity.application.usecase.authentication.LogoutUseCase;
import io.trellis.identity.application.usecase.authentication.RefreshSessionUseCase;
import io.trellis.identity.application.usecase.password.ForgotPasswordUseCase;
import io.trellis.identity.application.usecase.password.ResetPasswordUseCase;
import io.trellis.identity.application.usecase.profile.GetCurrentUserUseCase;
import io.trellis.identity.application.usecase.registration.RegisterUseCase;
import io.trellis.identity.application.usecase.registration.VerifyEmailUseCase;
import io.trellis.identity.application.view.AuthTokens;
import io.trellis.identity.application.view.AuthenticatedUser;
import io.trellis.identity.web.rest.dto.authentication.LoginRequest;
import io.trellis.identity.web.rest.dto.authentication.LogoutRequest;
import io.trellis.identity.web.rest.dto.authentication.RefreshRequest;
import io.trellis.identity.web.rest.dto.password.ForgotPasswordRequest;
import io.trellis.identity.web.rest.dto.password.ResetPasswordRequest;
import io.trellis.identity.web.rest.dto.registration.ActionTokenRequest;
import io.trellis.identity.web.rest.dto.registration.RegisterRequest;
import io.trellis.platform.security.jwt.ActorJwtAuthenticationToken;
import io.trellis.platform.web.security.PublicEndpoint;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thin REST edge for native user flows; Keycloak continues through Authorization Code + PKCE.
 *
 * <p>One use case per endpoint: the controller picks the collaborator and passes the request
 * through, and holds no rule of its own.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
class AuthController {
  private static final String EMAIL_CLAIM = "email";
  private static final String NAME_CLAIM = "name";

  private final LoginUseCase signIn;
  private final RegisterUseCase registerUser;
  private final VerifyEmailUseCase verifyEmail;
  private final ForgotPasswordUseCase requestPasswordReset;
  private final ResetPasswordUseCase resetPassword;
  private final RefreshSessionUseCase refreshSession;
  private final LogoutUseCase signOut;
  private final GetCurrentUserUseCase getCurrentUser;

  @PublicEndpoint
  @PostMapping("/login")
  AuthTokens login(RequestContext ctx, @Valid @RequestBody LoginRequest request) {
    return signIn.execute(ctx, request.email(), request.password());
  }

  @PublicEndpoint
  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  void register(RequestContext ctx, @Valid @RequestBody RegisterRequest request) {
    registerUser.execute(ctx, request.email(), request.password(), request.displayName());
  }

  @PublicEndpoint
  @PostMapping("/verify-email")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void verifyEmail(RequestContext ctx, @Valid @RequestBody ActionTokenRequest request) {
    verifyEmail.execute(ctx, request.token());
  }

  @PublicEndpoint
  @PostMapping("/forgot-password")
  @ResponseStatus(HttpStatus.ACCEPTED)
  void forgotPassword(RequestContext ctx, @Valid @RequestBody ForgotPasswordRequest request) {
    requestPasswordReset.execute(ctx, request.email());
  }

  @PublicEndpoint
  @PostMapping("/reset-password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void resetPassword(RequestContext ctx, @Valid @RequestBody ResetPasswordRequest request) {
    resetPassword.execute(ctx, request.email(), request.code(), request.newPassword());
  }

  @PublicEndpoint
  @PostMapping("/refresh")
  AuthTokens refresh(RequestContext ctx, @Valid @RequestBody RefreshRequest request) {
    return refreshSession.execute(ctx, request.refreshToken());
  }

  @PublicEndpoint
  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void logout(RequestContext ctx, @Valid @RequestBody LogoutRequest request) {
    signOut.execute(ctx, request.refreshToken());
  }

  @GetMapping("/me")
  AuthenticatedUser me(RequestContext ctx, Authentication authentication) {
    if (authentication instanceof ActorJwtAuthenticationToken jwt) {
      return getCurrentUser.execute(
          ctx,
          jwt.getToken().getClaimAsString(EMAIL_CLAIM),
          jwt.getToken().getClaimAsString(NAME_CLAIM));
    }
    return getCurrentUser.execute(ctx, null, null);
  }
}
