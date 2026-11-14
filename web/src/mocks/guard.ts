import { AUTH_ERRORS, type Permission } from "@/entities/auth/auth.constants";
import type { AuthUser } from "@/entities/auth/auth.types";
import { BEARER_PREFIX, HTTP_HEADERS, HTTP_STATUS } from "@/shared/constants/http";
import { SEED_USERS, type SeedUser } from "./auth.seed";
import { problem } from "./router";

/**
 * Mock tokens: `<prefix><userId>.<expiresAtMs>`. Readable on purpose (this is a mock), but they
 * expire like real ones so the client's refresh path actually runs during development.
 */
export const MOCK_TOKEN = {
  ACCESS_PREFIX: "mock-access.",
  REFRESH_PREFIX: "mock-refresh.",
  SEPARATOR: ".",
  ACCESS_TTL_MS: 5 * 60_000,
} as const;

export function issueTokens(user: SeedUser) {
  return {
    accessToken: `${MOCK_TOKEN.ACCESS_PREFIX}${user.id}${MOCK_TOKEN.SEPARATOR}${Date.now() + MOCK_TOKEN.ACCESS_TTL_MS}`,
    refreshToken: `${MOCK_TOKEN.REFRESH_PREFIX}${user.id}`,
  };
}

export function publicUser({ password: _password, ...user }: SeedUser): AuthUser {
  return user;
}

type TokenCheck = { user: SeedUser } | { errorCode: string };

function checkAccessToken(request: Request): TokenCheck {
  const header = request.headers.get(HTTP_HEADERS.AUTHORIZATION);
  if (!header?.startsWith(BEARER_PREFIX)) return { errorCode: AUTH_ERRORS.UNAUTHENTICATED };
  const token = header.slice(BEARER_PREFIX.length);
  if (!token.startsWith(MOCK_TOKEN.ACCESS_PREFIX)) return { errorCode: AUTH_ERRORS.UNAUTHENTICATED };

  const [userId, expiresAt] = token.slice(MOCK_TOKEN.ACCESS_PREFIX.length).split(MOCK_TOKEN.SEPARATOR);
  const user = SEED_USERS.find((candidate) => candidate.id === userId);
  if (!user) return { errorCode: AUTH_ERRORS.UNAUTHENTICATED };
  if (Number(expiresAt) < Date.now()) return { errorCode: AUTH_ERRORS.TOKEN_EXPIRED };
  return { user };
}

/** The caller, when a valid token is present; anonymous otherwise. For endpoints open to both. */
export function optionalUser(request: Request): SeedUser | undefined {
  const check = checkAccessToken(request);
  return "user" in check ? check.user : undefined;
}

/**
 * Authenticates the caller and, if given, checks one permission, answering with the real
 * backend's codes. Reproduced rather than skipped: a mock that lets everyone through teaches
 * the UI that forbidden actions succeed.
 */
export function guard(
  request: Request,
  path: string,
  permission?: Permission,
): { user: SeedUser } | { error: Response } {
  const check = checkAccessToken(request);
  if ("errorCode" in check) {
    return { error: problem(path, HTTP_STATUS.UNAUTHORIZED, check.errorCode, "Authentication is required.") };
  }
  if (permission && !check.user.permissions.includes(permission)) {
    return { error: problem(path, HTTP_STATUS.FORBIDDEN, AUTH_ERRORS.ACCESS_DENIED, "Not allowed.") };
  }
  return { user: check.user };
}
