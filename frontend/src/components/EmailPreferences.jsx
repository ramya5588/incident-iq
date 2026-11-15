import { useEffect, useState } from 'react'
import {
  describeApiError,
  getEmailPreferences,
  saveEmailPreferences,
} from '../services/incidentApi'

const OPTIONS = [
  {
    key: 'assigned',
    label: 'An incident is assigned to me',
  },
  {
    key: 'slaBreached',
    label: 'An incident misses an SLA deadline',
    hint: "Ones assigned to you or that you watch, or (for admins) ones nobody's assigned.",
  },
  {
    key: 'mentioned',
    label: 'Someone mentions me',
    hint: 'With @ in a comment or internal note.',
  },
  {
    key: 'commented',
    label: 'Someone comments',
    hint: 'On incidents you reported, are assigned or watch.',
  },
  {
    key: 'statusChanged',
    label: 'The status changes',
    hint: 'On incidents you reported or watch.',
  },
]

const KEYS = OPTIONS.map((option) => option.key)

/** Which notifications are also sent by email. In-app ones always arrive. */
function EmailPreferences({ email }) {
  const [saved, setSaved] = useState(null)
  const [form, setForm] = useState(null)
  const [emailEnabled, setEmailEnabled] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')

  useEffect(() => {
    let cancelled = false

    getEmailPreferences()
      .then((data) => {
        if (cancelled) return
        const choices = pick(data)
        setSaved(choices)
        setForm(choices)
        setEmailEnabled(data.emailEnabled)
      })
      .catch((requestError) => {
        console.error(requestError)
        if (!cancelled) setLoadError('Unable to load your email settings.')
      })

    return () => {
      cancelled = true
    }
  }, [])

  const changed = form && saved && KEYS.some((key) => form[key] !== saved[key])

  async function handleSubmit(event) {
    event.preventDefault()
    setSaving(true)
    setError('')
    setMessage('')

    try {
      const data = await saveEmailPreferences(form)
      const choices = pick(data)
      setSaved(choices)
      setForm(choices)
      setEmailEnabled(data.emailEnabled)
      setMessage('Email settings saved.')
    } catch (requestError) {
      console.error(requestError)
      setError(describeApiError(requestError, 'Unable to save your email settings.'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="content-card account-card">
      <div className="section-heading">
        <div>
          <h2>Email notifications</h2>
          <p>
            Choose what is also sent to <strong>{email}</strong>. You always
            get every notification in the app.
          </p>
        </div>
      </div>

      {loadError && <p className="error-message">{loadError}</p>}
      {!form && !loadError && <p className="account-note">Loading…</p>}

      {form && (
        <form className="email-preferences" onSubmit={handleSubmit}>
          {!emailEnabled && (
            <p className="account-note email-off">
              Email isn't set up on this server yet, so nothing is emailed.
              Your choices are kept for when it is.
            </p>
          )}

          <fieldset disabled={saving}>
            <legend>Email me when</legend>
            {OPTIONS.map((option) => (
              <label key={option.key} className="checkbox-row">
                <input
                  type="checkbox"
                  checked={form[option.key]}
                  onChange={(event) => {
                    setForm((current) => ({
                      ...current,
                      [option.key]: event.target.checked,
                    }))
                    setMessage('')
                  }}
                />
                <span>
                  {option.label}
                  {option.hint && <small>{option.hint}</small>}
                </span>
              </label>
            ))}
          </fieldset>

          {error && <p className="error-message">{error}</p>}
          {message && <p className="status-message">{message}</p>}

          <div className="form-actions">
            <button
              type="submit"
              className="primary-button"
              disabled={saving || !changed}
            >
              {saving ? 'Saving...' : 'Save email settings'}
            </button>
          </div>
        </form>
      )}
    </section>
  )
}

function pick(data) {
  return Object.fromEntries(KEYS.map((key) => [key, Boolean(data[key])]))
}

export default EmailPreferences
