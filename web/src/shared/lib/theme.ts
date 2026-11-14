/** Colour theme: follows the OS until the viewer picks one, then remembers the choice. */

export const THEMES = { LIGHT: "light", DARK: "dark" } as const;
export type Theme = (typeof THEMES)[keyof typeof THEMES];

export const THEME_STORAGE_KEY = "trellis.theme";
/** Class on <html> that switches the token set (see styles/tokens.css). */
export const DARK_CLASS = "dark";
const DARK_MEDIA_QUERY = "(prefers-color-scheme: dark)";

/**
 * Runs inline in <head> before first paint; an effect would run after paint and flash the
 * wrong theme. Kept as a string because it must not wait for any bundle to load.
 */
export const THEME_INIT_SCRIPT = `(function(){try{var t=localStorage.getItem(${JSON.stringify(
  THEME_STORAGE_KEY,
)});var d=t?t===${JSON.stringify(THEMES.DARK)}:matchMedia(${JSON.stringify(
  DARK_MEDIA_QUERY,
)}).matches;document.documentElement.classList.toggle(${JSON.stringify(DARK_CLASS)},d);}catch(e){}})();`;

export function currentTheme(): Theme {
  return document.documentElement.classList.contains(DARK_CLASS) ? THEMES.DARK : THEMES.LIGHT;
}

export function applyTheme(theme: Theme): void {
  document.documentElement.classList.toggle(DARK_CLASS, theme === THEMES.DARK);
  try {
    localStorage.setItem(THEME_STORAGE_KEY, theme);
  } catch {
    // Storage blocked: the choice lasts for this page only.
  }
}
