import { useState } from 'react'
import { getThemePreference, setThemePreference } from '../utils/theme'

const OPTIONS = [
  { value: 'system', label: 'System', hint: "Match this device's setting" },
  { value: 'light', label: 'Light' },
  { value: 'dark', label: 'Dark' },
]

/** Light / Dark / System, remembered in this browser. */
function ThemeSettings() {
  const [theme, setTheme] = useState(getThemePreference)

  return (
    <section className="content-card account-card" aria-labelledby="appearance-title">
      <div className="section-heading">
        <div>
          <h2 id="appearance-title">Appearance</h2>
          <p>Choose light or dark, or follow this device. It's remembered in this browser.</p>
        </div>
      </div>
      <fieldset className="theme-options">
        <legend className="visually-hidden">Theme</legend>
        {OPTIONS.map((option) => (
          <label key={option.value} className={theme === option.value ? 'selected' : ''}>
            <input
              type="radio"
              name="theme"
              value={option.value}
              checked={theme === option.value}
              onChange={() => setTheme(setThemePreference(option.value))}
            />
            <span>
              {option.label}
              {option.hint && <small>{option.hint}</small>}
            </span>
          </label>
        ))}
      </fieldset>
    </section>
  )
}

export default ThemeSettings
