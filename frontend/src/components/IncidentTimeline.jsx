import { useEffect, useState } from 'react'
import {
  addIncidentComment,
  describeApiError,
  getIncidentEvents,
} from '../services/incidentApi'
import { useCurrentUser } from '../context/currentUserContext'
import { STATUS_LABELS, canWorkIncidents } from '../utils/roles'
import { mentionSuggestions, splitMentions, withMentionMarkup } from '../utils/mentions'
import MentionTextarea from './MentionTextarea'

const COMMENT_MAX_LENGTH = 2000

function statusLabel(value) {
  return STATUS_LABELS[value] ?? value
}

function titleCase(value) {
  return value ? value.charAt(0) + value.slice(1).toLowerCase() : value
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
    case 'TITLE_CHANGED':
      return `${by ? `${by}renamed` : 'Renamed'} from “${event.fromValue}” to “${event.toValue}”`
    case 'DESCRIPTION_EDITED':
      return by ? `${by}edited the description` : 'Description edited'
    case 'PRIORITY_CHANGED':
      return `${by ? `${by}changed priority` : 'Priority changed'} from ${titleCase(event.fromValue)} to ${titleCase(event.toValue)}`
    case 'CATEGORY_CHANGED':
      if (!event.toValue) {
        return `${by ? `${by}cleared the category` : 'Category cleared'} (was ${event.fromValue})`
      }
      if (!event.fromValue) {
        return `${by ? `${by}set the category` : 'Category set'} to ${event.toValue}`
      }
      return `${by ? `${by}changed category` : 'Category changed'} from ${event.fromValue} to ${event.toValue}`
    case 'AI_TRIAGED':
      return by ? `${by}ran AI triage` : 'AI triage ran'
    case 'ATTACHMENT_ADDED':
      return `${actor} attached ${event.toValue}`
    case 'ATTACHMENT_REMOVED':
      return `${actor} removed the attachment ${event.toValue}`
    case 'SATISFACTION_RATED':
      return `${actor} rated this ${event.toValue}/5`
    case 'COMMENT':
      return event.internal ? `${actor} added an internal note` : `${actor} commented`
    default:
      return event.type
  }
}

/**
 * Activity timeline for an incident with a comment box. Agents and admins
 * choose between a reply (the reporter sees it) and an internal note.
 * Typing "@" suggests people to mention. `reporterId` is the incident's
 * reporter, who can be mentioned in replies.
 * Bump `refreshKey` to reload after the incident changes elsewhere.
 * `onCommented` is called after a comment is posted.
 */
function IncidentTimeline({ incidentId, reporterId, refreshKey, onCommented }) {
  const { currentUser, users } = useCurrentUser()
  const [events, setEvents] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const [body, setBody] = useState('')
  const [internal, setInternal] = useState(false)
  // People picked from the @ suggestions while writing this comment.
  const [picked, setPicked] = useState([])
  const [posting, setPosting] = useState(false)
  const [postError, setPostError] = useState('')
  const canWriteNotes = canWorkIncidents(currentUser)
  const asNote = canWriteNotes && internal

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
      const comment = await addIncidentComment(incidentId, withMentionMarkup(body, picked), {
        internal: asNote,
      })
      setEvents((current) => [comment, ...current])
      setBody('')
      setPicked([])
      onCommented?.()
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

      <form
        className={`comment-form${asNote ? ' internal' : ''}`}
        onSubmit={handleComment}
      >
        {canWriteNotes && (
          <div className="comment-mode" role="radiogroup" aria-label="Who can see this">
            <label className={asNote ? '' : 'selected'}>
              <input
                type="radio"
                name="comment-mode"
                checked={!asNote}
                onChange={() => setInternal(false)}
                disabled={posting}
              />
              Reply
            </label>
            <label className={asNote ? 'selected' : ''}>
              <input
                type="radio"
                name="comment-mode"
                checked={asNote}
                onChange={() => setInternal(true)}
                disabled={posting}
              />
              Internal note
            </label>
            <span className="comment-mode-hint">
              {asNote
                ? 'Only agents and admins will see this.'
                : 'The reporter will see this.'}
            </span>
          </div>
        )}
        <MentionTextarea
          rows="3"
          aria-label={asNote ? 'Internal note' : 'Comment'}
          placeholder={
            asNote
              ? 'Add an internal note for agents and admins…'
              : canWriteNotes
                ? 'Write a reply…'
                : 'Add a comment…'
          }
          value={body}
          onChange={setBody}
          suggest={(query) =>
            mentionSuggestions(users ?? [], query, {
              internal: asNote,
              reporterId,
              currentUserId: currentUser?.id,
            })
          }
          onPick={(person) =>
            setPicked((current) =>
              current.some((item) => item.id === person.id) ? current : [...current, person]
            )
          }
          maxLength={COMMENT_MAX_LENGTH}
          disabled={posting}
        />

        <div className="comment-actions">
          <span className="comment-author">
            Commenting as <strong>{currentUser?.displayName}</strong>
          </span>

          <span className="comment-count">
            {body.length}/{COMMENT_MAX_LENGTH}
          </span>

          <button
            type="submit"
            className="primary-button"
            disabled={posting || body.trim() === ''}
          >
            {posting ? 'Posting...' : asNote ? 'Add internal note' : 'Comment'}
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
            className={`timeline-item ${event.type.toLowerCase()}${event.internal ? ' internal' : ''}`}
          >
            <span className="timeline-dot" aria-hidden="true" />
            <div className="timeline-body">
              <p className="timeline-title">
                {describeEvent(event)}
                {event.internal && <span className="internal-badge">Internal</span>}
              </p>
              {event.message && (
                <p
                  className={
                    event.type === 'COMMENT'
                      ? 'timeline-comment'
                      : 'timeline-note'
                  }
                >
                  {event.type === 'COMMENT'
                    ? splitMentions(event.message).map((part, index) =>
                        part.mention ? (
                          <span
                            key={index}
                            className={`mention${part.mention.id === currentUser?.id ? ' mention-me' : ''}`}
                          >
                            @{part.mention.name}
                          </span>
                        ) : (
                          part.text
                        )
                      )
                    : event.message}
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
