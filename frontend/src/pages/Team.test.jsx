import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import Team from './Team'
import {
  deactivateUser,
  getUsers,
  getWorkload,
  reactivateUser,
  sendPasswordResetLink,
  updateUserDetails,
  unlockUser,
} from '../services/incidentApi'
import { USERS, renderPage } from '../test/render'

vi.mock('../services/incidentApi', async (importOriginal) => ({
  ...(await importOriginal()),
  getUsers: vi.fn(),
  getWorkload: vi.fn(),
  deactivateUser: vi.fn(),
  reactivateUser: vi.fn(),
  updateUserDetails: vi.fn(),
  getUnreadNotificationCount: vi.fn().mockResolvedValue(0),
  getPasswordResetAvailability: vi.fn().mockResolvedValue(true),
  getBusinessHours: vi.fn().mockResolvedValue({
    enabled: false,
    timeZone: 'UTC',
    dayStart: '09:00',
    dayEnd: '17:00',
    workingDays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'],
    priorities: ['LOW', 'MEDIUM', 'HIGH'],
    holidays: [],
  }),
  sendPasswordResetLink: vi.fn(),
  unlockUser: vi.fn(),
}))

const member = (overrides) => ({ active: true, deactivatedAt: null, ...overrides })

const TEAM = [
  member({ ...USERS.admin }),
  member({ ...USERS.agent }),
  member({
    id: 'u-gone',
    displayName: 'Gail Gone',
    email: 'gail@incidentiq.test',
    role: 'AGENT',
    active: false,
    deactivatedAt: '2026-01-01T00:00:00Z',
  }),
]

/** The member's row in the table (the sidebar also shows your own name). */
function rowFor(name) {
  return screen
    .getAllByText(name)
    .map((element) => element.closest('.team-row'))
    .find(Boolean)
}

describe('Team page member management', () => {
  beforeEach(() => {
    getUsers.mockReset().mockResolvedValue(TEAM)
    getWorkload.mockReset().mockResolvedValue([{ userId: 'u-agent', activeCount: 2 }])
    deactivateUser.mockReset()
    reactivateUser.mockReset()
    updateUserDetails.mockReset()
  })

  it('hides deactivated members until asked', async () => {
    const user = userEvent.setup()
    renderPage(<Team />, { currentUser: USERS.admin })

    expect(await screen.findByText('2 active members · 1 deactivated')).toBeInTheDocument()
    expect(screen.queryByText('Gail Gone')).not.toBeInTheDocument()

    await user.click(screen.getByRole('checkbox', { name: 'Show deactivated (1)' }))

    const row = rowFor('Gail Gone')
    expect(within(row).getByText('Deactivated')).toBeInTheDocument()
    expect(within(row).getByRole('button', { name: 'Reactivate Gail Gone' })).toBeInTheDocument()
    expect(within(row).queryByRole('button', { name: /Deactivate/ })).not.toBeInTheDocument()
  })

  it('confirms before deactivating and can return their incidents to the queue', async () => {
    deactivateUser.mockResolvedValue({
      user: member({ ...USERS.agent, active: false, deactivatedAt: '2026-01-05T09:00:00Z' }),
      unassignedIncidents: 2,
    })
    const user = userEvent.setup()
    renderPage(<Team />, { currentUser: USERS.admin })

    await user.click(await screen.findByRole('button', { name: 'Deactivate Alex Agent' }))

    const confirm = screen.getByRole('group', { name: 'Confirm deactivation' })
    const unassign = within(confirm).getByRole('checkbox', {
      name: /Also unassign their 2 open incidents/,
    })
    expect(unassign).toBeChecked()
    expect(deactivateUser).not.toHaveBeenCalled()

    await user.click(within(confirm).getByRole('button', { name: 'Deactivate' }))

    expect(deactivateUser).toHaveBeenCalledWith('u-agent', true)
    expect(
      await screen.findByText(
        'Alex Agent was deactivated and signed out. 2 incidents were returned to the queue.'
      )
    ).toBeInTheDocument()
    expect(within(rowFor('Alex Agent')).getByText('Deactivated')).toBeInTheDocument()
  })

  it('can deactivate without unassigning', async () => {
    deactivateUser.mockResolvedValue({
      user: member({ ...USERS.agent, active: false }),
      unassignedIncidents: 0,
    })
    const user = userEvent.setup()
    renderPage(<Team />, { currentUser: USERS.admin })

    await user.click(await screen.findByRole('button', { name: 'Deactivate Alex Agent' }))
    await user.click(screen.getByRole('checkbox', { name: /Also unassign/ }))
    await user.click(
      within(screen.getByRole('group', { name: 'Confirm deactivation' })).getByRole('button', {
        name: 'Deactivate',
      })
    )

    expect(deactivateUser).toHaveBeenCalledWith('u-agent', false)
  })

  it('reactivates a member', async () => {
    reactivateUser.mockResolvedValue(member({ ...TEAM[2], active: true, deactivatedAt: null }))
    const user = userEvent.setup()
    renderPage(<Team />, { currentUser: USERS.admin })

    await user.click(await screen.findByRole('checkbox', { name: /Show deactivated/ }))
    await user.click(screen.getByRole('button', { name: 'Reactivate Gail Gone' }))

    expect(reactivateUser).toHaveBeenCalledWith('u-gone')
    expect(await screen.findByText('Gail Gone can sign in again.')).toBeInTheDocument()
    expect(within(rowFor('Gail Gone')).queryByText('Deactivated')).not.toBeInTheDocument()
  })

  it('edits only the details that changed', async () => {
    updateUserDetails.mockResolvedValue(member({ ...USERS.agent, displayName: 'Alexandra Agent' }))
    const user = userEvent.setup()
    renderPage(<Team />, { currentUser: USERS.admin })

    await user.click(await screen.findByRole('button', { name: 'Edit Alex Agent' }))
    const name = screen.getByLabelText('Name')
    await user.clear(name)
    await user.type(name, 'Alexandra Agent')
    await user.click(screen.getByRole('button', { name: 'Save details' }))

    expect(updateUserDetails).toHaveBeenCalledWith('u-agent', { displayName: 'Alexandra Agent' })
    expect(await screen.findByText('Alexandra Agent')).toBeInTheDocument()
    expect(screen.getByText('Details saved.')).toBeInTheDocument()
  })

  it("doesn't offer to deactivate yourself, and non-admins can't manage", async () => {
    const { unmount } = renderPage(<Team />, { currentUser: USERS.admin })
    await screen.findByText('2 active members · 1 deactivated')
    const me = rowFor('Ada Admin')
    expect(within(me).queryByRole('button', { name: /Deactivate/ })).not.toBeInTheDocument()
    expect(within(me).getByRole('link', { name: 'Change password' })).toBeInTheDocument()
    unmount()

    renderPage(<Team />, { currentUser: USERS.agent })
    await screen.findByText('2 active members · 1 deactivated')
    expect(screen.queryByRole('button', { name: /Deactivate/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Edit / })).not.toBeInTheDocument()
  })

  it('can email a member a reset link instead of setting a password', async () => {
    sendPasswordResetLink.mockReset().mockResolvedValue()
    const user = userEvent.setup()
    renderPage(<Team />, { currentUser: USERS.admin })

    await user.click(
      await screen.findByRole('button', { name: 'Reset password for Alex Agent' })
    )
    await user.click(
      await screen.findByRole('button', { name: 'Email Alex Agent a reset link' })
    )

    expect(sendPasswordResetLink).toHaveBeenCalledWith('u-agent')
    expect(
      await screen.findByText(
        'Reset link sent to alex@incidentiq.test. It works once and expires in 30 minutes.'
      )
    ).toBeInTheDocument()
  })
})

