import rule from "../ui-boundary.mjs";
import { CWD, createRuleTester } from "./ruleTester.mjs";

const inUi = `${CWD}/src/ui/Button.tsx`;
const error = { messageId: "boundary" };

createRuleTester().run("ui-boundary", rule, {
  valid: [
    { code: `import { cn } from "./cn";`, filename: inUi },
    { code: `import { Slot } from "@radix-ui/react-slot";`, filename: inUi },
    { code: `import { formatDate } from "@/shared/lib/format";`, filename: inUi },
    { code: `import { get } from "@/shared/api/http";`, filename: `${CWD}/src/entities/a/a.api.ts` },
    { code: `import { useMe } from "@/entities/auth/auth.queries";`, filename: `${CWD}/src/features/x.tsx` },
  ],
  invalid: [
    { code: `import { useMe } from "@/entities/auth/auth.queries";`, filename: inUi, errors: [error] },
    { code: `import { get } from "@/shared/api/http";`, filename: inUi, errors: [error] },
    { code: `import { LoginForm } from "../features/auth/LoginForm";`, filename: inUi, errors: [error] },
    { code: `export * from "@/app/[locale]/layout";`, filename: `${CWD}/src/ui/deep/X.tsx`, errors: [error] },
    { code: `const m = import("@/mocks/browser");`, filename: inUi, errors: [error] },
  ],
});
