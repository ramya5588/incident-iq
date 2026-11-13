import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import NotificationBell from './NotificationBell'
import {
  getNotifications,
  getUnreadNotificationCount,
  markAllNotificationsRead,
  markNotificationRead,
} from '../services/incidentApi'
import { renderPage } from '../test/render'

vi.mock('../services/incidentApi', async (importOriginal) => ({
  ...(await importOriginal()),
  getNotifications: vi.fn(),
  getUnreadNotificationCount: vi.fn(),
  markNotificationRead: vi.fn(),
  markAllNotificationsRead: vi.fn(),
}))

const minutesAgo = (minutes) => new Date(Date.now() - minutes * 60000).toISOString()

const NOTIFICATIONS = [
  {
    id: 3,
    type: 'ASSIGNED',
    message: 'Ada Admin assigned you INC-007 “VPN drops”',
    incidentId: 'inc-7',
    createdAt: minutesAgo(5),
    readAt: null,
  },
  {
    id: 2,
    type: 'SLA_BREACHED',
    message: 'INC-003 “Disk full” missed its response deadline',
    incidentId: 'inc-3',
    createdAt: minutesAgo(120),
    readAt: null,
  },
  {
    id: 1,
    type: 'COMMENTED',
    message: 'Riley Reporter commented on INC-001 “Login broken”',
    incidentId: 'inc-1',
    createdAt: minutesAgo(3000),
    readAt: minutesAgo(2000),
  },
]

const location = () => screen.getByTestId('location').textContent

describe('NotificationBell', () => {
  beforeEach(() => {
    getUnreadNotificationCount.mockReset().mockResolvedValue(2)
    getNotifications.mockReset().mockResolvedValue(NOTIFICATIONS)
    markNotificationRead.mockReset().mockResolvedValue()
    markAllNotificationsRead.mockReset().mockResolvedValue()
  })

  it('shows the unread count', async () => {
    renderPage(<NotificationBell />)

    expect(
      await screen.findByRole('button', { name: 'Notifications, 2 unread' })
    ).toHaveTextContent('2')
  })

  it('lists notifications newest first, unread marked', async () => {
    const user = userEvent.setup()
    renderPage(<NotificationBell />)

    await user.click(await screen.findByRole('button', { name: /Notifications/ }))

    const panel = await screen.findByRole('dialog', { name: 'Notifications' })
    const items = within(panel).getAllByRole('listitem')
    expect(items).toHaveLength(3)
    expect(items[0]).toHaveTextContent('Ada Admin assigned you INC-007 “VPN drops”')
    expect(items[0]).toHaveTextContent('5m ago')
    expect(items[1]).toHaveTextContent('2h ago')
    expect(within(panel).getAllByLabelText('Unread')).toHaveLength(2)
  })

  it('opens the incident and marks the notification read', async () => {
    const user = userEvent.setup()
    renderPage(<NotificationBell />, { route: '/' })

    await user.click(await screen.findByRole('button', { name: /Notifications/ }))
    await user.click(await screen.findByText(/missed its response deadline/))

    expect(markNotificationRead).toHaveBeenCalledWith(2)
    await waitFor(() => expect(location()).toBe('/incidents/inc-3'))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Notifications, 1 unread' })).toBeInTheDocument()
  })

  it('does not mark an already-read notification again', async () => {
    const user = userEvent.setup()
    renderPage(<NotificationBell />)

    await user.click(await screen.findByRole('button', { name: /Notifications/ }))
    await user.click(await screen.findByText(/commented on INC-001/))

    expect(markNotificationRead).not.toHaveBeenCalled()
    await waitFor(() => expect(location()).toBe('/incidents/inc-1'))
  })

  it('marks everything as read', async () => {
    const user = userEvent.setup()
    renderPage(<NotificationBell />)

    await user.click(await screen.findByRole('button', { name: /Notifications/ }))
    await user.click(await screen.findByRole('button', { name: 'Mark all as read' }))

    expect(markAllNotificationsRead).toHaveBeenCalled()
    expect(screen.queryAllByLabelText('Unread')).toHaveLength(0)
    expect(screen.queryByRole('button', { name: 'Mark all as read' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Notifications, none unread' })).toBeInTheDocument()
  })

  it('explains when there is nothing yet and closes on Escape', async () => {
    getUnreadNotificationCount.mockResolvedValue(0)
    getNotifications.mockResolvedValue([])
    const user = userEvent.setup()
    renderPage(<NotificationBell />)

    await user.click(await screen.findByRole('button', { name: /Notifications/ }))
    expect(await screen.findByText(/You're all caught up/)).toBeInTheDocument()

    await user.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})
