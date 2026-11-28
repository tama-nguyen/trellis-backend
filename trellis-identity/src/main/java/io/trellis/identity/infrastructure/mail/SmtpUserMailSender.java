package io.trellis.identity.infrastructure.mail;

import io.trellis.identity.IdentityProperties;
import io.trellis.identity.application.port.IdentityMailDelivery;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/** SMTP adapter for one-time user actions. */
@Component
@RequiredArgsConstructor
public class SmtpUserMailSender implements IdentityMailDelivery {
  private static final String TOKEN_PARAMETER = "token";
  private static final String VERIFY_SUBJECT = "Verify your Trellis email";
  private static final String RESET_SUBJECT = "Reset your Trellis password";

  private final JavaMailSender mail;
  private final IdentityProperties properties;

  @Override
  public void sendEmailVerification(String email, String displayName, String token) {
    send(
        email,
        VERIFY_SUBJECT,
        "Hello "
            + displayName
            + ",\n\nVerify your email: "
            + actionUri(properties.verifyEmailUri(), token));
  }

  @Override
  public void sendPasswordResetCode(String email, String displayName, String code) {
    send(
        email, RESET_SUBJECT, "Hello " + displayName + ",\n\nYour password reset code is: " + code);
  }

  private void send(String recipient, String subject, String body) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(properties.mailFrom());
    message.setTo(recipient);
    message.setSubject(subject);
    message.setText(body);
    mail.send(message);
  }

  private static URI actionUri(URI base, String token) {
    return UriComponentsBuilder.fromUri(base)
        .queryParam(TOKEN_PARAMETER, token)
        .build()
        .encode()
        .toUri();
  }
}
