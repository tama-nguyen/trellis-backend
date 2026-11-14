import { readFileSync } from "node:fs";
import js from "@eslint/js";
import nextPlugin from "@next/eslint-plugin-next";
import prettier from "eslint-config-prettier";
import i18next from "eslint-plugin-i18next";
import reactHooks from "eslint-plugin-react-hooks";
import globals from "globals";
import tseslint from "typescript-eslint";
import noHardcodedColors from "./eslint-rules/no-hardcoded-colors.mjs";
import noRawFetch from "./eslint-rules/no-raw-fetch.mjs";
import uiBoundary from "./eslint-rules/ui-boundary.mjs";

/**
 * Colour token names, read from the @theme block in globals.css so the lint message and the
 * stylesheet cannot disagree: `--color-surface-hover: var(...)` -> `surface-hover`.
 */
const COLOR_TOKENS = [
  ...readFileSync(new URL("./src/styles/globals.css", import.meta.url), "utf8").matchAll(
    /--color-([a-z0-9-]+):\s*var\(/g,
  ),
].map((match) => match[1]);

/** Motion, layering and font tokens by family, from the same file: `--z-dialog: 50;` -> z: dialog. */
const DESIGN_TOKENS = Object.fromEntries(
  ["z", "duration", "ease", "animate", "font"].map((family) => [
    family,
    [
      ...readFileSync(new URL("./src/styles/globals.css", import.meta.url), "utf8").matchAll(
        new RegExp(`^\\s*--${family}-([a-z0-9-]+):`, "gm"),
      ),
    ].map((match) => match[1]),
  ]),
);

/** Local rules: conventions turned into build errors. Each has a test in eslint-rules/__tests__. */
const local = {
  rules: {
    "no-hardcoded-colors": noHardcodedColors,
    "no-raw-fetch": noRawFetch,
    "ui-boundary": uiBoundary,
  },
};

/** JSX attributes that never hold user-visible copy (DataHub keeps a similar allow-list). */
const NON_COPY_ATTRIBUTES = [
  ".*[Cc]lassName$",
  ".*Id$",
  "^data-.*",
  "^aria-(current|hidden|live|busy|invalid|describedby|labelledby|controls|expanded|haspopup)$",
  "^(id|key|href|src|name|type|role|rel|target|method|lang|dir|htmlFor|autoComplete|inputMode|as|variant|size|side|align|position|state|icon|dateTime|value|defaultValue|testId|locale|permission|mode)$",
];

export default tseslint.config(
  {
    ignores: [
      ".next/**",
      "node_modules/**",
      "playwright-report/**",
      "test-results/**",
      "coverage/**",
      "next-env.d.ts",
      "src/**/*.generated.ts",
      "src/shared/api/generated/**",
      "src/shared/graphql/generated/**",
    ],
  },
  js.configs.recommended,
  ...tseslint.configs.strict,
  {
    languageOptions: {
      globals: { ...globals.browser, ...globals.node },
    },
    linterOptions: { reportUnusedDisableDirectives: "error" },
  },
  {
    files: ["**/*.{ts,tsx}"],
    plugins: { "react-hooks": reactHooks, "@next/next": nextPlugin, local },
    rules: {
      ...reactHooks.configs["recommended-latest"].rules,
      ...nextPlugin.configs.recommended.rules,
      ...nextPlugin.configs["core-web-vitals"].rules,

      "@typescript-eslint/no-explicit-any": "error",
      "@typescript-eslint/consistent-type-definitions": ["error", "type"],
      "@typescript-eslint/consistent-type-imports": ["error", { fixStyle: "inline-type-imports" }],
      "@typescript-eslint/no-unused-vars": [
        "error",
        { argsIgnorePattern: "^_", varsIgnorePattern: "^_", destructuredArrayIgnorePattern: "^_" },
      ],
      "no-restricted-imports": [
        "error",
        {
          patterns: [
            {
              group: ["**/index", "**/index.ts", "**/index.tsx"],
              message: "No barrel files: import the module that owns the symbol.",
            },
          ],
          paths: [
            {
              name: "next/link",
              message: "Use Link from @/i18n/navigation, which keeps the locale prefix.",
            },
            { name: "axios", message: "Use the HTTP client in @/shared/api/http." },
          ],
        },
      ],

      "local/no-hardcoded-colors": ["error", { tokens: COLOR_TOKENS, designTokens: DESIGN_TOKENS }],
      "local/no-raw-fetch": [
        "error",
        // http.ts is the client; the proxy route forwards browser requests to the backend.
        { allow: ["src/shared/api/http.ts", "src/app/api/[...path]/route.ts"] },
      ],
      "local/ui-boundary": "error",
    },
  },
  {
    // User-visible copy goes through next-intl. jsx-only checks JSX text and attribute strings.
    files: ["src/**/*.tsx"],
    ...i18next.configs["flat/recommended"],
    rules: {
      "i18next/no-literal-string": [
        "error",
        {
          mode: "jsx-only",
          "jsx-attributes": { exclude: NON_COPY_ATTRIBUTES },
          // Arguments of translator calls are message keys, not copy: t(), tAll(), tCommon()...
          callees: { exclude: ["i18n(ext)?", "t", "t[A-Z]\\w*", "require", "track"] },
        },
      ],
    },
  },
  {
    // Tooling written in plain JS: the type-aware TS rules do not apply.
    files: ["**/*.{js,mjs,cjs}"],
    rules: {
      "@typescript-eslint/no-require-imports": "off",
    },
  },
  {
    // Playwright fixtures call `use(...)`, which the React hooks rules mistake for React's `use`,
    // and a fixture with no dependencies must destructure `{}` (Playwright reads the parameter).
    files: ["e2e/**/*.ts"],
    rules: {
      "react-hooks/rules-of-hooks": "off",
      "no-empty-pattern": "off",
    },
  },
  prettier,
);
