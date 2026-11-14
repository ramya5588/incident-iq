import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import AuthCard from '../components/AuthCard'
import {
  describeApiError,
  getPasswordResetAvailability,
  signIn,
} from '../services/incidentApi'

function SignInPage({ onSignedIn }) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const [canReset, setCanReset] = useState(false)

  // "Forgot password?" needs email; hide it when the server has none.
  useEffect(() => {
    let cancelled = false
    getPasswordResetAvailability()
      .then((available) => !cancelled && setCanReset(available))
      .catch(() => {})
    return () => {
      cancelled = true
    }
  }, [])

  async function handleSubmit(event) {
    event.preventDefault()
    setSubmitting(true)
    setError('')

    try {
      onSignedIn(await signIn(email, password))
    } catch (requestError) {
      console.error(requestError)
      setError(
        requestError.response
          ? describeApiError(requestError, 'Unable to sign in.')
          : 'Unable to reach the server. Is the backend running?'
      )
      setPassword('')
      setSubmitting(false)
    }
  }

  return (
    <AuthCard title="Sign in" subtitle="Use your IncidentIQ account.">
      <form onSubmit={handleSubmit}>
        <label>
          Email
          <input
            type="email"
            autoComplete="username"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            autoFocus
            required
            disabled={submitting}
          />
        </label>

        <label>
          Password
          <input
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            required
            disabled={submitting}
          />
        </label>

        {error && (
          <p className="error-message" role="alert">
            {error}
          </p>
        )}

        <button
          type="submit"
          className="primary-button"
          disabled={submitting}
        >
          {submitting ? 'Signing in...' : 'Sign in'}
        </button>
      </form>

      {canReset && (
        <p className="auth-footer">
          <Link to="/forgot-password">Forgot password?</Link>
        </p>
      )}
    </AuthCard>
  )
}

export default SignInPage
