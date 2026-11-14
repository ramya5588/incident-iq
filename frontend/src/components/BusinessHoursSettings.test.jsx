import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import BusinessHoursSettings from './BusinessHoursSettings'
import { describeBusinessHours } from '../utils/businessHours'
import { getBusinessHours, saveBusinessHours } from '../services/incidentApi'

vi.mock('../services/incidentApi', async (importOriginal) => ({
  ...(await importOriginal()),
  getBusinessHours: vi.fn(),
  saveBusinessHours: vi.fn(),
}))

const OFF = {
  enabled: false,
  timeZone: 'UTC',
  dayStart: '09:00',
  dayEnd: '17:00',
  workingDays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'],
  priorities: ['LOW', 'MEDIUM', 'HIGH'],
  holidays: [],
}

const ON = {
  ...OFF,
  enabled: true,
  timeZone: 'Europe/London',
  holidays: [{ date: '2026-12-25', name: 'Christmas Day' }],
}

beforeEach(() => {
  getBusinessHours.mockReset().mockResolvedValue(OFF)
  saveBusinessHours.mockReset()
})

describe('describeBusinessHours', () => {
  it('summarises the settings in one line', () => {
    expect(describeBusinessHours(OFF)).toBe('SLA deadlines run around the clock.')
    expect(describeBusinessHours(ON)).toBe(
      'High, Medium, Low deadlines count business hours only: Mon–Fri 09:00–17:00 (Europe/London), except 1 holiday.'
    )
    expect(
      describeBusinessHours({ ...ON, workingDays: ['SUNDAY', 'WEDNESDAY'], priorities: ['CRITICAL'], holidays: [] })
    ).toBe('Critical deadlines count business hours only: Wed, Sun 09:00–17:00 (Europe/London).')
    expect(describeBusinessHours({ ...ON, priorities: [] })).toBe('SLA deadlines run around the clock.')
  })
})

describe('BusinessHoursSettings', () => {
  it('shows everyone else a summary only', async () => {
    getBusinessHours.mockResolvedValue(ON)
    render(<BusinessHoursSettings admin={false} />)

    expect(await screen.findByText(/count business hours only/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Save business hours' })).not.toBeInTheDocument()
  })

  it('lets admins turn business hours on and set them up', async () => {
    saveBusinessHours.mockImplementation(async (settings) => settings)
    const user = userEvent.setup()
    render(<BusinessHoursSettings admin />)

    const enable = await screen.findByRole('checkbox', { name: /Count only business hours/ })
    // The details wait until business hours are on.
    expect(screen.getByLabelText('Time zone')).toBeDisabled()
    await user.click(enable)

    const zone = screen.getByLabelText('Time zone')
    await user.clear(zone)
    await user.type(zone, 'Europe/London')
    await user.click(
      within(screen.getByRole('group', { name: 'Priorities using business hours' })).getByLabelText(
        'Low'
      )
    )
    await user.click(within(screen.getByRole('group', { name: 'Working days' })).getByLabelText('Sat'))
    await user.click(screen.getByRole('button', { name: 'Add holiday' }))
    await user.type(screen.getByLabelText('Holiday 1 date'), '2026-12-25')
    await user.type(screen.getByLabelText('Holiday 1 name'), 'Christmas Day')
    await user.click(screen.getByRole('button', { name: 'Save business hours' }))

    expect(saveBusinessHours).toHaveBeenCalledWith({
      ...OFF,
      enabled: true,
      timeZone: 'Europe/London',
      workingDays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'],
      priorities: ['MEDIUM', 'HIGH'],
      holidays: [{ date: '2026-12-25', name: 'Christmas Day' }],
    })
    expect(await screen.findByText(/Business hours saved/)).toBeInTheDocument()
    expect(screen.getByText(/High, Medium deadlines count business hours only/)).toBeInTheDocument()
  })

  it('shows why the server refused the settings', async () => {
    getBusinessHours.mockResolvedValue(ON)
    saveBusinessHours.mockRejectedValue(
      Object.assign(new Error('Request failed'), {
        response: { status: 400, data: { message: 'Unknown time zone: Mars/Olympus' } },
      })
    )
    vi.spyOn(console, 'error').mockImplementation(() => {})
    const user = userEvent.setup()
    render(<BusinessHoursSettings admin />)

    await user.click(await screen.findByRole('button', { name: 'Save business hours' }))

    expect(await screen.findByText('Unknown time zone: Mars/Olympus')).toBeInTheDocument()
  })
})
