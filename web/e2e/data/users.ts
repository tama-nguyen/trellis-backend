import { SEED_USERS } from "../../src/mocks/auth.seed";

/** Test users, named by role. Credentials come from the mock seed (test values only). */
export const USER_KEYS = { MEMBER: "member", ADMIN: "admin" } as const;
export type UserKey = (typeof USER_KEYS)[keyof typeof USER_KEYS];

const byId = (id: string) => {
  const user = SEED_USERS.find((candidate) => candidate.id === id);
  if (!user) throw new Error(`Seed user ${id} missing`);
  return { email: user.email, password: user.password };
};

export const TEST_USERS: Record<UserKey, { email: string; password: string }> = {
  member: byId("u-member"),
  admin: byId("u-admin"),
};
