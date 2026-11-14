import rule from "../no-hardcoded-colors.mjs";
import { createRuleTester } from "./ruleTester.mjs";

const options = [{ tokens: ["canvas", "surface", "fg", "fg-muted", "brand", "border", "danger"] }];
const error = { messageId: "hardcoded" };

createRuleTester().run("no-hardcoded-colors", rule, {
  valid: [
    { code: `<div className="bg-surface text-fg border-border hover:bg-brand" />`, options },
    { code: `<div className={cn("text-fg-muted", isOn && "bg-danger/50")} />`, options },
    { code: `<p className="text-sm text-left font-semibold ring-2" />`, options },
    { code: `<a href="#main">x</a>`, options },
    // A string that is nothing but a hex value ("#abc") is reported even outside styles, so anchors stay non-hex.
    { code: `const ref = "#section-2"; const top = "#top";`, options },
    { code: `<div style={{ width: "100%", gridTemplateColumns: "1fr 2fr" }} />`, options },
    { code: `import x from "#internal/module";`, options },
    { code: `<div className="bg-[url(/hero.png)] w-[calc(100%-2rem)]" />`, options },
  ],
  invalid: [
    { code: `<div className="bg-[#fff] p-4" />`, options, errors: [error] },
    { code: `<div className="hover:text-[rgb(0_0_0)]" />`, options, errors: [error] },
    { code: `<div className="border-[var(--brand)]" />`, options, errors: [error] },
    { code: `<div className="bg-gray-800 text-sm" />`, options, errors: [error] },
    { code: `<div className="bg-gray-800 text-[#111] p-2" />`, options, errors: [error, error] },
    { code: `<div className="dark:text-white" />`, options, errors: [error] },
    { code: `<div labelClassName="text-red-500/50" />`, options, errors: [error] },
    { code: "const c = cva(`bg-blue-600 ${x}`);", options, errors: [error] },
    { code: `<div className={cn("p-2", "bg-slate-50")} />`, options, errors: [error] },
    { code: `<div style={{ color: "#ff0000" }} />`, options, errors: [error] },
    { code: `<div style={{ border: "1px solid rgba(0,0,0,0.1)" }} />`, options, errors: [error] },
    { code: `const theme = { backgroundColor: "#123456" };`, options, errors: [error] },
    { code: `const brand = "rgb(10, 20, 30)";`, options, errors: [error] },
    { code: `<svg><path fill="#000" /></svg>`, options, errors: [error] },
    {
      code: `<div className="bg-[#fff]" />`,
      options,
      errors: [
        {
          message:
            "Hardcoded colour 'bg-[#fff]'. Use a semantic token such as 'bg-surface' instead; available tokens: canvas, surface, fg, fg-muted, brand, border, danger (src/styles/globals.css).",
        },
      ],
    },
  ],
});

const designOptions = [
  {
    tokens: ["surface"],
    designTokens: {
      z: ["dropdown", "dialog"],
      duration: ["fast", "normal"],
      ease: ["standard"],
      animate: ["fade-in"],
      font: ["sans", "cjk", "mono"],
    },
  },
];
const designError = { messageId: "designValue" };

createRuleTester().run("no-hardcoded-colors (design tokens)", rule, {
  valid: [
    {
      code: `<div className="z-dialog duration-fast ease-standard animate-fade-in font-mono font-semibold" />`,
      options: designOptions,
    },
    // A numeric arbitrary font value is a weight, not a family; spacing arbitraries are not tokens.
    { code: `<div className="font-[550] w-[calc(100%-2rem)] max-h-[85dvh]" />`, options: designOptions },
    {
      code: `<div className="data-[state=open]:animate-fade-in hover:z-dropdown" />`,
      options: designOptions,
    },
  ],
  invalid: [
    { code: `<div className="z-[999]" />`, options: designOptions, errors: [designError] },
    { code: `<div className="duration-[350ms]" />`, options: designOptions, errors: [designError] },
    { code: `<div className="font-['Arial']" />`, options: designOptions, errors: [designError] },
    {
      code: `<div className="ease-[cubic-bezier(0.4,0,0.2,1)]" />`,
      options: designOptions,
      errors: [designError],
    },
    {
      code: `<div className={cn("p-2", "z-50", "delay-150")} />`,
      options: designOptions,
      errors: [designError, designError],
    },
    {
      code: `<div className="md:z-[999]" />`,
      options: designOptions,
      errors: [
        {
          message:
            "'md:z-[999]' bypasses the design tokens. Use one of: z-dropdown, z-dialog (src/styles/globals.css).",
        },
      ],
    },
    {
      code: `<div className="duration-300" />`,
      options: designOptions,
      errors: [
        {
          message:
            "'duration-300' bypasses the design tokens. Use one of: duration-fast, duration-normal (src/styles/globals.css).",
        },
      ],
    },
  ],
});
