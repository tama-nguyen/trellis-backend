import path from "node:path";

/**
 * local/ui-boundary: the design system (src/ui) cannot import app code. It must stay reusable
 * across products, so data and behaviour come in through props. Pattern from DataHub, where
 * alchemy-components may not import @app / @graphql.
 */
const DEFAULTS = {
  uiDir: "src/ui",
  forbidden: ["src/app", "src/features", "src/entities", "src/shared/api", "src/mocks"],
  aliases: { "@/": "src/" },
};

const toPosix = (value) => value.replace(/\\/g, "/");
const within = (target, dir) => target === dir || target.startsWith(`${dir}/`);

export default {
  meta: {
    type: "problem",
    docs: { description: "Keep src/ui free of app, feature, entity and API code" },
    schema: [
      {
        type: "object",
        properties: {
          uiDir: { type: "string" },
          forbidden: { type: "array", items: { type: "string" } },
          aliases: { type: "object", additionalProperties: { type: "string" } },
        },
        additionalProperties: false,
      },
    ],
    messages: {
      boundary:
        "src/ui must not import '{{source}}' ({{area}}). The design system cannot depend on app code; pass data and callbacks in through props.",
    },
  },
  create(context) {
    const options = { ...DEFAULTS, ...(context.options[0] ?? {}) };
    const cwd = context.cwd;
    const fileRel = toPosix(path.relative(cwd, path.resolve(context.filename)));
    if (!within(fileRel, options.uiDir)) return {};

    function resolve(source) {
      for (const [alias, target] of Object.entries(options.aliases)) {
        if (source.startsWith(alias)) return toPosix(path.posix.join(target, source.slice(alias.length)));
      }
      if (source.startsWith(".")) {
        return toPosix(
          path.relative(cwd, path.resolve(path.dirname(path.resolve(context.filename)), source)),
        );
      }
      return null;
    }

    function check(sourceNode) {
      if (!sourceNode || typeof sourceNode.value !== "string") return;
      const target = resolve(sourceNode.value);
      if (!target) return;
      const area = options.forbidden.find((dir) => within(target, dir));
      if (area)
        context.report({ node: sourceNode, messageId: "boundary", data: { source: sourceNode.value, area } });
    }

    return {
      ImportDeclaration: (node) => check(node.source),
      ExportNamedDeclaration: (node) => check(node.source),
      ExportAllDeclaration: (node) => check(node.source),
      ImportExpression: (node) => {
        if (node.source.type === "Literal") check(node.source);
      },
    };
  },
};
