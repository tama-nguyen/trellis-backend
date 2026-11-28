# Native authentication integration verification

The native browser lifecycle uses the existing Java identity REST API through the Next.js proxy.
Register, verify-email, forgot-password and reset-password forms join login and account screens.
The header resolves the current user and offers logout. Error responses use the shared ApiError
mapping and localized messages in English, Vietnamese and Chinese.

The production browser session decision in
[the integration plan](../plan/plan-authentication-user-fe-be.md) remains open. Local verification
uses the existing browser-held token pair. OIDC callback login and profile editing are outside this
native slice. Native sign-in and recovery work while Keycloak is stopped.

## Changes verified

- Registration queues encrypted durable mail; the worker delivers verification through Mailpit.
- An unverified account cannot log in. The explicit verification action enables login. Reusing
  the verification token fails.
- An invalid native access credential produces 401 without consulting OIDC. The browser rotates
  its genuine refresh credential and retries the current-user request successfully.
- Recovery requests return a generic confirmation and deliver a reset code. Reset submits
  email, code and newPassword. The old password fails, the code cannot be reused, and login with
  the new password succeeds.
- Logout through the header invalidates the current credential. Protected account navigation
  returns to sign-in.
- Session termination and account changes remove user query data. Temporary refresh outages
  preserve the session; definitive rejection ends it.
- The browser BFF removes `X-Internal-Key` and `X-User-Id` in both directions. An AI service may
  use that pair only when it has no bearer token: the backend resolves an ACTIVE native decimal Long ID or an
  encoded OIDC principal and then applies that user's ordinary permissions. A key alone grants no
  user permission; malformed or mixed bearer/internal authentication receives 401.

The initial browser attempts exposed terminal native-token fallback to JWT, a missing header
AuthMenu integration, and test selectors/navigation timing mistakes. The provider fix has a JUnit
regression test. Browser tests wait for route transitions and target the intended status text.

## Reproduce the local infrastructure

From trellis-backend, use JDK 21 and Docker. This isolated compose project owns its own database.

```sh
COMPOSE_PROJECT_NAME=trellis-auth-check TRELLIS_PG_PORT=25543 \
TRELLIS_MAILPIT_SMTP_PORT=21025 TRELLIS_MAILPIT_HTTP_PORT=28025 \
docker compose -f build-logic/docker/compose.yml --profile full up -d --wait postgres mailpit

export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew --offline :trellis-app:bootJar

docker run -d --name trellis-auth-check-backend \
  --network trellis-auth-check_default \
  -p 127.0.0.1:28180:8080 -p 127.0.0.1:28181:8081 \
  -v "$PWD/trellis-app/build/libs/trellis-app-0.1.0-SNAPSHOT.jar:/app.jar:ro" \
  -e SPRING_PROFILES_ACTIVE=dev \
  -e TRELLIS_DB_URL=jdbc:postgresql://postgres:5432/trellis \
  -e TRELLIS_SMTP_HOST=mailpit -e TRELLIS_SMTP_PORT=1025 \
  -e SPRING_LIQUIBASE_ENABLED=true \
  -e TRELLIS_IDENTITY_VERIFY_EMAIL_URI=http://localhost:23100/en/verify-email \
  -e MANAGEMENT_TRACING_ENABLED=false \
  -e 'SPRING_APPLICATION_JSON={"trellis":{"security":{"cors":{"allowed-origins":"http://localhost:23100"}}},"logging":{"level":{"org.springframework.jdbc":"INFO","org.hibernate.SQL":"INFO"}}}' \
  eclipse-temurin:21.0.13_9-jre-noble java -jar /app.jar
```

Wait for backend liveness at http://localhost:28181/actuator/health/liveness before driving requests.
Use the dev profile only on this disposable local stack. The dev encryption and quota secrets
are supplied by existing local defaults.

## Docker frontend and observed browser result

From trellis-web, build the existing production image with mocks disabled, then join the isolated
network. The backend jar and frontend image used the modified source.

```sh
docker build -t trellis/web:auth-check --build-arg NEXT_PUBLIC_MOCK_DOMAINS=false .
docker run -d --name trellis-auth-check-web --network trellis-auth-check_default   -p 127.0.0.1:23100:3000   -e TRELLIS_BACKEND_URL=http://trellis-auth-check-backend:8080 trellis/web:auth-check
```

The Docker build exited 0. Frontend, PostgreSQL and Mailpit reported healthy; backend liveness
reported UP. The Chromium lifecycle command below exited 0 with one passed test in 15.2 seconds
against these containers. The earlier dev-server run also passed. The browser actually exercised
the register, verification, current-user, refresh, emailed code reset and logout screens through
the Next.js proxy. Backend API assertions verify rejected/reused credentials and revocation. The reset assertion
first proves the current access credential is valid, then proves both that access credential and
its refresh credential are rejected after reset.

Frontend checks reported 75 passing Vitest tests in eight files and successful typecheck, lint,
contract checks, locale parity (916 keys) and formatting. An initial Docker compile failed on a
translation key while header integration was in progress; the subsequent image build and browser
run passed. The production build uses existing browser-held credentials, so this local result
does not establish production session security.

## Automated checks

Executed backend commands, each exited 0.

```sh
./gradlew --offline :trellis-identity:test :trellis-app:bootJar
./gradlew --offline :trellis-identity:spotlessCheck :trellis-app:checkApiContracts \
  :trellis-app:test --tests '*IdentityRegistrationTest' --tests '*IdentityMailDeliveryTest' \
  --tests '*ForgotPasswordRateLimitTest' --tests '*OidcIdentityIsolationTest'
```

JUnit XML reported 44 identity tests and 14 selected application tests, with zero failures,
errors or skipped tests. The provider unit test asserts that OIDC is never called when the native
session is invalid. Existing tests continue to cover real database transactions and mail behavior.

Executed web checks include typecheck, lint, contracts:check, i18n:check, test and format:check.
The native browser test lives at web/e2e/tests/auth/native-lifecycle.spec.ts and uses a new neutral
account on every run. It requires E2E_BASE_URL and E2E_MAILPIT_URL. Tracing and screenshots are
disabled for this credential flow. Use a disposable Mailpit instance and database.

```sh
# trellis-web
E2E_BASE_URL=http://localhost:23100 E2E_MAILPIT_URL=http://localhost:28025 \
npm run test:e2e -- e2e/tests/auth/native-lifecycle.spec.ts
```

Cross-project GitLab credentials and production proxy/cookie behavior were not exercised.
The web CI schema pin must advance with the backend contract commit before these changes are merged.

## Internal delegation verification

The backend is configured with `TRELLIS_INTERNAL_KEY` only in the disposable backend container.
The direct check selected an ACTIVE fixture user, called `GET /api/auth/me` using the configured key
and that user's decimal ID, and confirmed the returned ID matched. A request with an incorrect key
returned 401. The command deliberately did not print the key, bearer credential, email or response
body. `InternalKeySecurityTest` independently covers native status checks, encoded OIDC principals,
current role grants, bad keys and incomplete header pairs. The Playwright security-boundary test
confirms a browser-supplied pair is stripped by the BFF and cannot authenticate.

## Stop only the verification services

```sh
docker stop trellis-auth-check-web trellis-auth-check-backend
docker rm trellis-auth-check-web trellis-auth-check-backend
COMPOSE_PROJECT_NAME=trellis-auth-check docker compose \
  -f build-logic/docker/compose.yml --profile full down
```

The verification database volume persists across down/up. Remove it only when its disposable data
is no longer needed. Existing services in other compose projects are unaffected.
