import { useEffect, useState } from 'react'
import {
  describeApiError,
  getBusinessHours,
  saveBusinessHours,
} from '../services/incidentApi'
import { DAYS, PRIORITIES, describeBusinessHours, timeZones } from '../utils/businessHours'

const TIME_ZONES = timeZones()

function toggle(list, value, on) {
  return on ? [...new Set([...list, value])] : list.filter((item) => item !== value)
}

/**
 * Business hours for SLA deadlines. Admins edit them; everyone else sees a
 * summary.
 */
function BusinessHoursSettings({ admin }) {
  const [saved, setSaved] = useState(null)
  const [form, setForm] = useState(null)
  const [loadError, setLoadError] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')

  useEffect(() => {
    let cancelled = false
    getBusinessHours()
      .then((data) => {
        if (cancelled) return
        setSaved(data)
        setForm(data)
      })
      .catch((requestError) => {
        console.error(requestError)
        if (!cancelled) setLoadError('Unable to load the business hours.')
      })
    return () => {
      cancelled = true
    }
  }, [])

  function update(changes) {
    setForm((current) => ({ ...current, ...changes }))
    setMessage('')
  }

  function updateHoliday(index, changes) {
    update({
      holidays: form.holidays.map((holiday, i) =>
        i === index ? { ...holiday, ...changes } : holiday
      ),
    })
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setSaving(true)
    setError('')
    setMessage('')
    try {
      const data = await saveBusinessHours(form)
      setSaved(data)
      setForm(data)
      setMessage(
        'Business hours saved. They apply to deadlines set from now on; existing deadlines stay as they are.'
      )
    } catch (requestError) {
      console.error(requestError)
      setError(describeApiError(requestError, 'Unable to save the business hours.'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="content-card business-hours-card">
      <div className="section-heading">
        <div>
          <h2>SLA business hours</h2>
          <p>{saved ? describeBusinessHours(saved) : 'Which time counts toward SLA deadlines.'}</p>
        </div>
      </div>

      {loadError && <p className="error-message">{loadError}</p>}

      {admin && form && (
        <form className="business-hours-form" onSubmit={handleSubmit}>
          <label className="checkbox-row">
            <input
              type="checkbox"
              checked={form.enabled}
              onChange={(event) => update({ enabled: event.target.checked })}
              disabled={saving}
            />
            <span>
              Count only business hours
              <small>Off: every deadline runs around the clock.</small>
            </span>
          </label>

          <fieldset disabled={saving || !form.enabled}>
            <div className="business-hours-row">
              <label>
                Time zone
                <input
                  list="business-hours-zones"
                  value={form.timeZone}
                  onChange={(event) => update({ timeZone: event.target.value })}
                  required
                />
                <datalist id="business-hours-zones">
                  {TIME_ZONES.map((zone) => (
                    <option key={zone} value={zone} />
                  ))}
                </datalist>
              </label>
              <label>
                From
                <input
                  type="time"
                  value={form.dayStart}
                  onChange={(event) => update({ dayStart: event.target.value })}
                  required
                />
              </label>
              <label>
                To
                <input
                  type="time"
                  value={form.dayEnd}
                  onChange={(event) => update({ dayEnd: event.target.value })}
                  required
                />
              </label>
            </div>

            <div className="choice-group" role="group" aria-label="Working days">
              <span>Working days</span>
              {DAYS.map(([value, label]) => (
                <label key={value} className="chip-option">
                  <input
                    type="checkbox"
                    checked={form.workingDays.includes(value)}
                    onChange={(event) =>
                      update({ workingDays: toggle(form.workingDays, value, event.target.checked) })
                    }
                  />
                  {label}
                </label>
              ))}
            </div>

            <div className="choice-group" role="group" aria-label="Priorities using business hours">
              <span>Use for</span>
              {PRIORITIES.map(([value, label]) => (
                <label key={value} className="chip-option">
                  <input
                    type="checkbox"
                    checked={form.priorities.includes(value)}
                    onChange={(event) =>
                      update({ priorities: toggle(form.priorities, value, event.target.checked) })
                    }
                  />
                  {label}
                </label>
              ))}
              <small>Unticked priorities keep running around the clock.</small>
            </div>

            <div className="holidays">
              <span>Holidays</span>
              {form.holidays.length === 0 && <small>No holidays.</small>}
              {form.holidays.map((holiday, index) => (
                <div className="holiday-row" key={index}>
                  <input
                    type="date"
                    aria-label={`Holiday ${index + 1} date`}
                    value={holiday.date}
                    onChange={(event) => updateHoliday(index, { date: event.target.value })}
                    required
                  />
                  <input
                    aria-label={`Holiday ${index + 1} name`}
                    placeholder="Name, e.g. New Year's Day"
                    value={holiday.name}
                    maxLength={100}
                    onChange={(event) => updateHoliday(index, { name: event.target.value })}
                    required
                  />
                  <button
                    type="button"
                    className="secondary-button small-button"
                    aria-label={`Remove holiday ${index + 1}`}
                    onClick={() =>
                      update({ holidays: form.holidays.filter((_, i) => i !== index) })
                    }
                  >
                    Remove
                  </button>
                </div>
              ))}
              <button
                type="button"
                className="secondary-button small-button"
                onClick={() => update({ holidays: [...form.holidays, { date: '', name: '' }] })}
              >
                Add holiday
              </button>
            </div>
          </fieldset>

          {error && <p className="error-message">{error}</p>}
          {message && <p className="status-message">{message}</p>}

          <div className="form-actions">
            <button type="submit" className="primary-button" disabled={saving}>
              {saving ? 'Saving...' : 'Save business hours'}
            </button>
          </div>
        </form>
      )}
    </section>
  )
}

export default BusinessHoursSettings
