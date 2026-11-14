import { get, post } from "@/shared/api/http";
import { AUTH_API } from "./auth.constants";
import type { AuthTokens, AuthUser, LoginPayload } from "./auth.types";

/** Thin typed wrappers over `http`. No caching, no UI. */
export const authApi = {
  login: (payload: LoginPayload) => post<AuthTokens>(AUTH_API.LOGIN, payload, { anonymous: true }),
  refresh: (refreshToken: string) =>
    post<AuthTokens>(AUTH_API.REFRESH, { refreshToken }, { anonymous: true }),
  logout: (refreshToken: string) => post<undefined>(AUTH_API.LOGOUT, { refreshToken }, { anonymous: true }),
  me: (signal?: AbortSignal) => get<AuthUser>(AUTH_API.ME, { signal }),
};
