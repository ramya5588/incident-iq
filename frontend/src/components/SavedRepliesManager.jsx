import { useEffect, useState } from 'react'
import {
  createSavedReply,
  deleteSavedReply,
  describeApiError,
  listSavedReplies,
  updateSavedReply,
} from '../services/incidentApi'
import { PLACEHOLDERS } from '../utils/savedReplies'

const EMPTY = { title: '', body: '', shared: false }

/**
 * Account page section for agents and admins: the shared replies (admins can
 * change them) and your personal ones. `admin` decides whether new replies
 * can be shared with the team.
 */
function SavedRepliesManager({ admin }) {
  const [replies, setReplies] = useState(null)
  const [loadError, setLoadError] = useState('')
  const [form, setForm] = useState(null) // { id?, title, body, shared }
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [confirming, setConfirming] = useState(null)

  useEffect(() => {
    let cancelled = false
    listSavedReplies()
      .then((data) => {
        if (!cancelled) setReplies(data)
      })
      .catch((requestError) => {
        console.error(requestError)
        if (!cancelled) setLoadError('Unable to load saved replies.')
      })
    return () => {
      cancelled = true
    }
  }, [])

  async function reload() {
    setReplies(await listSavedReplies())
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setSaving(true)
    setError('')
    try {
      if (form.id) {
        await updateSavedReply(form.id, { title: form.title, body: form.body })
      } else {
        await createSavedReply({ title: form.title, body: form.body, personal: !form.shared })
      }
      setForm(null)
      await reload()
    } catch (requestError) {
      console.error(requestError)
      setError(describeApiError(requestError, 'Unable to save the reply.'))
    } finally {
      setSaving(false)
    }
  }

  async function remove(reply) {
    setConfirming(null)
    setError('')
    try {
      await deleteSavedReply(reply.id)
      await reload()
    } catch (requestError) {
      console.error(requestError)
      setError(describeApiError(requestError, 'Unable to delete the reply.'))
    }
  }

  return (
    <section className="content-card account-card" aria-labelledby="saved-replies-title">
      <div className="section-heading">
        <div>
          <h2 id="saved-replies-title">Saved replies</h2>
          <p>
            Text you can insert into a comment with one click. Use {PLACEHOLDERS.join(', ')} for
            the reporter's name, the incident number and your name.
          </p>
        </div>
        {!form && (
          <button
            type="button"
            className="secondary-button"
            onClick={() => {
              setForm({ ...EMPTY, shared: admin })
              setError('')
            }}
          >
            Add a reply
          </button>
        )}
      </div>

      {loadError && <p className="error-message">{loadError}</p>}
      {!replies && !loadError && <p className="account-note">Loading…</p>}

      {form && (
        <form className="saved-reply-form" onSubmit={handleSubmit}>
          <label>
            Title
            <input
              value={form.title}
              maxLength={100}
              onChange={(event) => setForm({ ...form, title: event.target.value })}
              required
            />
          </label>
          <label>
            Text
            <textarea
              rows="4"
              value={form.body}
              maxLength={2000}
              onChange={(event) => setForm({ ...form, body: event.target.value })}
              required
            />
          </label>
          {admin && !form.id && (
            <label className="checkbox-filter">
              <input
                type="checkbox"
                checked={form.shared}
                onChange={(event) => setForm({ ...form, shared: event.target.checked })}
              />
              Share with the team
            </label>
          )}
          <div className="saved-reply-actions">
            <button
              type="submit"
              className="primary-button"
              disabled={saving || !form.title.trim() || !form.body.trim()}
            >
              {saving ? 'Saving…' : form.id ? 'Save changes' : 'Add reply'}
            </button>
            <button type="button" className="secondary-button" onClick={() => setForm(null)}>
              Cancel
            </button>
          </div>
        </form>
      )}

      {error && (
        <p className="error-message" role="alert">
          {error}
        </p>
      )}

      {replies && replies.length === 0 && !form && (
        <p className="account-note">No saved replies yet.</p>
      )}

      {replies && replies.length > 0 && (
        <ul className="saved-reply-list">
          {replies.map((reply) => (
            <li key={reply.id}>
              <div>
                <strong>{reply.title}</strong>{' '}
                <span className="saved-reply-tag">{reply.shared ? 'Team' : 'Personal'}</span>
                <p>{reply.body}</p>
              </div>
              {reply.canEdit &&
                (confirming === reply.id ? (
                  <span className="attachment-confirm">
                    <button
                      type="button"
                      className="danger-button small-button"
                      onClick={() => remove(reply)}
                    >
                      Delete
                    </button>
                    <button
                      type="button"
                      className="secondary-button small-button"
                      onClick={() => setConfirming(null)}
                    >
                      Keep
                    </button>
                  </span>
                ) : (
                  <span className="attachment-confirm">
                    <button
                      type="button"
                      className="secondary-button small-button"
                      aria-label={`Edit ${reply.title}`}
                      onClick={() => {
                        setForm({ id: reply.id, title: reply.title, body: reply.body, shared: reply.shared })
                        setError('')
                      }}
                    >
                      Edit
                    </button>
                    <button
                      type="button"
                      className="secondary-button small-button"
                      aria-label={`Delete ${reply.title}`}
                      onClick={() => setConfirming(reply.id)}
                    >
                      Delete
                    </button>
                  </span>
                ))}
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

export default SavedRepliesManager
