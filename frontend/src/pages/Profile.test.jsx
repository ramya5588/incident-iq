import { StrictMode } from 'react'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AccountPage from './AccountPage'
import ConfirmEmailPage from './ConfirmEmailPage'
import ChangeEmail from '../components/ChangeEmail'
import {
  cancelEmailChange,
  confirmEmailChange,
  getPendingEmailChange,
  requestEmailChange,
  updateOwnName,
} from '../services/incidentApi'
import { USERS, renderPage } from '../test/render'

vi.mock('../services/incidentApi', async (importOriginal) => ({
  ...(await importOriginal()),
  updateOwnName: vi.fn(),
  requestEmailChange: vi.fn(),
  getPendingEmailChange: vi.fn(),
  cancelEmailChange: vi.fn(),
  confirmEmailChange: vi.fn(),
  getEmailPreferences: vi.fn().mockResolvedValue({
    assigned: true,
    commented: false,
    statusChanged: false,
    slaBreached: true,
    emailEnabled: true,
  }),
  getUnreadNotificationCount: vi.fn().mockResolvedValue(0),
}))

const PENDING = { newEmail: 'riley.new@incidentiq.test', expiresAt: '2026-01-06T09:00:00Z' }

beforeEach(() => {
  updateOwnName.mockReset()
  requestEmailChange.mockReset()
  getPendingEmailChange.mockReset().mockResolvedValue(null)
  cancelEmailChange.mockReset()
  confirmEmailChange.mockReset()
  vi.spyOn(console, 'error').mockImplementation(() => {})
})

describe('Account page: your name', () => {
  it('saves a new name', async () => {
    updateOwnName.mockResolvedValue({ ...USERS.user, displayName: 'Riley R.' })
    const user = userEvent.setup()
    renderPage(<AccountPage />, { currentUser: USERS.user })

    const page = within(screen.getByRole('main'))
    await user.click(page.getByRole('button', { name: 'Edit name' }))
    const name = page.getByLabelText('Name')
    expect(name).toHaveValue('Riley Reporter')
    await user.clear(name)
    await user.type(name, 'Riley R.')
    await user.click(page.getByRole('button', { name: 'Save name' }))

    expect(updateOwnName).toHaveBeenCalledWith('Riley R.')
    expect(await page.findByText('Name saved.')).toBeInTheDocument()
    expect(page.getByText('Only an admin can change your role.')).toBeInTheDocument()
  })
})

describe('ChangeEmail', () => {
  async function submit(user, email, password = 'my-password') {
    await user.type(screen.getByLabelText('New email'), email)
    await user.type(screen.getByLabelText(/Your password/), password)
    await user.click(screen.getByRole('button', { name: 'Change email' }))
  }

  it('waits for confirmation when the server can email', async () => {
    requestEmailChange.mockResolvedValue({ user: USERS.user, pending: PENDING })
    const onChanged = vi.fn()
    const user = userEvent.setup()
    render(<ChangeEmail currentEmail="riley@incidentiq.test" onChanged={onChanged} />)

    await submit(user, 'riley.new@incidentiq.test')

    expect(requestEmailChange).toHaveBeenCalledWith('riley.new@incidentiq.test', 'my-password')
    expect(
      await screen.findByText('Check riley.new@incidentiq.test for a link to confirm the change.')
    ).toBeInTheDocument()
    expect(screen.getByText(/Waiting for you to confirm/)).toBeInTheDocument()
    expect(onChanged).not.toHaveBeenCalled()
    expect(screen.getByLabelText(/Your password/)).toHaveValue('')
  })

  it('applies straight away when the server has no email', async () => {
    const changed = { ...USERS.user, email: 'riley.new@incidentiq.test' }
    requestEmailChange.mockResolvedValue({ user: changed, pending: null })
    const onChanged = vi.fn()
    const user = userEvent.setup()
    render(<ChangeEmail currentEmail="riley@incidentiq.test" onChanged={onChanged} />)

    await submit(user, 'riley.new@incidentiq.test')

    expect(await screen.findByText('You now sign in with riley.new@incidentiq.test.')).toBeInTheDocument()
    expect(onChanged).toHaveBeenCalledWith(changed)
  })

  it('shows a waiting change and can cancel it', async () => {
    getPendingEmailChange.mockResolvedValue(PENDING)
    cancelEmailChange.mockResolvedValue()
    const user = userEvent.setup()
    render(<ChangeEmail currentEmail="riley@incidentiq.test" onChanged={vi.fn()} />)

    await user.click(await screen.findByRole('button', { name: 'Cancel change' }))

    expect(cancelEmailChange).toHaveBeenCalled()
    expect(await screen.findByText('Email change cancelled.')).toBeInTheDocument()
    expect(screen.queryByText(/Waiting for you to confirm/)).not.toBeInTheDocument()
  })

  it('shows why a change was refused', async () => {
    requestEmailChange.mockRejectedValue(
      Object.assign(new Error('Request failed'), {
        response: { status: 400, data: { message: 'Your current password is incorrect.' } },
      })
    )
    const user = userEvent.setup()
    render(<ChangeEmail currentEmail="riley@incidentiq.test" onChanged={vi.fn()} />)

    await submit(user, 'riley.new@incidentiq.test', 'wrong')

    expect(await screen.findByText('Your current password is incorrect.')).toBeInTheDocument()
  })
})

describe('ConfirmEmailPage', () => {
  function renderAt(route, onConfirmed) {
    return render(
      <StrictMode>
        <MemoryRouter initialEntries={[route]}>
          <ConfirmEmailPage onConfirmed={onConfirmed} />
        </MemoryRouter>
      </StrictMode>
    )
  }

  it('confirms once, even when React runs the effect twice', async () => {
    confirmEmailChange.mockResolvedValue('riley.new@incidentiq.test')
    const onConfirmed = vi.fn()
    renderAt('/confirm-email#token=abc_DEF-123', onConfirmed)

    expect(await screen.findByText(/You now sign in with/)).toHaveTextContent(
      'riley.new@incidentiq.test'
    )
    expect(confirmEmailChange).toHaveBeenCalledTimes(1)
    expect(confirmEmailChange).toHaveBeenCalledWith('abc_DEF-123')
    expect(onConfirmed).toHaveBeenCalled()
  })

  it('explains an invalid or expired link', async () => {
    confirmEmailChange.mockRejectedValue(
      Object.assign(new Error('Request failed'), { response: { status: 400, data: {} } })
    )
    renderAt('/confirm-email#token=used', vi.fn())

    expect(await screen.findByRole('alert')).toHaveTextContent(/invalid or has expired/)
  })

  it('treats a link without a token as invalid', async () => {
    renderAt('/confirm-email', vi.fn())

    expect(await screen.findByRole('alert')).toHaveTextContent(/invalid or has expired/)
    expect(confirmEmailChange).not.toHaveBeenCalled()
  })
})
