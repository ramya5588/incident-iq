import { render as rtlRender, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import SignInPage from './SignInPage'
import { getPasswordResetAvailability, signIn } from '../services/incidentApi'

vi.mock('../services/incidentApi', async (importOriginal) => ({
  ...(await importOriginal()),
  signIn: vi.fn(),
  getPasswordResetAvailability: vi.fn(),
}))

function render(ui) {
  return rtlRender(<MemoryRouter>{ui}</MemoryRouter>)
}

describe('SignInPage', () => {
  beforeEach(() => {
    signIn.mockReset()
    getPasswordResetAvailability.mockReset().mockResolvedValue(false)
  })

  async function submit(email, password) {
    const user = userEvent.setup()
    await user.type(screen.getByLabelText('Email'), email)
    await user.type(screen.getByLabelText('Password'), password)
    await user.click(screen.getByRole('button', { name: 'Sign in' }))
  }

  it('passes the signed-in user up on success', async () => {
    const onSignedIn = vi.fn()
    const account = { id: 'u1', displayName: 'Ada' }
    signIn.mockResolvedValue(account)
    render(<SignInPage onSignedIn={onSignedIn} />)

    await submit('ada@incidentiq.test', 'correct-horse')

    expect(signIn).toHaveBeenCalledWith('ada@incidentiq.test', 'correct-horse')
    expect(onSignedIn).toHaveBeenCalledWith(account)
  })

  it('shows the server message and clears the password on failure', async () => {
    const onSignedIn = vi.fn()
    signIn.mockRejectedValue({
      response: { status: 401, data: { message: 'Incorrect email or password.' } },
    })
    render(<SignInPage onSignedIn={onSignedIn} />)

    await submit('ada@incidentiq.test', 'wrong-password')

    expect(await screen.findByRole('alert')).toHaveTextContent('Incorrect email or password.')
    expect(screen.getByLabelText('Password')).toHaveValue('')
    expect(screen.getByLabelText('Email')).toHaveValue('ada@incidentiq.test')
    expect(onSignedIn).not.toHaveBeenCalled()
  })

  it('explains a sign-in lockout', async () => {
    signIn.mockRejectedValue({
      response: {
        status: 429,
        data: { message: 'Too many failed sign-ins. Try again in 15 minutes, or reset your password with "Forgot password?".' },
      },
    })
    render(<SignInPage onSignedIn={vi.fn()} />)

    await submit('ada@incidentiq.test', 'wrong-password')

    expect(await screen.findByRole('alert')).toHaveTextContent('Try again in 15 minutes')
  })

  it('explains when the server cannot be reached', async () => {
    signIn.mockRejectedValue(new Error('Network Error'))
    render(<SignInPage onSignedIn={vi.fn()} />)

    await submit('ada@incidentiq.test', 'anything-123')

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Unable to reach the server. Is the backend running?'
    )
  })

  it('offers "Forgot password?" only when the server can email', async () => {
    getPasswordResetAvailability.mockResolvedValue(true)
    const { unmount } = render(<SignInPage onSignedIn={vi.fn()} />)
    expect(await screen.findByRole('link', { name: 'Forgot password?' })).toHaveAttribute(
      'href',
      '/forgot-password'
    )
    unmount()

    getPasswordResetAvailability.mockResolvedValue(false)
    render(<SignInPage onSignedIn={vi.fn()} />)
    await Promise.resolve()
    expect(screen.queryByRole('link', { name: 'Forgot password?' })).not.toBeInTheDocument()
  })
})
