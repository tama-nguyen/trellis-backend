import type { Permission } from "./auth.constants";

export type AuthTokens = {
  accessToken: string;
  refreshToken: string;
};

export type LoginPayload = {
  email: string;
  password: string;
};

/** The signed-in user (`GET /api/auth/me`). Permissions are data; never gate UI on a role name. */
export type AuthUser = {
  id: string;
  email: string;
  displayName: string;
  permissions: Permission[];
};
