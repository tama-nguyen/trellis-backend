import { AUTH_API, AUTH_ERRORS } from "@/entities/auth/auth.constants";
import type { LoginPayload } from "@/entities/auth/auth.types";
import { HTTP_METHODS, HTTP_STATUS } from "@/shared/constants/http";
import { SEED_USERS } from "./auth.seed";
import { MOCK_TOKEN, guard, issueTokens, publicUser } from "./guard";
import { json, noContent, problem, type MockRoute } from "./router";

export const authRoutes: MockRoute[] = [
  {
    method: HTTP_METHODS.POST,
    pattern: AUTH_API.LOGIN,
    handler: async ({ request, path }) => {
      const { email, password } = (await request.json()) as LoginPayload;
      const user = SEED_USERS.find(
        (candidate) => candidate.email === email.trim().toLowerCase() && candidate.password === password,
      );
      // Same answer for unknown email and wrong password: a difference would confirm which emails exist.
      if (!user) {
        return problem(
          path,
          HTTP_STATUS.UNAUTHORIZED,
          AUTH_ERRORS.INVALID_CREDENTIALS,
          "Invalid credentials.",
        );
      }
      return json(issueTokens(user));
    },
  },
  {
    method: HTTP_METHODS.POST,
    pattern: AUTH_API.REFRESH,
    handler: async ({ request, path }) => {
      const { refreshToken } = (await request.json()) as { refreshToken: string };
      const userId = refreshToken.startsWith(MOCK_TOKEN.REFRESH_PREFIX)
        ? refreshToken.slice(MOCK_TOKEN.REFRESH_PREFIX.length)
        : undefined;
      const user = SEED_USERS.find((candidate) => candidate.id === userId);
      if (!user) {
        return problem(
          path,
          HTTP_STATUS.UNAUTHORIZED,
          AUTH_ERRORS.REFRESH_TOKEN_INVALID,
          "Refresh token invalid.",
        );
      }
      return json(issueTokens(user));
    },
  },
  {
    method: HTTP_METHODS.POST,
    pattern: AUTH_API.LOGOUT,
    handler: () => noContent(),
  },
  {
    method: HTTP_METHODS.GET,
    pattern: AUTH_API.ME,
    handler: ({ request, path }) => {
      const result = guard(request, path);
      return "error" in result ? result.error : json(publicUser(result.user));
    },
  },
];
