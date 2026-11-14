import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AccountPage from './AccountPage'
import { changeOwnPassword } from '../services/incidentApi'
import { USERS, renderPage } from '../test/render'

vi.mock('../services/incidentApi', async (importOriginal) => ({
  ...(await importOriginal()),
  changeOwnPassword: vi.fn(),
  getEmailPreferences: vi.fn().mockResolvedValue({
    assigned: true,
    commented: false,
    statusChanged: false,
    slaBreached: true,
    emailEnabled: true,
  }),
  // The sidebar bell checks for notifications.
  getUnreadNotificationCount: vi.fn().mockResolvedValue(0),
}))

async function fill(current, next, confirm) {
  const user = userEvent.setup()
  await user.type(screen.getByLabelText('Current password'), current)
  await user.type(screen.getByLabelText('New password'), next)
  await user.type(screen.getByLabelText('Confirm new password'), confirm)
  return user
}

describe('AccountPage', () => {
  beforeEach(() => {
    changeOwnPassword.mockReset()
  })

  it('shows who is signed in', () => {
    renderPage(<AccountPage />, { currentUser: USERS.agent })

    // The sidebar also shows the name, so look inside the page itself.
    const page = within(screen.getByRole('main'))
    expect(page.getByText('Alex Agent')).toBeInTheDocument()
    expect(
      page.getByText('alex@incidentiq.test', { selector: '.account-email' })
    ).toBeInTheDocument()
    expect(page.getByText(/Support agent: works incidents/)).toBeInTheDocument()
  })

  it('blocks saving while the new passwords differ', async () => {
    renderPage(<AccountPage />)

    await fill('old-password', 'new-password-1', 'new-password-2')

    expect(screen.getByText("The new passwords don't match.")).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Change password' })).toBeDisabled()
    expect(changeOwnPassword).not.toHaveBeenCalled()
  })

  it('changes the password and clears the form', async () => {
    changeOwnPassword.mockResolvedValue()
    renderPage(<AccountPage />)

    const user = await fill('old-password', 'new-password-1', 'new-password-1')
    await user.click(screen.getByRole('button', { name: 'Change password' }))

    expect(changeOwnPassword).toHaveBeenCalledWith('old-password', 'new-password-1')
    expect(await screen.findByText(/Password changed\./)).toBeInTheDocument()
    expect(screen.getByLabelText('Current password')).toHaveValue('')
  })

  it('shows the server message when the current password is wrong', async () => {
    changeOwnPassword.mockRejectedValue({
      response: { status: 400, data: { message: 'Your current password is incorrect.' } },
    })
    renderPage(<AccountPage />)

    const user = await fill('bad-guess', 'new-password-1', 'new-password-1')
    await user.click(screen.getByRole('button', { name: 'Change password' }))

    expect(await screen.findByText('Your current password is incorrect.')).toBeInTheDocument()
  })
})
