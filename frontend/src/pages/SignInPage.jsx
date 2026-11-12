import { useState } from 'react'
import { describeApiError, signIn } from '../services/incidentApi'

function SignInPage({ onSignedIn }) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')

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
    <main className="signin-page">
      <section className="signin-card" aria-labelledby="signin-title">
        <div className="brand signin-brand">
          <span className="brand-mark">IQ</span>
          <div>
            <strong>IncidentIQ</strong>
            <small>Intelligent Operations</small>
          </div>
        </div>

        <h1 id="signin-title">Sign in</h1>
        <p className="signin-subtitle">Use your IncidentIQ account.</p>

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
      </section>
    </main>
  )
}

export default SignInPage
