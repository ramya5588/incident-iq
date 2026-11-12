import { useState } from 'react'
import { describeApiError, updateIncident } from '../services/incidentApi'

const PRIORITY_OPTIONS = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']

/** Inline editor for an incident's title, description, priority and category. */
function IncidentEditForm({ incident, onSaved, onCancel }) {
  const [draft, setDraft] = useState({
    title: incident.title,
    description: incident.description,
    priority: incident.priority,
    category: incident.category ?? '',
  })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  function updateField(field, value) {
    setDraft((current) => ({ ...current, [field]: value }))
  }

  // Only send what actually changed, so the timeline stays accurate.
  const changes = {}
  if (draft.title.trim() !== incident.title) changes.title = draft.title
  if (draft.description.trim() !== incident.description) {
    changes.description = draft.description
  }
  if (draft.priority !== incident.priority) changes.priority = draft.priority
  if (draft.category.trim() !== (incident.category ?? '')) {
    changes.category = draft.category
  }
  const hasChanges = Object.keys(changes).length > 0

  async function handleSubmit(event) {
    event.preventDefault()
    setSaving(true)
    setError('')

    try {
      onSaved(await updateIncident(incident.id, changes))
    } catch (requestError) {
      console.error(requestError)
      setError(describeApiError(requestError, 'Unable to save your changes.'))
      setSaving(false)
    }
  }

  return (
    <form className="edit-form" onSubmit={handleSubmit}>
      <label>
        Title
        <input
          type="text"
          value={draft.title}
          onChange={(event) => updateField('title', event.target.value)}
          maxLength={200}
          autoFocus
          required
          disabled={saving}
        />
      </label>

      <label>
        Description
        <textarea
          rows="6"
          value={draft.description}
          onChange={(event) => updateField('description', event.target.value)}
          maxLength={5000}
          required
          disabled={saving}
        />
      </label>

      <div className="form-grid">
        <label>
          Priority
          <select
            value={draft.priority}
            onChange={(event) => updateField('priority', event.target.value)}
            disabled={saving}
          >
            {PRIORITY_OPTIONS.map((priority) => (
              <option key={priority} value={priority}>
                {priority.charAt(0) + priority.slice(1).toLowerCase()}
              </option>
            ))}
          </select>
        </label>

        <label>
          Category
          <input
            type="text"
            value={draft.category}
            placeholder="Leave empty for none"
            onChange={(event) => updateField('category', event.target.value)}
            maxLength={100}
            disabled={saving}
          />
        </label>
      </div>

      {error && <p className="error-message">{error}</p>}

      <div className="form-actions">
        <button
          type="button"
          className="secondary-button"
          onClick={onCancel}
          disabled={saving}
        >
          Cancel
        </button>
        <button
          type="submit"
          className="primary-button"
          disabled={saving || !hasChanges}
        >
          {saving ? 'Saving...' : 'Save changes'}
        </button>
      </div>
    </form>
  )
}

export default IncidentEditForm
