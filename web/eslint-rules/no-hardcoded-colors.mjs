import { BASELINE_OPTION_SCHEMA, createBaselineCheck } from "./git-baseline.mjs";

/**
 * local/no-hardcoded-colors: colours come from semantic tokens only (src/styles/tokens.css,
 * exposed as Tailwind colours in src/styles/globals.css). Hardcoded colours break dark mode and
 * re-branding, because they do not follow the token switch. Reports:
 *
 *  - hex / rgb() / hsl() / oklch() values in className strings, cn()/cva()/clsx() arguments,
 *    style objects, and colour-ish props (`color`, `fill`, `backgroundColor`...);
 *  - Tailwind arbitrary colour values: `bg-[#fff]`, `text-[rgb(0_0_0)]`, `border-[var(--x)]`;
 *  - Tailwind default palette classes: `bg-gray-800`, `text-white` (the palette is also removed
 *    from the theme, so these would silently render nothing).
 *
 * It also reports values that bypass the other design tokens (motion, layering, fonts):
 *
 *  - arbitrary values `z-[999]`, `duration-[350ms]`, `delay-[1s]`, `ease-[cubic-bezier(...)]`,
 *    `animate-[...]`, `font-['Arial']`;
 *  - raw scale numbers `z-50`, `duration-300`, `delay-150`, which skip the named scale.
 *
 * The messages list the token names, read from globals.css by eslint.config.mjs.
 */

const CLASS_ATTRIBUTE = /[cC]lassName$/;
const CLASS_FUNCTIONS = new Set(["cn", "clsx", "cva", "cx", "twMerge"]);
const COLOR_PROPERTY = /(^|[a-z])(color|background|fill|stroke|border|outline|shadow)/i;
const COLOR_ATTRIBUTES = new Set(["color", "fill", "stroke", "stopColor", "floodColor", "lightingColor"]);

