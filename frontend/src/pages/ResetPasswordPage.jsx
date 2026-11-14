import { useEffect, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import AuthCard from '../components/AuthCard'
import {
  checkPasswordResetToken,
  confirmPasswordReset,
  describeApiError,
} from '../services/incidentApi'

/** The token comes after "#" so it's never sent to a server or logged. */
function tokenFrom(hash) {
  return new URLSearchParams(hash.replace(/^#/, '')).get('token') ?? ''
}

/** Opened from the emailed link: choose a new password. */
function ResetPasswordForm({ token, onReset }) {
  const [state, setState] = useState(token ? 'checking' : 'invalid') // checking | ready | invalid | done
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!token) return
    // Keep the token out of the address bar and browser history.
    window.history.replaceState(null, '', window.location.pathname)

    let cancelled = false
    checkPasswordResetToken(token)
      .then(() => !cancelled && setState('ready'))
      .catch((requestError) => {
        console.error(requestError)
        if (!cancelled) setState('invalid')
      })
    return () => {
      cancelled = true
    }
  }, [token])

  const mismatch = confirm !== '' && password !== confirm

  async function handleSubmit(event) {
    event.preventDefault()
    if (password !== confirm) return
    setSubmitting(true)
    setError('')

    try {
      await confirmPasswordReset(token, password)
      setState('done')
      onReset?.()
    } catch (requestError) {
      console.error(requestError)
      setError(describeApiError(requestError, 'Unable to reset your password.'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthCard title="Choose a new password">
      {state === 'checking' && <p className="auth-hint">Checking your link…</p>}

      {state === 'invalid' && (
        <div className="auth-done" role="alert">
          <p>This reset link is invalid or has expired. Links work once and last 30 minutes.</p>
          <p>
            <Link to="/forgot-password">Ask for a new link</Link>
          </p>
        </div>
      )}

      {state === 'done' && (
        <div className="auth-done" role="status">
          <p>
            Your password has been changed. Any other devices signed in to your
            account have been signed out.
          </p>
        </div>
      )}

      {state === 'ready' && (
        <form onSubmit={handleSubmit}>
          <label>
            New password
            <input
              type="password"
              autoComplete="new-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              minLength={8}
              maxLength={72}
              placeholder="At least 8 characters"
              autoFocus
              required
              disabled={submitting}
            />
          </label>

          <label>
            Confirm new password
            <input
              type="password"
              autoComplete="new-password"
              value={confirm}
              onChange={(event) => setConfirm(event.target.value)}
              maxLength={72}
              required
              disabled={submitting}
              aria-invalid={mismatch}
            />
          </label>

          {mismatch && <p className="error-message">The passwords don't match.</p>}
          {error && (
            <p className="error-message" role="alert">
              {error}
            </p>
          )}

          <button
            type="submit"
            className="primary-button"
            disabled={submitting || mismatch}
          >
            {submitting ? 'Saving...' : 'Set new password'}
          </button>
        </form>
      )}

      <p className="auth-footer">
        <Link to="/">{state === 'done' ? 'Sign in' : 'Back to sign in'}</Link>
      </p>
    </AuthCard>
  )
}

/** A different link (even in the same tab) starts the page over. */
function ResetPasswordPage({ onReset }) {
  const { hash } = useLocation()
  const token = tokenFrom(hash)
  return <ResetPasswordForm key={token} token={token} onReset={onReset} />
}

export default ResetPasswordPage
