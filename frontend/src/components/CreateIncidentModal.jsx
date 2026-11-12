import { useEffect, useState } from 'react'
import {
  createIncident,
  describeApiError,
  getUsers,
} from '../services/incidentApi'

// Default reporter until there is real sign-in.
const DEFAULT_REPORTER_ID = '918bface-4f32-4450-9507-588685f3392b'

const EMPTY_INCIDENT = {
  title: '',
  description: '',
  priority: 'MEDIUM',
  category: '',
}

function CreateIncidentModal({ onClose, onCreated }) {
  const [incident, setIncident] = useState(EMPTY_INCIDENT)
  const [users, setUsers] = useState([])
  const [reporterId, setReporterId] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState('')

  useEffect(() => {
    async function loadUsers() {
      try {
        const data = await getUsers()
        setUsers(data)
        const preferred =
          data.find((user) => user.id === DEFAULT_REPORTER_ID) ?? data[0]
        setReporterId(preferred?.id ?? '')
      } catch (requestError) {
        console.error(requestError)
        setFormError('Unable to load users. You can still try again later.')
      }
    }

    loadUsers()
  }, [])

  useEffect(() => {
    function handleKeyDown(event) {
      if (event.key === 'Escape' && !submitting) onClose()
    }

    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [onClose, submitting])

  function updateField(field, value) {
    setIncident((current) => ({ ...current, [field]: value }))
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setSubmitting(true)
    setFormError('')

    try {
      const created = await createIncident({
        ...incident,
        createdById: reporterId,
      })
      onCreated(created)
    } catch (requestError) {
      console.error(requestError)
      setFormError(
        describeApiError(requestError, 'Unable to create the incident.')
      )
      setSubmitting(false)
    }
  }

  return (
    <div
      className="modal-backdrop"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget && !submitting) onClose()
      }}
    >
      <section
        className="modal-card"
        role="dialog"
        aria-modal="true"
        aria-labelledby="create-incident-title"
      >
        <div className="modal-header">
          <div>
            <p className="eyebrow">NEW REQUEST</p>
            <h2 id="create-incident-title">Create incident</h2>
          </div>

          <button
            type="button"
            className="close-button"
            aria-label="Close"
            onClick={onClose}
            disabled={submitting}
          >
            ×
          </button>
        </div>

        <form onSubmit={handleSubmit}>
          <label>
            Title
            <input
              type="text"
              value={incident.title}
              onChange={(event) => updateField('title', event.target.value)}
              maxLength={200}
              autoFocus
              required
            />
          </label>

          <label>
            Description
            <textarea
              rows="5"
              value={incident.description}
              onChange={(event) =>
                updateField('description', event.target.value)
              }
              maxLength={5000}
              required
            />
          </label>

          <div className="form-grid">
            <label>
              Priority
              <select
                value={incident.priority}
                onChange={(event) =>
                  updateField('priority', event.target.value)
                }
              >
                <option value="LOW">Low</option>
                <option value="MEDIUM">Medium</option>
                <option value="HIGH">High</option>
                <option value="CRITICAL">Critical</option>
              </select>
            </label>

            <label>
              Category
              <input
                type="text"
                placeholder="Authentication"
                value={incident.category}
                onChange={(event) =>
                  updateField('category', event.target.value)
                }
                maxLength={100}
                required
              />
            </label>
          </div>

          <label>
            Reported by
            <select
              value={reporterId}
              onChange={(event) => setReporterId(event.target.value)}
              required
            >
              {users.length === 0 && <option value="">Loading users…</option>}
              {users.map((user) => (
                <option key={user.id} value={user.id}>
                  {user.displayName}
                </option>
              ))}
            </select>
          </label>

          {formError && <p className="error-message">{formError}</p>}

          <div className="form-actions">
            <button
              type="button"
              className="secondary-button"
              onClick={onClose}
              disabled={submitting}
            >
              Cancel
            </button>

            <button
              type="submit"
              className="primary-button"
              disabled={submitting || !reporterId}
            >
              {submitting ? 'Creating...' : 'Create incident'}
            </button>
          </div>
        </form>
      </section>
    </div>
  )
}

export default CreateIncidentModal
