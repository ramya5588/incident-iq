import { useState } from 'react'
import { listSavedReplies } from '../services/incidentApi'
import { matchReplies } from '../utils/savedReplies'

/**
 * "Saved replies" button for the comment box. Opens a searchable list
 * (loaded the first time it's opened); picking one calls `onPick(reply)`.
 */
function SavedReplyPicker({ onPick, disabled }) {
  const [open, setOpen] = useState(false)
  const [replies, setReplies] = useState(null)
  const [error, setError] = useState('')
  const [search, setSearch] = useState('')

  async function toggle() {
    const next = !open
    setOpen(next)
    setSearch('')
    if (next && replies === null) {
      try {
        setReplies(await listSavedReplies())
        setError('')
      } catch (requestError) {
        console.error(requestError)
        setError('Unable to load saved replies.')
      }
    }
  }

  function pick(reply) {
    setOpen(false)
    onPick(reply)
  }

  const shown = replies ? matchReplies(replies, search) : []

  return (
    <div
      className="saved-reply-picker"
      onKeyDown={(event) => {
        if (event.key === 'Escape' && open) {
          event.stopPropagation()
          setOpen(false)
        }
      }}
    >
      <button
        type="button"
        className="secondary-button small-button"
        aria-expanded={open}
        onClick={toggle}
        disabled={disabled}
      >
        Saved replies
      </button>

      {open && (
        <div className="saved-reply-popover" role="dialog" aria-label="Saved replies">
          <input
            type="search"
            aria-label="Search saved replies"
            placeholder="Search saved replies"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            autoFocus
          />
          {error && <p className="error-message">{error}</p>}
          {replies === null && !error && <p className="timeline-empty">Loading…</p>}
          {replies !== null && shown.length === 0 && (
            <p className="timeline-empty">
              {replies.length === 0
                ? 'No saved replies yet. Add them on your Account page.'
                : 'No saved replies match.'}
            </p>
          )}
          {shown.length > 0 && (
            <ul>
              {shown.map((reply) => (
                <li key={reply.id}>
                  <button type="button" onClick={() => pick(reply)}>
                    <strong>{reply.title}</strong>
                    {!reply.shared && <span className="saved-reply-tag">Personal</span>}
                    <small>{reply.body}</small>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  )
}

export default SavedReplyPicker
