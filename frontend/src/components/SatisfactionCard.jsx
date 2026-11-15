import { useState } from 'react'
import { describeApiError, rateIncident } from '../services/incidentApi'

const RATING_LABELS = {
  1: 'Very poor',
  2: 'Poor',
  3: 'OK',
  4: 'Good',
  5: 'Great',
}

function Stars({ score }) {
  return (
    <span className="rating-stars" aria-hidden="true">
      {'★'.repeat(score)}
      <span className="rating-stars-empty">{'★'.repeat(5 - score)}</span>
    </span>
  )
}

/**
 * Once an incident is resolved or closed, its reporter can rate how it went
 * (1 to 5, with an optional comment) and change that later. Everyone else
 * sees the rating, or that there isn't one yet.
 */
function SatisfactionCard({ incident, currentUser, onRated }) {
  const done = incident.status === 'RESOLVED' || incident.status === 'CLOSED'
  const isReporter = incident.createdById === currentUser?.id
  const rated = incident.satisfactionScore != null

  const [score, setScore] = useState(incident.satisfactionScore ?? 0)
  const [comment, setComment] = useState(incident.satisfactionComment ?? '')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')

  if (!done && !rated) return null

  async function handleSubmit(event) {
    event.preventDefault()
    setSaving(true)
    setError('')
    setMessage('')
    try {
      const updated = await rateIncident(incident.id, { score, comment })
      setMessage('Thanks for your feedback.')
      onRated(updated)
    } catch (requestError) {
      console.error(requestError)
      setError(describeApiError(requestError, 'Unable to save your rating.'))
    } finally {
      setSaving(false)
    }
  }

  if (isReporter && done) {
    return (
      <section className="content-card satisfaction-card">
        <h2>{rated ? 'Your rating' : 'How did we do?'}</h2>
        <p className="account-note">
          Rate how this incident was handled. The team sees your rating and comment.
        </p>
        <form onSubmit={handleSubmit}>
          <fieldset className="rating-picker" disabled={saving}>
            <legend>Rating</legend>
            {[1, 2, 3, 4, 5].map((value) => (
              <label key={value} className={value <= score ? 'on' : ''}>
                <input
                  type="radio"
                  name="satisfaction"
                  value={value}
                  checked={score === value}
                  onChange={() => setScore(value)}
                  aria-label={`${value} out of 5: ${RATING_LABELS[value]}`}
                />
                <span aria-hidden="true">★</span>
              </label>
            ))}
            <span className="rating-label">{score ? RATING_LABELS[score] : 'Choose 1 to 5 stars'}</span>
          </fieldset>
          <label className="rating-comment">
            Comment (optional)
            <textarea
              rows="2"
              maxLength={1000}
              value={comment}
              onChange={(event) => setComment(event.target.value)}
              disabled={saving}
            />
          </label>
          <button type="submit" className="primary-button" disabled={saving || score === 0}>
            {saving ? 'Saving…' : rated ? 'Update rating' : 'Send rating'}
          </button>
          {message && <p className="status-message">{message}</p>}
          {error && (
            <p className="error-message" role="alert">
              {error}
            </p>
          )}
        </form>
      </section>
    )
  }

  return (
    <section className="content-card satisfaction-card">
      <h2>Reporter's rating</h2>
      {rated ? (
        <>
          <p className="rating-summary">
            <Stars score={incident.satisfactionScore} />{' '}
            <strong>
              {incident.satisfactionScore}/5 · {RATING_LABELS[incident.satisfactionScore]}
            </strong>
          </p>
          {incident.satisfactionComment && (
            <blockquote className="rating-quote">{incident.satisfactionComment}</blockquote>
          )}
        </>
      ) : (
        <p className="timeline-empty">The reporter hasn't rated this yet.</p>
      )}
    </section>
  )
}

export default SatisfactionCard
