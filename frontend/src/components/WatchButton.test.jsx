import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import WatchButton from './WatchButton'
import IncidentsList from '../pages/IncidentsList'
import {
  getIncidentPage,
  getWatchers,
  stopWatchingIncident,
  watchIncident,
} from '../services/incidentApi'
import { USERS, renderPage } from '../test/render'

vi.mock('../services/incidentApi', async (importOriginal) => ({
  ...(await importOriginal()),
  getWatchers: vi.fn(),
  watchIncident: vi.fn(),
  stopWatchingIncident: vi.fn(),
  getIncidentPage: vi.fn(),
  getUnreadNotificationCount: vi.fn().mockResolvedValue(0),
}))

beforeEach(() => {
  getWatchers.mockReset()
  watchIncident.mockReset()
  stopWatchingIncident.mockReset()
  getIncidentPage.mockReset().mockResolvedValue({ content: [], totalElements: 0, totalPages: 0 })
  vi.spyOn(console, 'error').mockImplementation(() => {})
})

describe('WatchButton', () => {
  it('watches and stops watching', async () => {
    getWatchers.mockResolvedValue({ watching: false, count: 1, watchers: [{ id: 'u-1', displayName: 'Sam Patel' }] })
    watchIncident.mockResolvedValue({
      watching: true,
      count: 2,
      watchers: [
        { id: 'u-agent', displayName: 'Alex Agent' },
        { id: 'u-1', displayName: 'Sam Patel' },
      ],
    })
    stopWatchingIncident.mockResolvedValue({ watching: false, count: 1, watchers: [] })
    const user = userEvent.setup()
    render(<WatchButton incidentId="inc-1" />)

    const button = await screen.findByRole('button', { name: '☆ Watch' })
    expect(button).toHaveAttribute('aria-pressed', 'false')
    expect(screen.getByText('Watched by 1: Sam Patel')).toBeInTheDocument()

    await user.click(button)
    expect(watchIncident).toHaveBeenCalledWith('inc-1')
    expect(screen.getByRole('button', { name: '★ Watching' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByText('Watched by 2: Alex Agent, Sam Patel')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '★ Watching' }))
    expect(stopWatchingIncident).toHaveBeenCalledWith('inc-1')
    expect(await screen.findByRole('button', { name: '☆ Watch' })).toBeInTheDocument()
  })

  it('shows reporters only the count', async () => {
    getWatchers.mockResolvedValue({ watching: true, count: 3, watchers: null })
    render(<WatchButton incidentId="inc-1" />)

    expect(await screen.findByText('Watched by 3')).toBeInTheDocument()
  })

  it('says when watching could not be changed', async () => {
    getWatchers.mockResolvedValue({ watching: false, count: 0, watchers: [] })
    watchIncident.mockRejectedValue(
      Object.assign(new Error('Request failed'), {
        response: { status: 404, data: { message: 'Incident not found' } },
      })
    )
    const user = userEvent.setup()
    render(<WatchButton incidentId="inc-1" />)

    expect(await screen.findByText('No watchers')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '☆ Watch' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Incident not found')
  })
})

describe('Watching filter', () => {
  it('shows only watched incidents through the URL', async () => {
    const user = userEvent.setup()
    renderPage(<IncidentsList />, { currentUser: USERS.user, route: '/incidents' })
    await waitFor(() => expect(getIncidentPage).toHaveBeenCalledTimes(1))

    await user.click(screen.getByRole('checkbox', { name: 'Watching only' }))

    await waitFor(() =>
      expect(getIncidentPage).toHaveBeenLastCalledWith(expect.objectContaining({ watching: true }))
    )
    expect(screen.getByTestId('location')).toHaveTextContent('/incidents?watching=true')
    expect(await screen.findByText(/You're not watching any incidents/)).toBeInTheDocument()
  })
})
