// @mentions. The comment box shows "@Sam Patel"; what's sent and stored is
// "@[Sam Patel](user-id)", so a mention keeps pointing at the right person.
// The server checks every mention and uses the real name.

const MENTION = /@\[([^\]\n]{1,100})\]\(([0-9a-fA-F-]{36})\)/g

/** Most suggestions shown at once. */
const MAX_SUGGESTIONS = 6

/** Splits stored text into plain parts and mentions, for display. */
export function splitMentions(text) {
  const parts = []
  let last = 0
  for (const match of text.matchAll(MENTION)) {
    if (match.index > last) parts.push({ text: text.slice(last, match.index) })
    parts.push({ mention: { name: match[1], id: match[2] } })
    last = match.index + match[0].length
  }
  if (last < text.length) parts.push({ text: text.slice(last) })
  return parts
}

/**
 * The "@query" being typed just before the caret, or null. The @ must start
 * the text or follow a space or new line, so emails don't trigger it.
 */
export function mentionQueryAt(text, caret) {
  const before = text.slice(0, caret)
  const at = before.lastIndexOf('@')
  if (at < 0) return null
  if (at > 0 && !/\s/.test(before[at - 1])) return null
  const query = before.slice(at + 1)
  if (query.length > 40 || /[\n@]/.test(query) || query.startsWith(' ')) return null
  return { start: at, query }
}

/**
 * Who can be mentioned here: active agents and admins, and (in a reply, not
 * an internal note) the incident's reporter. Never yourself.
 */
export function mentionSuggestions(users, query, { internal, reporterId, currentUserId }) {
  const wanted = query.trim().toLowerCase()
  return users
    .filter((user) => user.active !== false && user.id !== currentUserId)
    .filter((user) => user.role !== 'USER' || (!internal && user.id === reporterId))
    .filter((user) => {
      if (!wanted) return true
      const name = user.displayName.toLowerCase()
      return name.startsWith(wanted) || name.split(/\s+/).some((word) => word.startsWith(wanted))
    })
    .sort((a, b) => a.displayName.localeCompare(b.displayName))
    .slice(0, MAX_SUGGESTIONS)
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

/**
 * Turns "@Sam Patel" back into a stored mention for each person picked from
 * the suggestions (if their name is still in the text). Longer names first,
 * so "@Sam Patel" wins over "@Sam".
 */
export function withMentionMarkup(text, picked) {
  const people = [...picked].sort((a, b) => b.displayName.length - a.displayName.length)
  let result = text
  for (const person of people) {
    const name = person.displayName.replace(/[\]\n]/g, '')
    const pattern = new RegExp(`(^|\\s)@${escapeRegExp(person.displayName)}(?![\\p{L}\\p{N}_\\]])`, 'gu')
    result = result.replace(pattern, (whole, space) => `${space}@[${name}](${person.id})`)
  }
  return result
}
