import { useEffect, useRef, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import AuthCard from '../components/AuthCard'
import { confirmEmailChange, describeApiError } from '../services/incidentApi'

/** The token comes after "#" so it's never sent to a server or logged. */
function tokenFrom(hash) {
  return new URLSearchParams(hash.replace(/^#/, '')).get('token') ?? ''
}

const INVALID = 'This confirmation link is invalid or has expired. Ask for the change again from your Account page.'

function ConfirmEmail({ token, onConfirmed }) {
  const [state, setState] = useState(token ? 'confirming' : 'failed') // confirming | done | failed
  const [email, setEmail] = useState('')
  const [error, setError] = useState(token ? '' : INVALID)
  // Confirming uses the link up, so send it once even if the effect re-runs
  // (React runs effects twice in development).
  const request = useRef(null)

  useEffect(() => {
    if (!token) return
    // Keep the token out of the address bar and browser history.
    window.history.replaceState(null, '', window.location.pathname)

    let cancelled = false
    request.current ??= confirmEmailChange(token)
    request.current
      .then((newEmail) => {
        if (cancelled) return
        setEmail(newEmail)
        setState('done')
        onConfirmed?.()
      })
      .catch((requestError) => {
        console.error(requestError)
        if (cancelled) return
        setError(
          requestError.response?.status === 400
            ? INVALID
            : describeApiError(requestError, 'Unable to confirm your new email.')
        )
        setState('failed')
      })
    return () => {
      cancelled = true
    }
  }, [token, onConfirmed])

  return (
    <AuthCard title="Confirm your new email">
      {state === 'confirming' && <p className="auth-hint">Confirming…</p>}

      {state === 'done' && (
        <div className="auth-done" role="status">
          <p>
            Done. You now sign in with <strong>{email}</strong>, and
            notifications go there.
          </p>
        </div>
      )}

      {state === 'failed' && (
        <div className="auth-done" role="alert">
          <p>{error}</p>
        </div>
      )}

      <p className="auth-footer">
        <Link to="/">Go to IncidentIQ</Link>
      </p>
    </AuthCard>
  )
}

/** Opened from the emailed link. A different link starts the page over. */
function ConfirmEmailPage({ onConfirmed }) {
  const { hash } = useLocation()
  const token = tokenFrom(hash)
  return <ConfirmEmail key={token} token={token} onConfirmed={onConfirmed} />
}

export default ConfirmEmailPage
