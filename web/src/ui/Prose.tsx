import type { ComponentPropsWithoutRef } from "react";
import { cn } from "./cn";

export type ProseProps = ComponentPropsWithoutRef<"div">;

/**
 * Long-form text styled by @tailwindcss/typography, coloured by the semantic tokens
 * (`prose-tokens` in globals.css), so it follows light and dark without `prose-invert`.
 * `max-w-none`: the page layout decides the measure, not the plugin's 65ch.
 */
export function Prose({ className, ...props }: ProseProps) {
  return <div className={cn("prose max-w-none prose-tokens", className)} {...props} />;
}
