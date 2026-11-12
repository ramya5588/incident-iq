import { useEffect, useState } from 'react'
import {
  addIncidentComment,
  describeApiError,
  getIncidentEvents,
} from '../services/incidentApi'
import { useCurrentUser } from '../context/currentUserContext'

const STATUS_LABELS = {
  OPEN: 'Open',
  IN_PROGRESS: 'In progress',
  RESOLVED: 'Resolved',
  CLOSED: 'Closed',
}

const COMMENT_MAX_LENGTH = 2000

function statusLabel(value) {
  return STATUS_LABELS[value] ?? value
}

function formatTime(value) {
  return new Date(value).toLocaleString(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  })
}

function describeEvent(event) {
  const actor = event.actorName ?? 'Someone'
  // Older events (and API calls without X-User-Id) have no actor.
  const by = event.actorName ? `${event.actorName} ` : ''

  switch (event.type) {
    case 'CREATED':
      return `${actor} reported this incident`
    case 'STATUS_CHANGED':
      return by
        ? `${by}changed status from ${statusLabel(event.fromValue)} to ${statusLabel(event.toValue)}`
        : `Status changed from ${statusLabel(event.fromValue)} to ${statusLabel(event.toValue)}`
    case 'ASSIGNEE_CHANGED':
      if (event.fromValue && event.toValue) {
        return by
          ? `${by}reassigned from ${event.fromValue} to ${event.toValue}`
          : `Reassigned from ${event.fromValue} to ${event.toValue}`
      }
      if (event.toValue) {
        if (event.actorName === event.toValue) return `${by}took this incident`
        return by ? `${by}assigned ${event.toValue}` : `Assigned to ${event.toValue}`
      }
      return by
        ? `${by}unassigned ${event.fromValue ?? 'the previous assignee'}`
        : `Unassigned from ${event.fromValue ?? 'previous assignee'}`
    case 'AI_TRIAGED':
      return by ? `${by}ran AI triage` : 'AI triage ran'
    case 'COMMENT':
      return `${actor} commented`
    default:
      return event.type
  }
}

/**
 * Activity timeline for an incident with a comment box.
 * Bump `refreshKey` to reload after the incident changes elsewhere.
 */
function IncidentTimeline({ incidentId, refreshKey }) {
  const { users, currentUser } = useCurrentUser()
  const [events, setEvents] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const [authorId, setAuthorId] = useState('')
  const [body, setBody] = useState('')
  const [posting, setPosting] = useState(false)
  const [postError, setPostError] = useState('')

  // Defaults to whoever you're acting as; '' until picked explicitly.
  const effectiveAuthorId = authorId || currentUser?.id || ''

  useEffect(() => {
    let cancelled = false

    async function loadEvents() {
      try {
        const data = await getIncidentEvents(incidentId)
        if (!cancelled) {
          setEvents(data)
          setError('')
        }
      } catch (requestError) {
        console.error(requestError)
        if (!cancelled) setError('Unable to load the activity timeline.')
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    loadEvents()

    return () => {
      cancelled = true
    }
  }, [incidentId, refreshKey])

  async function handleComment(event) {
    event.preventDefault()
    setPosting(true)
    setPostError('')

    try {
      const comment = await addIncidentComment(
        incidentId,
        effectiveAuthorId,
        body
      )
      setEvents((current) => [comment, ...current])
      setBody('')
    } catch (requestError) {
      console.error(requestError)
      setPostError(describeApiError(requestError, 'Unable to add the comment.'))
    } finally {
      setPosting(false)
    }
  }

  return (
    <section className="timeline-section">
      <h2>Activity</h2>

      <form className="comment-form" onSubmit={handleComment}>
        <textarea
          rows="3"
          placeholder="Add a note for the team…"
          value={body}
          onChange={(event) => setBody(event.target.value)}
          maxLength={COMMENT_MAX_LENGTH}
          disabled={posting}
        />

        <div className="comment-actions">
          <label>
            As
            <select
              value={effectiveAuthorId}
              onChange={(event) => setAuthorId(event.target.value)}
              disabled={posting || users.length === 0}
            >
              {users.map((user) => (
                <option key={user.id} value={user.id}>
                  {user.displayName}
                </option>
              ))}
            </select>
          </label>

          <span className="comment-count">
            {body.length}/{COMMENT_MAX_LENGTH}
          </span>

          <button
            type="submit"
            className="primary-button"
            disabled={posting || body.trim() === '' || !effectiveAuthorId}
          >
            {posting ? 'Posting...' : 'Comment'}
          </button>
        </div>

        {postError && <p className="error-message">{postError}</p>}
      </form>

      {loading && <p className="timeline-empty">Loading activity…</p>}
      {error && <p className="error-message">{error}</p>}
      {!loading && !error && events.length === 0 && (
        <p className="timeline-empty">No activity yet.</p>
      )}

      <ol className="timeline">
        {events.map((event) => (
          <li
            key={event.id}
            className={`timeline-item ${event.type.toLowerCase()}`}
          >
            <span className="timeline-dot" aria-hidden="true" />
            <div className="timeline-body">
              <p className="timeline-title">{describeEvent(event)}</p>
              {event.message && (
                <p
                  className={
                    event.type === 'COMMENT'
                      ? 'timeline-comment'
                      : 'timeline-note'
                  }
                >
                  {event.message}
                </p>
              )}
              <time className="timeline-time" dateTime={event.createdAt}>
                {formatTime(event.createdAt)}
              </time>
            </div>
          </li>
        ))}
      </ol>
    </section>
  )
}

export default IncidentTimeline
