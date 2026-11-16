// Saved replies can use {reporter}, {incident} and {agent}; they're filled in
// when the reply is inserted, so the text can still be edited before sending.

export const PLACEHOLDERS = ['{reporter}', '{incident}', '{agent}']

/** Replaces the placeholders it has values for; unknown ones are left as typed. */
export function fillPlaceholders(text, values) {
  return text.replace(/\{(reporter|incident|agent)\}/g, (whole, name) => values[name] ?? whole)
}

/** Replies whose title or text contains the search, case-insensitively. */
export function matchReplies(replies, search) {
  const wanted = search.trim().toLowerCase()
  if (!wanted) return replies
  return replies.filter(
    (reply) =>
      reply.title.toLowerCase().includes(wanted) || reply.body.toLowerCase().includes(wanted)
  )
}
