import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
    getIncidentById,
    triageIncident,
    updateIncidentStatus,
} from '../services/incidentApi'

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

    useEffect(() => {
        async function loadIncident() {
            try {
                const data = await getIncidentById(id)
                setIncident(data)
                setSelectedStatus(data.status)
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
        } catch (requestError) {
            console.error(requestError)
            setStatusError('Unable to update the status. Please try again.')
        } finally {
            setUpdatingStatus(false)
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
                <section>
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
        </main>
    )
}

export default IncidentDetails