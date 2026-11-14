import { useState } from 'react'
import Sidebar from '../components/Sidebar'
import EmailPreferences from '../components/EmailPreferences'
import { useCurrentUser } from '../context/currentUserContext'
import { changeOwnPassword, describeApiError } from '../services/incidentApi'
import { initials } from '../utils/initials'

const ROLE_DESCRIPTIONS = {
  ADMIN: 'Administrator: works incidents and manages the team.',
  AGENT: 'Support agent: works incidents (edit, status, assign, triage).',
  USER: 'User: reports incidents, views them and comments.',
}

const EMPTY_FORM = { current: '', next: '', confirm: '' }

function AccountPage() {
  const { currentUser } = useCurrentUser()
  const [form, setForm] = useState(EMPTY_FORM)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')

  function updateField(field, value) {
    setForm((current) => ({ ...current, [field]: value }))
    setMessage('')
  }

  const mismatch = form.confirm !== '' && form.next !== form.confirm

  async function handleSubmit(event) {
    event.preventDefault()
    if (form.next !== form.confirm) return

    setSaving(true)
    setError('')
    setMessage('')

    try {
      await changeOwnPassword(form.current, form.next)
      setForm(EMPTY_FORM)
      setMessage(
        'Password changed. Any other devices signed in to your account have been signed out.'
      )
    } catch (requestError) {
      console.error(requestError)
      setError(describeApiError(requestError, 'Unable to change your password.'))
    } finally {
      setSaving(false)
    }
  }

  if (!currentUser) return null

  return (
    <div className="app-shell">
      <Sidebar />

      <main className="main-content">
        <header className="page-header">
          <div>
            <p className="eyebrow">YOUR ACCOUNT</p>
            <h1>Account</h1>
            <p>Your details, email notifications and password.</p>
          </div>
        </header>

        <section className="content-card account-card">
          <div className="member">
            <span className="avatar member-avatar">
              {initials(currentUser.displayName)}
            </span>
            <div>
              <strong>{currentUser.displayName}</strong>
              <p className="account-email">{currentUser.email}</p>
            </div>
          </div>
          <p className="account-role">
            <span className={`role-badge ${currentUser.role.toLowerCase()}`}>
              {currentUser.role}
            </span>
            {ROLE_DESCRIPTIONS[currentUser.role]}
          </p>
          <p className="account-note">
            Only an admin can change your name, email or role.
          </p>
        </section>

        <EmailPreferences email={currentUser.email} />

        <section className="content-card account-card">
          <div className="section-heading">
            <div>
              <h2>Change password</h2>
              <p>
                You stay signed in here; other devices signed in to your account
                will be signed out.
              </p>
            </div>
          </div>

          <form className="edit-form password-form" onSubmit={handleSubmit}>
            <label>
              Current password
              <input
                type="password"
                autoComplete="current-password"
                value={form.current}
                onChange={(event) => updateField('current', event.target.value)}
                required
                disabled={saving}
              />
            </label>

            <label>
              New password
              <input
                type="password"
                autoComplete="new-password"
                value={form.next}
                onChange={(event) => updateField('next', event.target.value)}
                minLength={8}
                maxLength={72}
                placeholder="At least 8 characters"
                required
                disabled={saving}
              />
            </label>

            <label>
              Confirm new password
              <input
                type="password"
                autoComplete="new-password"
                value={form.confirm}
                onChange={(event) => updateField('confirm', event.target.value)}
                maxLength={72}
                required
                disabled={saving}
                aria-invalid={mismatch}
              />
            </label>

            {mismatch && (
              <p className="error-message">The new passwords don't match.</p>
            )}
            {error && <p className="error-message">{error}</p>}
            {message && <p className="status-message">{message}</p>}

            <div className="form-actions">
              <button
                type="submit"
                className="primary-button"
                disabled={saving || mismatch}
              >
                {saving ? 'Saving...' : 'Change password'}
              </button>
            </div>
          </form>
        </section>
      </main>
    </div>
  )
}

export default AccountPage
