import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ThemeSettings from './ThemeSettings'
import { applyTheme, getThemePreference, setThemePreference } from '../utils/theme'

afterEach(() => {
  window.localStorage.clear()
  delete document.documentElement.dataset.theme
  vi.restoreAllMocks()
})

describe('theme preference', () => {
  it('follows the system until a theme is chosen', () => {
    expect(getThemePreference()).toBe('system')
    expect(setThemePreference('dark')).toBe('dark')
    expect(document.documentElement.dataset.theme).toBe('dark')
    expect(getThemePreference()).toBe('dark')

    setThemePreference('system')
    expect(document.documentElement.dataset.theme).toBeUndefined()
    expect(window.localStorage.getItem('incidentiq.theme')).toBeNull()
  })

  it('ignores unknown values and blocked storage', () => {
    window.localStorage.setItem('incidentiq.theme', 'purple')
    expect(getThemePreference()).toBe('system')

    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    expect(setThemePreference('light')).toBe('light')
    expect(document.documentElement.dataset.theme).toBe('light')

    applyTheme('nonsense')
    expect(document.documentElement.dataset.theme).toBeUndefined()
  })
})

describe('ThemeSettings', () => {
  it('switches the theme from the Account page', async () => {
    const user = userEvent.setup()
    render(<ThemeSettings />)

    expect(screen.getByRole('radio', { name: /System/ })).toBeChecked()
    await user.click(screen.getByRole('radio', { name: 'Dark' }))

    expect(document.documentElement.dataset.theme).toBe('dark')
    expect(screen.getByRole('radio', { name: 'Dark' })).toBeChecked()
    expect(window.localStorage.getItem('incidentiq.theme')).toBe('dark')
  })

  it('starts from the saved choice', () => {
    window.localStorage.setItem('incidentiq.theme', 'light')
    render(<ThemeSettings />)

    expect(screen.getByRole('radio', { name: 'Light' })).toBeChecked()
  })
})
