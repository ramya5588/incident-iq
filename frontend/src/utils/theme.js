// Light, dark or follow the system. The choice is kept in this browser only
// (it's a per-device preference), and applied as data-theme on <html>;
// "system" removes it so the CSS follows prefers-color-scheme.

const STORAGE_KEY = 'incidentiq.theme'
export const THEMES = ['system', 'light', 'dark']

export function getThemePreference() {
  try {
    const saved = window.localStorage.getItem(STORAGE_KEY)
    return THEMES.includes(saved) ? saved : 'system'
  } catch {
    return 'system'
  }
}

export function applyTheme(theme) {
  const root = document.documentElement
  if (theme === 'light' || theme === 'dark') root.dataset.theme = theme
  else delete root.dataset.theme
}

export function setThemePreference(theme) {
  const value = THEMES.includes(theme) ? theme : 'system'
  try {
    if (value === 'system') window.localStorage.removeItem(STORAGE_KEY)
    else window.localStorage.setItem(STORAGE_KEY, value)
  } catch {
    // Storage can be blocked (private windows); the theme still applies now.
  }
  applyTheme(value)
  return value
}
