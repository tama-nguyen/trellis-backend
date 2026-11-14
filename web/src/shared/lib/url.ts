/** Values accepted in a query string. `undefined`, `null` and "" are dropped, arrays repeat the key. */
export type QueryValue =
  string | number | boolean | null | undefined | readonly (string | number | boolean)[];
export type QueryParams = Record<string, QueryValue>;
export type PathParams = Record<string, string | number>;

const PATH_PARAM_PREFIX = ":";

/** Fills `:name` placeholders, URL-encoding each value. Throws on a placeholder left unfilled. */
export function fillPath(pattern: string, pathParams: PathParams = {}): string {
  const filled = pattern
    .split("/")
    .map((segment) => {
      if (!segment.startsWith(PATH_PARAM_PREFIX)) return segment;
      const value = pathParams[segment.slice(PATH_PARAM_PREFIX.length)];
      if (value === undefined) throw new Error(`Missing path param "${segment}" for ${pattern}`);
      return encodeURIComponent(String(value));
    })
    .join("/");
  return filled;
}

export function toQueryString(params: QueryParams = {}): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === "") continue;
    if (Array.isArray(value)) {
      for (const item of value) search.append(key, String(item));
    } else {
      search.set(key, String(value));
    }
  }
  const query = search.toString();
  return query ? `?${query}` : "";
}

/** `base` + filled path + query string. The only way client code should assemble a URL. */
export function buildUrl(
  base: string,
  pattern: string,
  pathParams?: PathParams,
  params?: QueryParams,
): string {
  return `${base}${fillPath(pattern, pathParams)}${toQueryString(params)}`;
}

/**
 * A post-login redirect target taken from the URL, or `null` if it is not a same-site path.
 *
 * The value is attacker-controlled (anyone can send a link with `?redirect=`), so only a
 * single leading slash is accepted: `//evil.example` and `/\evil.example` are protocol-relative
 * in browsers and would make the login page an open redirect.
 */
export function safeRedirectPath(raw: string | null | undefined): string | null {
  if (!raw || !raw.startsWith("/")) return null;
  if (raw.startsWith("//") || raw.startsWith("/\\")) return null;
  return raw;
}
