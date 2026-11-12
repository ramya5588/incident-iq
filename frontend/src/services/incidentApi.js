import axios from 'axios'

const api = axios.create({
    baseURL: '/api',
    headers: {
        'Content-Type': 'application/json',
    },
})

export async function getIncidents() {
    const response = await api.get('/incidents')
    return response.data.content ?? []
}
export async function getIncidentPage({ status, priority, page = 0, size = 10 } = {}) {
    const params = { page, size }
    if (status) params.status = status
    if (priority) params.priority = priority

    const response = await api.get('/incidents', { params })
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

export async function getUsers() {
    const response = await api.get('/users')
    return response.data
}

export async function updateIncidentStatus(id, status) {
    const response = await api.patch(`/incidents/${id}/status`, { status })
    return response.data
}