const HEX = /(^|[^\w&])#(?:[0-9a-f]{8}|[0-9a-f]{6}|[0-9a-f]{3,4})\b/i;
const COLOR_FUNCTION = /\b(?:rgba?|hsla?|oklch|oklab|lab|lch|hwb|color)\(/i;
const WHOLE_HEX = /^\s*#(?:[0-9a-f]{8}|[0-9a-f]{6}|[0-9a-f]{3,4})\s*$/i;

const COLOR_UTILITIES =
  "bg|text|border|border-[trblxyse]|ring|ring-offset|outline|fill|stroke|from|via|to|divide|placeholder|caret|accent|decoration|shadow|inset-shadow";
const PALETTE =
  "slate|gray|zinc|neutral|stone|red|orange|amber|yellow|lime|green|emerald|teal|cyan|sky|blue|indigo|violet|purple|fuchsia|pink|rose";
const PALETTE_CLASS = new RegExp(
  `^-?(${COLOR_UTILITIES})-(?:(?:${PALETTE})-(?:50|[1-9]00|950)|white|black)(?:/\\d+)?$`,
);
const ARBITRARY_COLOR = new RegExp(
  `^-?(${COLOR_UTILITIES})-\\[(?:#|(?:rgba?|hsla?|oklch|oklab|color|var)[(:])`,
  "i",
);

/** Utility families that have design tokens; `font-[` only when it names a family, not a weight. */
const ARBITRARY_DESIGN = /^-?(?:(z|duration|delay|ease|animate)-\[|(font)-\[(?!\d))/;
const RAW_SCALE = /^-?(z|duration|delay)-\d+$/;
/** Which token family answers each utility (delay reuses the duration scale). */
const DESIGN_FAMILY = {
  z: "z",
  duration: "duration",
  delay: "duration",
  ease: "ease",
  animate: "animate",
  font: "font",
};

/** The usual token for each utility family, used to make the message concrete. */
const PREFERRED_TOKEN = {
  bg: "surface",
  text: "fg",
  border: "border",
  ring: "ring",
  outline: "ring",
  divide: "border",
};

/** `hover:md:!bg-red-500` -> `bg-red-500`. Arbitrary values may contain ':' so split outside brackets. */
function baseUtility(token) {
  let depth = 0;
  let start = 0;
  for (let index = 0; index < token.length; index += 1) {
    const char = token[index];
    if (char === "[") depth += 1;
    else if (char === "]") depth -= 1;
    else if (char === ":" && depth === 0) start = index + 1;
  }
  return token.slice(start).replace(/^!/, "");
}

/** Every offending class in the string, so one lint run shows them all. */
/** @returns {{ value: string, prefix: string | null }[]} */
function findInClassString(text) {
  const found = [];
  for (const token of text.split(/\s+/)) {
    if (!token) continue;
    const utility = baseUtility(token);
    const match = PALETTE_CLASS.exec(utility) ?? ARBITRARY_COLOR.exec(utility);
    if (match) {
      found.push({ value: token, prefix: match[1] });
      continue;
    }
    const design = ARBITRARY_DESIGN.exec(utility) ?? RAW_SCALE.exec(utility);
    if (design) {
      const family = design[1] ?? design[2];
      found.push({ value: token, prefix: family, design: DESIGN_FAMILY[family] });
    }
  }
  if (found.length === 0 && (HEX.test(text) || COLOR_FUNCTION.test(text))) {
    found.push({ value: text.trim(), prefix: null });
  }
  return found;
}

function findInStyleValue(text) {
  return HEX.test(text) || COLOR_FUNCTION.test(text) ? [{ value: text.trim(), prefix: null }] : [];
}

function propertyName(node) {
  if (!node || node.type !== "Property" || node.computed) return null;
  if (node.key.type === "Identifier") return node.key.name;
  if (node.key.type === "Literal" && typeof node.key.value === "string") return node.key.value;
  return null;
}

function jsxAttributeName(node) {
  return node.type === "JSXAttribute" && node.name.type === "JSXIdentifier" ? node.name.name : null;
}

function calleeName(node) {
  if (node.type !== "CallExpression") return null;
  if (node.callee.type === "Identifier") return node.callee.name;
  return null;
}

/** Where a string sits decides how it is checked: as classes, as a style value, or not at all. */
function contextOf(ancestors, node) {
  const parent = ancestors[ancestors.length - 1];
  for (let index = ancestors.length - 1; index >= 0; index -= 1) {
    const ancestor = ancestors[index];
    const attribute = jsxAttributeName(ancestor);
    if (attribute !== null) {
      if (CLASS_ATTRIBUTE.test(attribute)) return "class";
      if (attribute === "style") return "style";
      if (COLOR_ATTRIBUTES.has(attribute)) return "style";
      return null;
    }
    if (CLASS_FUNCTIONS.has(calleeName(ancestor) ?? "")) return "class";
    if (ancestor.type === "ImportDeclaration" || ancestor.type === "ExportNamedDeclaration") return null;
  }
  // Outside JSX: an object property named like a colour (`backgroundColor: "#fff"`), or a whole
  // string that is nothing but a colour.
  const name = propertyName(parent);
  if (name && parent.value === node && COLOR_PROPERTY.test(name)) return "style";
  if (
    typeof node.value === "string" &&
    (WHOLE_HEX.test(node.value) || /^\s*(?:rgba?|hsla?|oklch)\(/i.test(node.value))
  ) {
    return "style";
  }
  return null;
}

export default {
  meta: {
    type: "problem",
    docs: { description: "Disallow hardcoded colours; use semantic design tokens" },
    schema: [
      {
        type: "object",
        properties: {
          /** Token names available as Tailwind colours (canvas, surface, fg, ...). */
          tokens: { type: "array", items: { type: "string" } },
          /** Non-colour token names per family: { z: ["base", ...], duration: [...], ease, animate, font }. */
          designTokens: {
            type: "object",
            additionalProperties: { type: "array", items: { type: "string" } },
          },
          baseline: BASELINE_OPTION_SCHEMA,
        },
        additionalProperties: false,
      },
    ],
    messages: {
      hardcoded:
        "Hardcoded colour '{{value}}'. Use a semantic token{{example}} instead; available tokens: {{tokens}} (src/styles/globals.css).",
      designValue: "'{{value}}' bypasses the design tokens. Use one of: {{tokens}} (src/styles/globals.css).",
    },
  },
  create(context) {
    const options = context.options[0] ?? {};
    if (createBaselineCheck(options.baseline, context.cwd)(context.filename)) return {};
    const tokens = options.tokens ?? [];
    const tokenList = tokens.length > 0 ? tokens.join(", ") : "see tokens.css";

    const designTokens = options.designTokens ?? {};

    function report(node, found) {
      if (found.design) {
        const prefix = found.prefix === "delay" ? "delay" : found.design;
        const names = designTokens[found.design] ?? [];
        context.report({
          node,
          messageId: "designValue",
          data: {
            value: found.value,
            tokens:
              names.length > 0 ? names.map((name) => `${prefix}-${name}`).join(", ") : "see globals.css",
          },
        });
        return;
      }
      const family = found.prefix?.startsWith("border") ? "border" : found.prefix;
      const suggested = family ? (PREFERRED_TOKEN[family] ?? tokens[0]) : undefined;
      const example = family && suggested ? ` such as '${found.prefix}-${suggested}'` : "";
      context.report({
        node,
        messageId: "hardcoded",
        data: { value: found.value, example, tokens: tokenList },
      });
    }

    function check(node, text) {
      const where = contextOf(context.sourceCode.getAncestors(node), node);
      if (where === null) return;
      const found = where === "class" ? findInClassString(text) : findInStyleValue(text);
      for (const item of found) report(node, item);
    }

    return {
      Literal(node) {
        if (typeof node.value === "string") check(node, node.value);
      },
      TemplateElement(node) {
        check(node, node.value.cooked ?? node.value.raw);
      },
    };
  },
};
