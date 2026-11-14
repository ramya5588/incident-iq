import { useState } from 'react'
import { Link } from 'react-router-dom'
import AuthCard from '../components/AuthCard'
import { describeApiError, requestPasswordReset } from '../services/incidentApi'

/** "Forgot password?": asks for an email and sends a reset link to it. */
function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const [sentMessage, setSentMessage] = useState('')

  async function handleSubmit(event) {
    event.preventDefault()
    setSubmitting(true)
    setError('')

    try {
      setSentMessage(await requestPasswordReset(email))
    } catch (requestError) {
      console.error(requestError)
      setError(
        requestError.response
          ? describeApiError(requestError, 'Unable to send a reset link.')
          : 'Unable to reach the server. Is the backend running?'
      )
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthCard
      title="Reset your password"
      subtitle="We'll email you a link to choose a new one."
    >
      {sentMessage ? (
        <div className="auth-done" role="status">
          <p>{sentMessage}</p>
          <p className="auth-hint">
            The link expires in 30 minutes. If nothing arrives, check your spam
            folder or ask an admin.
          </p>
        </div>
      ) : (
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

          {error && (
            <p className="error-message" role="alert">
              {error}
            </p>
          )}

          <button type="submit" className="primary-button" disabled={submitting}>
            {submitting ? 'Sending...' : 'Send reset link'}
          </button>
        </form>
      )}

      <p className="auth-footer">
        <Link to="/">Back to sign in</Link>
      </p>
    </AuthCard>
  )
}

export default ForgotPasswordPage
