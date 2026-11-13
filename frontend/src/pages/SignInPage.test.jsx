import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import SignInPage from './SignInPage'
import { signIn } from '../services/incidentApi'

vi.mock('../services/incidentApi', async (importOriginal) => ({
  ...(await importOriginal()),
  signIn: vi.fn(),
}))

describe('SignInPage', () => {
  beforeEach(() => {
    signIn.mockReset()
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

  it('explains when the server cannot be reached', async () => {
    signIn.mockRejectedValue(new Error('Network Error'))
    render(<SignInPage onSignedIn={vi.fn()} />)

    await submit('ada@incidentiq.test', 'anything-123')

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Unable to reach the server. Is the backend running?'
    )
  })
})
