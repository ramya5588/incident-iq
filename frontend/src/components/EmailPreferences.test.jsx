import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import EmailPreferences from './EmailPreferences'
import { getEmailPreferences, saveEmailPreferences } from '../services/incidentApi'

vi.mock('../services/incidentApi', async (importOriginal) => ({
  ...(await importOriginal()),
  getEmailPreferences: vi.fn(),
  saveEmailPreferences: vi.fn(),
}))

const DEFAULTS = {
  assigned: true,
  commented: false,
  statusChanged: false,
  slaBreached: true,
  emailEnabled: true,
}

describe('EmailPreferences', () => {
  beforeEach(() => {
    getEmailPreferences.mockReset().mockResolvedValue(DEFAULTS)
    saveEmailPreferences.mockReset()
  })

  it('shows the current choices and where emails go', async () => {
    render(<EmailPreferences email="alex@incidentiq.test" />)

    expect(
      await screen.findByRole('checkbox', { name: 'An incident is assigned to me' })
    ).toBeChecked()
    expect(screen.getByRole('checkbox', { name: /misses an SLA deadline/ })).toBeChecked()
    expect(screen.getByRole('checkbox', { name: /Someone comments/ })).not.toBeChecked()
    expect(screen.getByRole('checkbox', { name: /The status changes/ })).not.toBeChecked()
    expect(screen.getByText('alex@incidentiq.test')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Save email settings' })).toBeDisabled()
    expect(screen.queryByText(/Email isn't set up/)).not.toBeInTheDocument()
  })

  it('saves changed choices', async () => {
    saveEmailPreferences.mockResolvedValue({ ...DEFAULTS, commented: true })
    const user = userEvent.setup()
    render(<EmailPreferences email="alex@incidentiq.test" />)

    await user.click(await screen.findByRole('checkbox', { name: /Someone comments/ }))
    await user.click(screen.getByRole('button', { name: 'Save email settings' }))

    expect(saveEmailPreferences).toHaveBeenCalledWith({
      assigned: true,
      commented: true,
      statusChanged: false,
      slaBreached: true,
    })
    expect(await screen.findByText('Email settings saved.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Save email settings' })).toBeDisabled()
  })

  it('says when the server has no email set up', async () => {
    getEmailPreferences.mockResolvedValue({ ...DEFAULTS, emailEnabled: false })
    render(<EmailPreferences email="alex@incidentiq.test" />)

    expect(await screen.findByText(/Email isn't set up on this server yet/)).toBeInTheDocument()
    // Choices can still be saved for later.
    expect(screen.getByRole('checkbox', { name: /Someone comments/ })).toBeEnabled()
  })

  it('reports a failed load', async () => {
    getEmailPreferences.mockRejectedValue(new Error('offline'))
    vi.spyOn(console, 'error').mockImplementation(() => {})
    render(<EmailPreferences email="alex@incidentiq.test" />)

    expect(await screen.findByText('Unable to load your email settings.')).toBeInTheDocument()
  })
})
