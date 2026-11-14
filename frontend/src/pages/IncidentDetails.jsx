import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
    describeApiError,
    getIncidentById,
    triageIncident,
    updateIncidentAssignee,
    updateIncidentStatus,
} from '../services/incidentApi'
import IncidentTimeline from '../components/IncidentTimeline'
import IncidentEditForm from '../components/IncidentEditForm'
import { useCurrentUser } from '../context/currentUserContext'
import {
    HOLD_REASONS,
    STATUS_LABELS,
    canBeAssigned,
    canWorkIncidents,
} from '../utils/roles'
import SlaBadge from '../components/SlaBadge'
import { useNow } from '../hooks/useNow'
import { formatDeadline } from '../utils/sla'

const STATUS_OPTIONS = Object.entries(STATUS_LABELS).map(([value, label]) => ({
    value,
    label,
}))

const HOLD_NOTE_MAX_LENGTH = 200

function SlaRow({ label, hint, clock, startedAt, now }) {
    return (
        <div className="sla-row">
            <dt>
                {label}
                <small>{hint}</small>
            </dt>
            <dd>
                <span className="sla-due">
                    {clock.state === 'PAUSED'
                        ? 'Moves later while on hold'
                        : formatDeadline(clock.dueAt)}
                </span>
                <SlaBadge clock={clock} startedAt={startedAt} now={now} />
                {clock.completedAt && (
                    <small className="sla-done">
                        Done {formatDeadline(clock.completedAt)}
                    </small>
                )}
            </dd>
        </div>
    )
}

