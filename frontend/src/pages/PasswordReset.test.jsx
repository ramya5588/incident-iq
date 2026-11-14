import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ForgotPasswordPage from './ForgotPasswordPage'
import ResetPasswordPage from './ResetPasswordPage'
import {
  checkPasswordResetToken,
  confirmPasswordReset,
  requestPasswordReset,
} from '../services/incidentApi'

vi.mock('../services/incidentApi', async (importOriginal) => ({
  ...(await importOriginal()),
  requestPasswordReset: vi.fn(),
  checkPasswordResetToken: vi.fn(),
  confirmPasswordReset: vi.fn(),
}))

const SENT = "If an account with that email exists, we've sent it a link to reset the password."
const TOKEN = 'abc_DEF-123'

function renderAt(ui, route) {
  return render(<MemoryRouter initialEntries={[route]}>{ui}</MemoryRouter>)
}

function badRequest(message) {
  return Object.assign(new Error('Request failed'), {
    response: { status: 400, data: { message } },
  })
}

beforeEach(() => {
  requestPasswordReset.mockReset()
  checkPasswordResetToken.mockReset()
  confirmPasswordReset.mockReset()
  vi.spyOn(console, 'error').mockImplementation(() => {})
})

describe('ForgotPasswordPage', () => {
  it('sends a link and shows the same answer whoever asks', async () => {
    requestPasswordReset.mockResolvedValue(SENT)
    const user = userEvent.setup()
    renderAt(<ForgotPasswordPage />, '/forgot-password')

    await user.type(screen.getByLabelText('Email'), 'riley@incidentiq.test')
    await user.click(screen.getByRole('button', { name: 'Send reset link' }))

    expect(requestPasswordReset).toHaveBeenCalledWith('riley@incidentiq.test')
    expect(await screen.findByText(SENT)).toBeInTheDocument()
    expect(screen.queryByLabelText('Email')).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Back to sign in' })).toHaveAttribute('href', '/')
  })
})

describe('ResetPasswordPage', () => {
  it('sets a new password from a valid link', async () => {
    checkPasswordResetToken.mockResolvedValue()
    confirmPasswordReset.mockResolvedValue()
    const onReset = vi.fn()
    const user = userEvent.setup()
    renderAt(<ResetPasswordPage onReset={onReset} />, `/reset-password#token=${TOKEN}`)

    const password = await screen.findByLabelText('New password')
    expect(checkPasswordResetToken).toHaveBeenCalledWith(TOKEN)

    await user.type(password, 'brand-new-password')
    await user.type(screen.getByLabelText('Confirm new password'), 'brand-new-passwor')
    expect(screen.getByText("The passwords don't match.")).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Set new password' })).toBeDisabled()

    await user.type(screen.getByLabelText('Confirm new password'), 'd')
    await user.click(screen.getByRole('button', { name: 'Set new password' }))

    expect(confirmPasswordReset).toHaveBeenCalledWith(TOKEN, 'brand-new-password')
    expect(await screen.findByText(/Your password has been changed/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Sign in' })).toBeInTheDocument()
    expect(onReset).toHaveBeenCalled()
  })

  it('explains an expired or used link and offers a new one', async () => {
    checkPasswordResetToken.mockRejectedValue(badRequest('This reset link is invalid or has expired.'))
    renderAt(<ResetPasswordPage />, `/reset-password#token=${TOKEN}`)

    expect(await screen.findByText(/invalid or has expired/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Ask for a new link' })).toHaveAttribute(
      'href',
      '/forgot-password'
    )
    expect(screen.queryByLabelText('New password')).not.toBeInTheDocument()
  })

  it('treats a link without a token as invalid', async () => {
    renderAt(<ResetPasswordPage />, '/reset-password')

    expect(await screen.findByText(/invalid or has expired/)).toBeInTheDocument()
    expect(checkPasswordResetToken).not.toHaveBeenCalled()
  })

  it('shows why the server refused the new password', async () => {
    checkPasswordResetToken.mockResolvedValue()
    confirmPasswordReset.mockRejectedValue(
      badRequest('This reset link is invalid or has expired. Ask for a new one.')
    )
    const user = userEvent.setup()
    renderAt(<ResetPasswordPage />, `/reset-password#token=${TOKEN}`)

    await user.type(await screen.findByLabelText('New password'), 'brand-new-password')
    await user.type(screen.getByLabelText('Confirm new password'), 'brand-new-password')
    await user.click(screen.getByRole('button', { name: 'Set new password' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Ask for a new one.')
  })
})