describe('Team page sign-in lockout', () => {
  const inAWhile = new Date(Date.now() + 10 * 60 * 1000).toISOString()
  const earlier = new Date(Date.now() - 60 * 1000).toISOString()

  beforeEach(() => {
    getWorkload.mockReset().mockResolvedValue([])
    unlockUser.mockReset()
  })

  it('shows a locked member and lets an admin unlock them', async () => {
    getUsers.mockReset().mockResolvedValue([member({ ...USERS.admin }), member({ ...USERS.agent, lockedUntil: inAWhile })])
    unlockUser.mockResolvedValue(member({ ...USERS.agent, lockedUntil: null }))
    const user = userEvent.setup()
    renderPage(<Team />, { currentUser: USERS.admin })

    await screen.findByText('Alex Agent')
    const row = rowFor('Alex Agent')
    expect(within(row).getByText(/^Locked until/)).toBeInTheDocument()
    await user.click(within(row).getByRole('button', { name: 'Unlock Alex Agent' }))

    expect(unlockUser).toHaveBeenCalledWith('u-agent')
    expect(await screen.findByText('Alex Agent can sign in again.')).toBeInTheDocument()
    expect(within(rowFor('Alex Agent')).queryByText(/^Locked until/)).not.toBeInTheDocument()
  })

  it('ignores locks that have already run out', async () => {
    getUsers.mockReset().mockResolvedValue([member({ ...USERS.admin }), member({ ...USERS.agent, lockedUntil: earlier })])
    renderPage(<Team />, { currentUser: USERS.admin })

    await screen.findByText('Alex Agent')
    expect(within(rowFor('Alex Agent')).queryByText(/^Locked until/)).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Unlock Alex Agent' })).not.toBeInTheDocument()
  })

  it('shows agents the lock without the Unlock button', async () => {
    getUsers.mockReset().mockResolvedValue([member({ ...USERS.admin, lockedUntil: inAWhile }), member({ ...USERS.agent })])
    renderPage(<Team />, { currentUser: USERS.agent })

    await screen.findByText('Ada Admin')
    expect(within(rowFor('Ada Admin')).getByText(/^Locked until/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Unlock/ })).not.toBeInTheDocument()
  })
})
