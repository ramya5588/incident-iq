// Attachment limits and helpers. The backend checks all of this too, and
// decides the real file type from the content; these only give quick feedback.

export const MAX_ATTACHMENT_BYTES = 5 * 1024 * 1024
export const MAX_ATTACHMENTS = 10

/** For the file picker: what the backend accepts. */
export const ATTACHMENT_ACCEPT =
  'image/png,image/jpeg,image/gif,image/webp,application/pdf,.txt,.log,.csv,.json'

const ALLOWED_EXTENSIONS = ['png', 'jpg', 'jpeg', 'gif', 'webp', 'pdf', 'txt', 'log', 'csv', 'json']

/** "2.4 MB", "830 KB", "12 bytes". */
export function formatBytes(bytes) {
  if (bytes >= 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
  if (bytes >= 1024) return `${Math.round(bytes / 1024)} KB`
  return `${bytes} bytes`
}

/** Why a file can't be attached, or '' if it looks fine. */
export function attachmentProblem(file) {
  const extension = file.name.includes('.') ? file.name.split('.').pop().toLowerCase() : ''
  if (!ALLOWED_EXTENSIONS.includes(extension)) {
    return `${file.name}: only images (PNG, JPEG, GIF, WebP), PDFs and text files (.txt, .log, .csv, .json) can be attached.`
  }
  if (file.size === 0) return `${file.name} is empty.`
  if (file.size > MAX_ATTACHMENT_BYTES) {
    return `${file.name} is ${formatBytes(file.size)}; files can be at most 5 MB.`
  }
  return ''
}
