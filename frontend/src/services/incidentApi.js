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
/** Whether "Forgot password?" can be offered (the server has email set up). */
export async function getPasswordResetAvailability() {
    const response = await api.get('/auth/password-reset')
    return response.data.available
}

/** Emails a reset link if the account exists. Returns the (same either way) message. */
export async function requestPasswordReset(email) {
    const response = await api.post('/auth/password-reset', { email })
    return response.data.message
}

/** Resolves if the reset link can still be used; rejects (400) if not. */
export async function checkPasswordResetToken(token) {
    await api.post('/auth/password-reset/check', { token })
}

export async function confirmPasswordReset(token, newPassword) {
    await api.post('/auth/password-reset/confirm', { token, newPassword })
}

/** Admin only: emails the member a link to choose their own password. */
export async function sendPasswordResetLink(userId) {
    await api.post(`/users/${userId}/password-reset-link`)
}

/** Change your own display name. Returns the updated user. */
export async function updateOwnName(displayName) {
    const response = await api.patch('/auth/profile', { displayName })
    return response.data
}

/**
 * Change your sign-in email. Returns { user, pending }: `pending`
 * ({ newEmail, expiresAt }) when it must be confirmed from the new address,
 * null when it applied straight away.
 */
export async function requestEmailChange(newEmail, currentPassword) {
    const response = await api.post('/auth/email-change', { newEmail, currentPassword })
    return response.data
}

/** The change waiting for confirmation, or null. */
export async function getPendingEmailChange() {
    const response = await api.get('/auth/email-change')
    return response.status === 204 ? null : response.data
}

export async function cancelEmailChange() {
    await api.delete('/auth/email-change')
}

/** Confirms from the emailed link. Returns the new email. */
export async function confirmEmailChange(token) {
    const response = await api.post('/auth/email-change/confirm', { token })
    return response.data.email
}

export async function changeOwnPassword(currentPassword, newPassword) {
    await api.put('/auth/password', { currentPassword, newPassword })
}

/** Which notifications you also get by email, plus `emailEnabled` (server has mail set up). */
export async function getEmailPreferences() {
    const response = await api.get('/notifications/email-preferences')
    return response.data
}

/** `preferences`: { assigned, commented, mentioned, statusChanged, slaBreached }. */
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
/** Admin: lift a sign-in lockout. Returns the member. */
export async function unlockUser(userId) {
    const response = await api.post(`/users/${userId}/unlock`)
    return response.data
}

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
    watching = false,
    page = 0,
    size = 10,
} = {}) {
    const params = { page, size }
    if (overdue) params.overdue = true
    if (watching) params.watching = true
    if (status) params.status = status
    if (priority) params.priority = priority
    if (q) params.q = q

    const response = await api.get('/incidents', { params })
    return response.data
}

/** Agents and admins: { dueSoon, waiting, unassigned, unassignedTotal, unread }. */
export async function getMyWork() {
    const response = await api.get('/dashboard/my-work')
    return response.data
}

/** The incidents you reported, most recently updated first. */
export async function getMyIncidents() {
    const response = await api.get('/dashboard/my-incidents')
    return response.data
}

/** [{ date, open }] for the last `days` days, in the browser's time zone. */
export async function getOpenTrend(days = 30) {
    const response = await api.get('/reports/open-trend', {
        params: { days, timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone },
    })
    return response.data
}

/** Closes an incident as a duplicate of `mainIncidentId`. Returns the incident. */
export async function markDuplicate(incidentId, mainIncidentId) {
    const response = await api.put(`/incidents/${incidentId}/duplicate-of`, { mainIncidentId })
    return response.data
}

/** No longer a duplicate; it's reopened. Returns the incident. */
export async function undoDuplicate(incidentId) {
    const response = await api.delete(`/incidents/${incidentId}/duplicate-of`)
    return response.data
}

