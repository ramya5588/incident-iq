import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  getNotifications,
  getUnreadNotificationCount,
  markAllNotificationsRead,
  markNotificationRead,
} from '../services/incidentApi'
import { formatDuration } from '../utils/sla'

/** How often to check for new notifications while the app is open. */
const POLL_INTERVAL_MS = 30000

const TYPE_ICONS = {
  ASSIGNED: '👤',
  COMMENTED: '💬',
  MENTIONED: '@',
  STATUS_CHANGED: '🔄',
  SLA_BREACHED: '⏰',
}

function timeAgo(value, now) {
  const elapsed = now - Date.parse(value)
  return elapsed < 60000 ? 'just now' : `${formatDuration(elapsed)} ago`
}

/**
 * Bell with an unread count; opens a panel of recent notifications.
 * Clicking one marks it read and opens its incident.
 */
function NotificationBell() {
  const navigate = useNavigate()
  const [unread, setUnread] = useState(0)
  const [open, setOpen] = useState(false)
  const [items, setItems] = useState(null)
  const [error, setError] = useState('')
  const [now, setNow] = useState(() => Date.now())
  const containerRef = useRef(null)

  // Keep the count fresh: on load, every 30s, and when the tab regains focus.
  useEffect(() => {
    let cancelled = false

    async function refreshCount() {
      try {
        const count = await getUnreadNotificationCount()
        if (!cancelled) setUnread(count)
      } catch (requestError) {
        console.error(requestError)
      }
    }

    refreshCount()
    const timer = setInterval(refreshCount, POLL_INTERVAL_MS)
    window.addEventListener('focus', refreshCount)

    return () => {
      cancelled = true
      clearInterval(timer)
      window.removeEventListener('focus', refreshCount)
    }
  }, [])

  // Close on Escape or a click outside the bell and panel.
  useEffect(() => {
    if (!open) return undefined

    function handleKeyDown(event) {
      if (event.key === 'Escape') setOpen(false)
    }
    function handleMouseDown(event) {
      if (!containerRef.current?.contains(event.target)) setOpen(false)
    }

    window.addEventListener('keydown', handleKeyDown)
    window.addEventListener('mousedown', handleMouseDown)
    return () => {
      window.removeEventListener('keydown', handleKeyDown)
      window.removeEventListener('mousedown', handleMouseDown)
    }
  }, [open])

  async function togglePanel() {
    if (open) {
      setOpen(false)
      return
    }

    setOpen(true)
    setError('')
    setNow(Date.now())
    try {
      const list = await getNotifications()
      setItems(list)
      setUnread(list.filter((item) => !item.readAt).length)
    } catch (requestError) {
      console.error(requestError)
      setError('Unable to load notifications.')
    }
  }

  async function openNotification(item) {
    setOpen(false)
    if (!item.readAt) {
      setItems((current) =>
        current?.map((existing) =>
          existing.id === item.id
            ? { ...existing, readAt: new Date().toISOString() }
            : existing
        )
      )
      setUnread((count) => Math.max(count - 1, 0))
      try {
        await markNotificationRead(item.id)
      } catch (requestError) {
        console.error(requestError)
      }
    }
    navigate(`/incidents/${item.incidentId}`)
  }

  async function markAllRead() {
    try {
      await markAllNotificationsRead()
      const readAt = new Date().toISOString()
      setItems((current) =>
        current?.map((item) => (item.readAt ? item : { ...item, readAt }))
      )
      setUnread(0)
    } catch (requestError) {
      console.error(requestError)
      setError('Unable to mark notifications as read.')
    }
  }

  const label =
    unread > 0
      ? `Notifications, ${unread} unread`
      : 'Notifications, none unread'

  return (
    <div className="notification-bell" ref={containerRef}>
      <button
        type="button"
        className="bell-button"
        aria-label={label}
        aria-expanded={open}
        aria-haspopup="dialog"
        title={label}
        onClick={togglePanel}
      >
        <span aria-hidden="true">🔔</span>
        {unread > 0 && (
          <span className="bell-count" aria-hidden="true">
            {unread > 99 ? '99+' : unread}
          </span>
        )}
      </button>

      {open && (
        <section
          className="notification-panel"
          role="dialog"
          aria-label="Notifications"
        >
          <header className="notification-panel-header">
            <strong>Notifications</strong>
            {items?.some((item) => !item.readAt) && (
              <button
                type="button"
                className="link-button"
                onClick={markAllRead}
              >
                Mark all as read
              </button>
            )}
          </header>

          {error && <p className="error-message">{error}</p>}
          {!error && items === null && (
            <p className="notification-empty">Loading…</p>
          )}
          {!error && items?.length === 0 && (
            <p className="notification-empty">
              You're all caught up. Assignments, comments, status changes and
              missed SLAs will show up here.
            </p>
          )}

          {items?.length > 0 && (
            <ul className="notification-list">
              {items.map((item) => (
                <li key={item.id}>
                  <button
                    type="button"
                    className={`notification-item ${item.readAt ? '' : 'unread'}`}
                    onClick={() => openNotification(item)}
                  >
                    <span className="notification-icon" aria-hidden="true">
                      {TYPE_ICONS[item.type] ?? '•'}
                    </span>
                    <span className="notification-text">
                      <span>{item.message}</span>
                      <time dateTime={item.createdAt}>
                        {timeAgo(item.createdAt, now)}
                      </time>
                    </span>
                    {!item.readAt && (
                      <span className="unread-dot" aria-label="Unread" />
                    )}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </section>
      )}
    </div>
  )
}

export default NotificationBell
