import { useEffect, useState } from 'react'
import {
  cancelEmailChange,
  describeApiError,
  getPendingEmailChange,
  requestEmailChange,
} from '../services/incidentApi'
import { formatDeadline } from '../utils/sla'

const EMPTY_FORM = { newEmail: '', currentPassword: '' }

/**
 * Change the email you sign in with. With email set up the new address must
 * be confirmed from a link sent to it; otherwise it applies straight away.
 */
function ChangeEmail({ currentEmail, onChanged }) {
  const [pending, setPending] = useState(null)
  const [form, setForm] = useState(EMPTY_FORM)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')

  useEffect(() => {
    let cancelled = false
    getPendingEmailChange()
      .then((data) => !cancelled && setPending(data))
      .catch((requestError) => console.error(requestError))
    return () => {
      cancelled = true
    }
  }, [])

  function updateField(field, value) {
    setForm((current) => ({ ...current, [field]: value }))
    setMessage('')
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setSaving(true)
    setError('')
    setMessage('')

    try {
      const result = await requestEmailChange(form.newEmail, form.currentPassword)
      setForm(EMPTY_FORM)
      if (result.pending) {
        setPending(result.pending)
        setMessage(`Check ${result.pending.newEmail} for a link to confirm the change.`)
      } else {
        setPending(null)
        onChanged(result.user)
        setMessage(`You now sign in with ${result.user.email}.`)
      }
    } catch (requestError) {
      console.error(requestError)
      setError(describeApiError(requestError, 'Unable to change your email.'))
    } finally {
      setSaving(false)
    }
  }

  async function handleCancel() {
    setSaving(true)
    setError('')
    try {
      await cancelEmailChange()
      setPending(null)
      setMessage('Email change cancelled.')
    } catch (requestError) {
      console.error(requestError)
      setError(describeApiError(requestError, 'Unable to cancel the change.'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="content-card account-card">
      <div className="section-heading">
        <div>
          <h2>Sign-in email</h2>
          <p>
            You sign in with <strong>{currentEmail}</strong>. Notifications go
            there too.
          </p>
        </div>
      </div>

      {pending && (
        <div className="pending-change" role="status">
          <p>
            Waiting for you to confirm <strong>{pending.newEmail}</strong>. We
            sent a link there; it expires {formatDeadline(pending.expiresAt)}.
            Until then you keep signing in with your current email.
          </p>
          <button
            type="button"
            className="secondary-button small-button"
            onClick={handleCancel}
            disabled={saving}
          >
            Cancel change
          </button>
        </div>
      )}

      <form className="edit-form password-form" onSubmit={handleSubmit}>
        <label>
          New email
          <input
            type="email"
            autoComplete="email"
            value={form.newEmail}
            onChange={(event) => updateField('newEmail', event.target.value)}
            maxLength={255}
            required
            disabled={saving}
          />
        </label>

        <label>
          Your password (to confirm it's you)
          <input
            type="password"
            autoComplete="current-password"
            value={form.currentPassword}
            onChange={(event) => updateField('currentPassword', event.target.value)}
            required
            disabled={saving}
          />
        </label>

        {error && <p className="error-message">{error}</p>}
        {message && <p className="status-message">{message}</p>}

        <div className="form-actions">
          <button type="submit" className="primary-button" disabled={saving}>
            {saving ? 'Saving...' : 'Change email'}
          </button>
        </div>
      </form>
    </section>
  )
}

export default ChangeEmail