/** The incidents merged into this one: [{ id, incidentNumber, title }]. */
export async function getDuplicates(incidentId) {
    const response = await api.get(`/incidents/${incidentId}/duplicates`)
    return response.data
}

/** Shared replies, then your personal ones (agents and admins). */
export async function listSavedReplies() {
    const response = await api.get('/saved-replies')
    return response.data
}

/** { title, body, personal } — only admins can add shared (personal: false) ones. */
export async function createSavedReply(reply) {
    const response = await api.post('/saved-replies', reply)
    return response.data
}

export async function updateSavedReply(id, { title, body }) {
    const response = await api.put(`/saved-replies/${id}`, { title, body })
    return response.data
}

export async function deleteSavedReply(id) {
    await api.delete(`/saved-replies/${id}`)
}

/** The reporter rates a resolved incident: { score: 1-5, comment }. Returns the incident. */
export async function rateIncident(incidentId, { score, comment }) {
    const response = await api.put(`/incidents/${incidentId}/rating`, { score, comment })
    return response.data
}

/** { watching, count, watchers } (watchers is null for reporters). */
export async function getWatchers(incidentId) {
    const response = await api.get(`/incidents/${incidentId}/watchers`)
    return response.data
}

export async function watchIncident(incidentId) {
    const response = await api.put(`/incidents/${incidentId}/watchers/me`)
    return response.data
}

export async function stopWatchingIncident(incidentId) {
    const response = await api.delete(`/incidents/${incidentId}/watchers/me`)
    return response.data
}

export async function listAttachments(incidentId) {
    const response = await api.get(`/incidents/${incidentId}/attachments`)
    return response.data
}

/** Uploads one file; returns the new attachment. */
export async function uploadAttachment(incidentId, file) {
    const form = new FormData()
    form.append('file', file)
    // Multipart, not the JSON default; the browser adds the boundary.
    const response = await api.post(`/incidents/${incidentId}/attachments`, form, {
        headers: { 'Content-Type': 'multipart/form-data' },
    })
    return response.data
}

export async function deleteAttachment(incidentId, attachmentId) {
    await api.delete(`/incidents/${incidentId}/attachments/${attachmentId}`)
}

/** Where the file itself is served (images inline, others as downloads). */
export function attachmentUrl(incidentId, attachmentId) {
    return `/api/incidents/${incidentId}/attachments/${attachmentId}`
}

function reportParams({ from, to, timeZone, priority }) {
    const params = new URLSearchParams({ from, to, timeZone })
    if (priority) params.set('priority', priority)
    return params
}

/** Agents and admins: the report for a date range (inclusive, in timeZone). */
export async function getReportSummary(options) {
    const response = await api.get(`/reports/summary?${reportParams(options)}`)
    return response.data
}

/** Link that downloads the range's incidents as CSV (uses the session cookie). */
export function reportCsvUrl(options) {
    return `/api/reports/incidents.csv?${reportParams(options)}`
}

/** SLA business hours (anyone signed in can read them). */
export async function getBusinessHours() {
    const response = await api.get('/sla/business-hours')
    return response.data
}

/** Admin only. Takes and returns the same shape as getBusinessHours. */
export async function saveBusinessHours(settings) {
    const response = await api.put('/sla/business-hours', settings)
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

/**
 * The same change for several incidents: { incidentIds, status?, holdReason?,
 * holdNote?, priority?, assigneeId?, unassign? }. Returns { updated, failed }.
 */
export async function bulkUpdateIncidents(changes) {
    const response = await api.patch('/incidents/bulk', changes)
    return response.data
}

export async function getIncidentEvents(id) {
    const response = await api.get(`/incidents/${id}/events`)
    return response.data
}

/**
 * Posted as the signed-in user. `internal`: an internal note only agents and
 * admins can see.
 */
export async function addIncidentComment(id, body, { internal = false } = {}) {
    const response = await api.post(`/incidents/${id}/comments`, { body, internal })
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