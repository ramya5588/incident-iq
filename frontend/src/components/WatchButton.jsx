import { useEffect, useState } from 'react'
import {
  describeApiError,
  getWatchers,
  stopWatchingIncident,
  watchIncident,
} from '../services/incidentApi'

/**
 * Watch / Stop watching an incident, with how many people watch it. Agents
 * and admins also get the names (the server leaves them out for reporters).
 */
function WatchButton({ incidentId }) {
  const [state, setState] = useState(null)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    getWatchers(incidentId)
      .then((data) => {
        if (!cancelled) setState(data)
      })
      .catch((requestError) => {
        console.error(requestError)
        if (!cancelled) setError('Unable to load watchers.')
      })
    return () => {
      cancelled = true
    }
  }, [incidentId])

  async function toggle() {
    setSaving(true)
    setError('')
    try {
      setState(
        await (state.watching ? stopWatchingIncident(incidentId) : watchIncident(incidentId))
      )
    } catch (requestError) {
      console.error(requestError)
      setError(describeApiError(requestError, 'Unable to change watching.'))
    } finally {
      setSaving(false)
    }
  }

  if (!state) {
    return error ? <p className="error-message">{error}</p> : null
  }

  const names = state.watchers?.map((watcher) => watcher.displayName).join(', ')

  return (
    <div className="watch-control">
      <button
        type="button"
        className={state.watching ? 'secondary-button watching' : 'secondary-button'}
        aria-pressed={state.watching}
        onClick={toggle}
        disabled={saving}
      >
        {state.watching ? '★ Watching' : '☆ Watch'}
      </button>
      <span className="watch-count" title={names || undefined}>
        {state.count === 0
          ? 'No watchers'
          : `Watched by ${state.count}${names ? `: ${names}` : ''}`}
      </span>
      {error && (
        <p className="error-message" role="alert">
          {error}
        </p>
      )}
    </div>
  )
}

export default WatchButton
