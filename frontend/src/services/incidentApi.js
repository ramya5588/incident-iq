import axios from 'axios'

const api = axios.create({
    baseURL: '/api',
    headers: {
        'Content-Type': 'application/json',
    },
})

// Sign-in uses a session cookie. CSRF: the backend sets an XSRF-TOKEN cookie
// and axios echoes it in the X-XSRF-TOKEN header on same-origin requests
// (its defaults), so nothing extra is needed per call.

let unauthorizedHandler = null

/** Called when any non-auth request comes back 401 (session expired etc.). */
export function onUnauthorized(handler) {
    unauthorizedHandler = handler
}

api.interceptors.response.use(
    (response) => response,
    (error) => {
        const isAuthCall = error.config?.url?.startsWith('/auth/')
        if (error.response?.status === 401 && !isAuthCall && unauthorizedHandler) {
            unauthorizedHandler()
        }
        return Promise.reject(error)
    }
)

/** The signed-in user, or null when signed out. */
export async function getCurrentUser() {
    try {
        const response = await api.get('/auth/me')
        return response.data
    } catch (error) {
        if (error.response?.status === 401) return null
        throw error
    }
}

export async function signIn(email, password) {
    const response = await api.post('/auth/login', { email, password })
    return response.data
}

export async function signOut() {
    await api.post('/auth/logout')
}

/** Your most recent notifications, newest first. */
export async function getNotifications() {
    const response = await api.get('/notifications')
    return response.data
}

export async function getUnreadNotificationCount() {
    const response = await api.get('/notifications/unread-count')
    return response.data.unread
}

export async function markNotificationRead(id) {
    await api.post(`/notifications/${id}/read`)
}

export async function markAllNotificationsRead() {
    await api.post('/notifications/read-all')
}

/** Change your own password. Other sessions of your account are signed out. */
export async function changeOwnPassword(currentPassword, newPassword) {
    await api.put('/auth/password', { currentPassword, newPassword })
}

/** Which notifications you also get by email, plus `emailEnabled` (server has mail set up). */
export async function getEmailPreferences() {
    const response = await api.get('/notifications/email-preferences')
    return response.data
}

/** `preferences`: { assigned, commented, statusChanged, slaBreached }. */
export async function saveEmailPreferences(preferences) {
    const response = await api.put('/notifications/email-preferences', preferences)
    return response.data
}

/** Admin only. Pass only the fields to change ({ displayName, email }). */
export async function updateUserDetails(userId, changes) {
    const response = await api.patch(`/users/${userId}`, changes)
    return response.data
}

/** Admin only. Returns { user, unassignedIncidents }. */
export async function deactivateUser(userId, unassignOpenIncidents) {
    const response = await api.post(`/users/${userId}/deactivate`, {
        unassignOpenIncidents,
    })
    return response.data
}

/** Admin only. */
export async function reactivateUser(userId) {
    const response = await api.post(`/users/${userId}/reactivate`)
    return response.data
}

/** Admin only. */
export async function changeUserRole(userId, role) {
    const response = await api.patch(`/users/${userId}/role`, { role })
    return response.data
}

/** Admin only. The member's existing sessions are signed out. */
export async function resetUserPassword(userId, newPassword) {
    await api.put(`/users/${userId}/password`, { newPassword })
}

export async function getIncidentPage({
    status,
    priority,
    q,
    overdue = false,
    page = 0,
    size = 10,
} = {}) {
    const params = { page, size }
    if (overdue) params.overdue = true
    if (status) params.status = status
    if (priority) params.priority = priority
    if (q) params.q = q

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

/** Posted as the signed-in user. */
export async function addIncidentComment(id, body) {
    const response = await api.post(`/incidents/${id}/comments`, { body })
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

/** `hold` = { holdReason, holdNote } when the status is ON_HOLD. */
export async function updateIncidentStatus(id, status, hold = {}) {
    const response = await api.patch(`/incidents/${id}/status`, { status, ...hold })
    return response.data
}