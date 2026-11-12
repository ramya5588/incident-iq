import axios from 'axios'

const api = axios.create({
    baseURL: '/api',
    headers: {
        'Content-Type': 'application/json',
    },
})

// Who changes are made as ("Acting as" in the sidebar). Sent as X-User-Id
// so the backend can attribute timeline events. Not authentication.
let actingUserId = null

export function setActingUserId(userId) {
    actingUserId = userId || null
}

api.interceptors.request.use((config) => {
    if (actingUserId) {
        config.headers['X-User-Id'] = actingUserId
    }
    return config
})

export async function getIncidentPage({ status, priority, page = 0, size = 10 } = {}) {
    const params = { page, size }
    if (status) params.status = status
    if (priority) params.priority = priority

    const response = await api.get('/incidents', { params })
    return response.data
}

export async function getIncidentStats() {
    const startOfToday = new Date()
    startOfToday.setHours(0, 0, 0, 0)

    const response = await api.get('/incidents/stats', {
        params: { resolvedSince: startOfToday.toISOString() },
    })
    return response.data
}

export async function getIncidentById(id) {
    const response = await api.get(`/incidents/${id}`)
    return response.data
}

export async function createIncident(incident) {
    const response = await api.post('/incidents', incident)
    return response.data
}

/** Partial edit: pass only the fields to change ('' category clears it). */
export async function updateIncident(id, changes) {
    const response = await api.patch(`/incidents/${id}`, changes)
    return response.data
}

export async function triageIncident(id) {
    const response = await api.post(`/incidents/${id}/triage`)
    return response.data
}

export async function updateIncidentAssignee(id, assigneeId) {
    const response = await api.patch(`/incidents/${id}/assignee`, {
        assigneeId: assigneeId || null,
    })
    return response.data
}

export async function getIncidentEvents(id) {
    const response = await api.get(`/incidents/${id}/events`)
    return response.data
}

export async function addIncidentComment(id, authorId, body) {
    const response = await api.post(`/incidents/${id}/comments`, {
        authorId,
        body,
    })
    return response.data
}

export async function getUsers() {
    const response = await api.get('/users')
    return response.data
}

export async function createUser(user) {
    const response = await api.post('/users', user)
    return response.data
}

export async function getWorkload() {
    const response = await api.get('/incidents/workload')
    return response.data
}

/**
 * Turns an API error into a readable message, using the backend's
 * message and per-field validation details when present.
 */
export function describeApiError(requestError, fallback) {
    const data = requestError?.response?.data
    if (data?.details && Object.keys(data.details).length > 0) {
        return Object.values(data.details).join(' ')
    }
    if (data?.message && requestError.response.status < 500) {
        return data.message
    }
    return fallback
}

export async function updateIncidentStatus(id, status) {
    const response = await api.patch(`/incidents/${id}/status`, { status })
    return response.data
}