function IncidentDetails() {
    const { id } = useParams()

    const [incident, setIncident] = useState(null)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState('')
    const [triaging, setTriaging] = useState(false)
    const [triageError, setTriageError] = useState('')
    const [selectedStatus, setSelectedStatus] = useState('')
    const [updatingStatus, setUpdatingStatus] = useState(false)
    const [statusError, setStatusError] = useState('')
    const [statusMessage, setStatusMessage] = useState('')
    const [holdReason, setHoldReason] = useState('')
    const [holdNote, setHoldNote] = useState('')
    const { users, usersError, currentUser } = useCurrentUser()
    const now = useNow()
    // Reporters (USER role) can view and comment; the rest is for agents/admins.
    const canWork = canWorkIncidents(currentUser)
    const [selectedAssignee, setSelectedAssignee] = useState('')
    const [updatingAssignee, setUpdatingAssignee] = useState(false)
    const [assigneeError, setAssigneeError] = useState('')
    const [assigneeMessage, setAssigneeMessage] = useState('')
    const [editing, setEditing] = useState(false)
    const [editMessage, setEditMessage] = useState('')
    const [timelineKey, setTimelineKey] = useState(0)
    const refreshTimeline = () => setTimelineKey((key) => key + 1)

    useEffect(() => {
        async function loadIncident() {
            try {
                const data = await getIncidentById(id)
                setIncident(data)
                setSelectedStatus(data.status)
                setHoldReason(data.holdReason ?? '')
                setHoldNote(data.holdNote ?? '')
                setSelectedAssignee(data.assignedToId ?? '')
            } catch (requestError) {
                console.error(requestError)
                setError('Unable to load this incident.')
            } finally {
                setLoading(false)
            }
        }

        loadIncident()
    }, [id])
    async function handleTriage() {
        setTriaging(true)
        setTriageError('')

        try {
            const updatedIncident = await triageIncident(id)
            setIncident(updatedIncident)
            refreshTimeline()
        } catch (requestError) {
            console.error(requestError)
            setTriageError(
                describeApiError(requestError, 'Unable to run AI triage. Please try again.')
            )
        } finally {
            setTriaging(false)
        }
    }

    async function handleStatusUpdate(event) {
        event.preventDefault()
        setUpdatingStatus(true)
        setStatusError('')
        setStatusMessage('')

        try {
            const hold =
                selectedStatus === 'ON_HOLD' ? { holdReason, holdNote } : {}
            const updatedIncident = await updateIncidentStatus(id, selectedStatus, hold)
            showStatus(updatedIncident)
            setStatusMessage(
                updatedIncident.status === 'ON_HOLD'
                    ? 'On hold. The resolution clock is paused.'
                    : 'Status updated.'
            )
            refreshTimeline()
        } catch (requestError) {
            console.error(requestError)
            setStatusError(
                describeApiError(requestError, 'Unable to update the status. Please try again.')
            )
        } finally {
            setUpdatingStatus(false)
        }
    }

    function showStatus(updatedIncident) {
        setIncident(updatedIncident)
        setSelectedStatus(updatedIncident.status)
        setHoldReason(updatedIncident.holdReason ?? '')
        setHoldNote(updatedIncident.holdNote ?? '')
    }

    // A reporter's reply can take the incident off hold, so reload it.
    async function handleCommented() {
        if (incident?.status !== 'ON_HOLD') return
        try {
            const updatedIncident = await getIncidentById(id)
            if (updatedIncident.status !== incident.status) {
                showStatus(updatedIncident)
                refreshTimeline()
            }
        } catch (requestError) {
            console.error(requestError)
        }
    }

    async function handleAssigneeUpdate(event) {
        event.preventDefault()
        setUpdatingAssignee(true)
        setAssigneeError('')
        setAssigneeMessage('')

        try {
            const updatedIncident = await updateIncidentAssignee(
                id,
                selectedAssignee
            )
            setIncident(updatedIncident)
            setSelectedAssignee(updatedIncident.assignedToId ?? '')
            setAssigneeMessage(
                updatedIncident.assignedToName
                    ? `Assigned to ${updatedIncident.assignedToName}.`
                    : 'Incident unassigned.'
            )
            refreshTimeline()
        } catch (requestError) {
            console.error(requestError)
            setAssigneeError(
                describeApiError(requestError, 'Unable to update the assignee. Please try again.')
            )
        } finally {
            setUpdatingAssignee(false)
        }
    }

    if (loading) {
        return <main className="details-page">Loading incident...</main>
    }

    if (error) {
        return (
            <main className="details-page">
                <Link to="/">← Back to dashboard</Link>
                <p>{error}</p>
            </main>
        )
    }

    if (!incident) {
        return <main className="details-page">Incident not found.</main>
    }

    const holdChanged =
        selectedStatus === 'ON_HOLD' &&
        (holdReason !== (incident.holdReason ?? '') ||
            holdNote.trim() !== (incident.holdNote ?? ''))
    const statusUnchanged = selectedStatus === incident.status && !holdChanged
    const missingHoldReason = selectedStatus === 'ON_HOLD' && holdReason === ''

    // Active agents and admins, plus whoever is assigned now (so the menu
    // still shows them if their role changed or they were deactivated).
    const assignableUsers = users.filter(
        (user) =>
            (canBeAssigned(user) && user.active !== false) ||
            user.id === incident.assignedToId
    )

    return (
        <main className="details-page">
            <Link to="/">← Back to dashboard</Link>

            <p className="eyebrow">INCIDENT DETAILS</p>

            {editing ? (
                <IncidentEditForm
                    incident={incident}
                    onCancel={() => setEditing(false)}
                    onSaved={(updatedIncident) => {
                        setIncident(updatedIncident)
                        setEditing(false)
                        setEditMessage('Details saved.')
                        refreshTimeline()
                    }}
                />
            ) : (
                <>
                    <div className="details-title-row">
                        <h1>{incident.title}</h1>
                        {canWork && (
                            <button
                                type="button"
                                className="secondary-button"
                                onClick={() => {
                                    setEditing(true)
                                    setEditMessage('')
                                }}
                            >
                                Edit details
                            </button>
                        )}
                    </div>

                    <p>
                        INC-{String(incident.incidentNumber).padStart(3, '0')}
                    </p>

                    {editMessage && (
                        <p className="status-message">{editMessage}</p>
                    )}
                </>
            )}

            <section>
                {!editing && (
                    <>
                        <h2>Description</h2>
                        <p className="details-description">
                            {incident.description}
                        </p>
                    </>
                )}

                {canWork ? (
                    <form className="status-form" onSubmit={handleStatusUpdate}>
                        <label htmlFor="incident-status">
                            <strong>Status:</strong>
                        </label>
                        <select
                            id="incident-status"
                            value={selectedStatus}
                            onChange={(event) => {
                                setSelectedStatus(event.target.value)
                                setStatusMessage('')
                            }}
                            disabled={updatingStatus}
                        >
                            {STATUS_OPTIONS.map((option) => (
                                <option key={option.value} value={option.value}>
                                    {option.label}
                                </option>
                            ))}
                        </select>
                        <button
                            className="secondary-button"
                            type="submit"
                            disabled={
                                updatingStatus || statusUnchanged || missingHoldReason
                            }
                        >
                            {updatingStatus ? 'Updating...' : 'Update status'}
                        </button>

                        {selectedStatus === 'ON_HOLD' && (
                            <div className="hold-fields">
                                <label>
                                    Waiting for
                                    <select
                                        value={holdReason}
                                        onChange={(event) => {
                                            setHoldReason(event.target.value)
                                            setStatusMessage('')
                                        }}
                                        disabled={updatingStatus}
                                        required
                                    >
                                        <option value="">Choose a reason…</option>
                                        {Object.entries(HOLD_REASONS).map(
                                            ([value, label]) => (
                                                <option key={value} value={value}>
                                                    {label}
                                                </option>
                                            )
                                        )}
                                    </select>
                                </label>
                                <label>
                                    Note (optional)
                                    <input
                                        type="text"
                                        value={holdNote}
                                        maxLength={HOLD_NOTE_MAX_LENGTH}
                                        placeholder="e.g. Vendor ticket 4471"
                                        onChange={(event) => {
                                            setHoldNote(event.target.value)
                                            setStatusMessage('')
                                        }}
                                        disabled={updatingStatus}
                                    />
                                </label>
                                <small className="hold-hint">
                                    {holdReason === 'WAITING_ON_REPORTER'
                                        ? "The resolution clock pauses. The reporter's next comment puts it back in progress."
                                        : 'The resolution clock pauses until the status changes.'}
                                </small>
                            </div>
                        )}
                    </form>
                ) : (
                    <p>
                        <strong>Status:</strong>{' '}
                        {STATUS_LABELS[incident.status] ?? incident.status}
                    </p>
                )}

                {incident.status === 'ON_HOLD' && (
                    <p className="hold-summary">
                        <strong>On hold</strong> since{' '}
                        {formatDeadline(incident.onHoldSince)}:{' '}
                        {HOLD_REASONS[incident.holdReason] ?? incident.holdReason}
                        {incident.holdNote ? `. ${incident.holdNote}` : ''}
                    </p>
                )}

                {statusMessage && (
                    <p className="status-message">{statusMessage}</p>
                )}
                {statusError && (
                    <p className="error-message">{statusError}</p>
                )}

                {canWork ? (
                    <form className="status-form" onSubmit={handleAssigneeUpdate}>
                        <label htmlFor="incident-assignee">
                            <strong>Assigned to:</strong>
                        </label>
                        <select
                            id="incident-assignee"
                            value={selectedAssignee}
                            onChange={(event) => {
                                setSelectedAssignee(event.target.value)
                                setAssigneeMessage('')
                            }}
                            disabled={updatingAssignee || usersError !== ''}
                        >
                            <option value="">Unassigned</option>
                            {assignableUsers.map((user) => (
                                <option key={user.id} value={user.id}>
                                    {user.displayName}
                                    {user.role !== 'USER'
                                        ? ` (${user.role.toLowerCase()})`
                                        : ''}
                                    {user.active === false ? ' – deactivated' : ''}
                                </option>
                            ))}
                        </select>
                        <button
                            className="secondary-button"
                            type="submit"
                            disabled={
                                updatingAssignee ||
                                selectedAssignee === (incident.assignedToId ?? '')
                            }
                        >
                            {updatingAssignee ? 'Saving...' : 'Update assignee'}
                        </button>
                    </form>
                ) : (
                    <p>
                        <strong>Assigned to:</strong>{' '}
                        {incident.assignedToName ?? 'Unassigned'}
                    </p>
                )}

                {assigneeMessage && (
                    <p className="status-message">{assigneeMessage}</p>
                )}
                {(assigneeError || (canWork && usersError)) && (
                    <p className="error-message">{assigneeError || usersError}</p>
                )}

                {incident.resolvedAt && (
                    <p>
                        <strong>Resolved:</strong>{' '}
                        {new Date(incident.resolvedAt).toLocaleString()}
                    </p>
                )}

                {!editing && (
                    <>
                        <p>
                            <strong>Priority:</strong> {incident.priority}
                        </p>

                        <p>
                            <strong>Category:</strong>{' '}
                            {incident.category ?? 'None'}
                        </p>
                    </>
                )}
            </section>

            {incident.sla && (
                <section className="sla-section">
                    <h2>SLA</h2>
                    <dl className="sla-grid">
                        <SlaRow
                            label="Respond by"
                            hint="First assigned or moved out of Open"
                            clock={incident.sla.response}
                            startedAt={incident.createdAt}
                            now={now}
                        />
                        <SlaRow
                            label="Resolve by"
                            hint="Status set to Resolved"
                            clock={incident.sla.resolution}
                            startedAt={incident.createdAt}
                            now={now}
                        />
                    </dl>
                </section>
            )}

            {canWork && (
                <button
                    className="primary-button"
                    type="button"
                    onClick={handleTriage}
                    disabled={triaging}
                >
                    {triaging ? 'Running AI triage...' : 'Run AI triage'}
                </button>
            )}

            {triageError && (
                <p className="error-message">{triageError}</p>
            )}

            {incident.aiSummary && (
                <section className="ai-section">
                    <h2>AI triage</h2>

                    <p>
                        <strong>Summary:</strong> {incident.aiSummary}
                    </p>

                    <p>
                        <strong>Suggested priority:</strong>{' '}
                        {incident.aiSuggestedPriority}
                    </p>

                    <p>
                        <strong>Suggested category:</strong>{' '}
                        {incident.aiSuggestedCategory}
                    </p>

                    <p>
                        <strong>Reasoning:</strong> {incident.aiReasoning}
                    </p>
                </section>
            )}

            <IncidentTimeline
                incidentId={id}
                refreshKey={timelineKey}
                onCommented={handleCommented}
            />
        </main>
    )
}

export default IncidentDetails