import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
    getIncidentById,
    getUsers,
    triageIncident,
    updateIncidentAssignee,
    updateIncidentStatus,
} from '../services/incidentApi'
import IncidentTimeline from '../components/IncidentTimeline'

const STATUS_OPTIONS = [
    { value: 'OPEN', label: 'Open' },
    { value: 'IN_PROGRESS', label: 'In progress' },
    { value: 'RESOLVED', label: 'Resolved' },
    { value: 'CLOSED', label: 'Closed' },
]

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
    const [users, setUsers] = useState([])
    const [usersError, setUsersError] = useState('')
    const [selectedAssignee, setSelectedAssignee] = useState('')
    const [updatingAssignee, setUpdatingAssignee] = useState(false)
    const [assigneeError, setAssigneeError] = useState('')
    const [assigneeMessage, setAssigneeMessage] = useState('')
    const [timelineKey, setTimelineKey] = useState(0)
    const refreshTimeline = () => setTimelineKey((key) => key + 1)

    useEffect(() => {
        async function loadUsers() {
            try {
                setUsers(await getUsers())
            } catch (requestError) {
                console.error(requestError)
                setUsersError('Unable to load users for assignment.')
            }
        }

        loadUsers()
    }, [])

    useEffect(() => {
        async function loadIncident() {
            try {
                const data = await getIncidentById(id)
                setIncident(data)
                setSelectedStatus(data.status)
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
            setTriageError('Unable to run AI triage. Please try again.')
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
            const updatedIncident = await updateIncidentStatus(id, selectedStatus)
            setIncident(updatedIncident)
            setSelectedStatus(updatedIncident.status)
            setStatusMessage('Status updated.')
            refreshTimeline()
        } catch (requestError) {
            console.error(requestError)
            setStatusError('Unable to update the status. Please try again.')
        } finally {
            setUpdatingStatus(false)
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
            setAssigneeError('Unable to update the assignee. Please try again.')
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

    return (
        <main className="details-page">
            <Link to="/">← Back to dashboard</Link>

            <p className="eyebrow">INCIDENT DETAILS</p>
            <h1>{incident.title}</h1>

            <p>
                INC-{String(incident.incidentNumber).padStart(3, '0')}
            </p>

            <section>
                <h2>Description</h2>
                <p>{incident.description}</p>

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
                            updatingStatus || selectedStatus === incident.status
                        }
                    >
                        {updatingStatus ? 'Updating...' : 'Update status'}
                    </button>
                </form>

                {statusMessage && (
                    <p className="status-message">{statusMessage}</p>
                )}
                {statusError && (
                    <p className="error-message">{statusError}</p>
                )}

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
                        {users.map((user) => (
                            <option key={user.id} value={user.id}>
                                {user.displayName}
                                {user.role !== 'USER'
                                    ? ` (${user.role.toLowerCase()})`
                                    : ''}
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

                {assigneeMessage && (
                    <p className="status-message">{assigneeMessage}</p>
                )}
                {(assigneeError || usersError) && (
                    <p className="error-message">{assigneeError || usersError}</p>
                )}

                {incident.resolvedAt && (
                    <p>
                        <strong>Resolved:</strong>{' '}
                        {new Date(incident.resolvedAt).toLocaleString()}
                    </p>
                )}

                <p>
                    <strong>Priority:</strong> {incident.priority}
                </p>

                <p>
                    <strong>Category:</strong> {incident.category}
                </p>
            </section>
            <button
                className="primary-button"
                type="button"
                onClick={handleTriage}
                disabled={triaging}
            >
                {triaging ? 'Running AI triage...' : 'Run AI triage'}
            </button>

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
                users={users}
                refreshKey={timelineKey}
            />
        </main>
    )
}

export default IncidentDetails