import { useEffect, useRef, useState } from 'react'
import {
  attachmentUrl,
  deleteAttachment,
  describeApiError,
  listAttachments,
  uploadAttachment,
} from '../services/incidentApi'
import {
  ATTACHMENT_ACCEPT,
  MAX_ATTACHMENTS,
  attachmentProblem,
  formatBytes,
} from '../utils/attachments'

function formatTime(value) {
  return new Date(value).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })
}

/**
 * The files on an incident: thumbnails for images, downloads for the rest,
 * and (when allowed) a drop zone to add more. `onChanged` runs after a file
 * is added or removed, so the timeline can refresh.
 */
function IncidentAttachments({ incidentId, canAttach, currentUser, onChanged }) {
  const [attachments, setAttachments] = useState([])
  const [loaded, setLoaded] = useState(false)
  const [loadError, setLoadError] = useState('')
  const [uploading, setUploading] = useState('')
  const [errors, setErrors] = useState([])
  const [confirming, setConfirming] = useState(null)
  const [dragging, setDragging] = useState(false)
  const input = useRef(null)

  useEffect(() => {
    let cancelled = false
    listAttachments(incidentId)
      .then((data) => {
        if (cancelled) return
        setAttachments(data)
        setLoaded(true)
      })
      .catch((requestError) => {
        console.error(requestError)
        if (!cancelled) setLoadError('Unable to load the attachments.')
      })
    return () => {
      cancelled = true
    }
  }, [incidentId])

  async function addFiles(fileList) {
    const files = [...fileList]
    if (files.length === 0) return
    const problems = []
    let room = MAX_ATTACHMENTS - attachments.length

    for (const file of files) {
      const problem = attachmentProblem(file)
      if (problem) {
        problems.push(problem)
        continue
      }
      if (room <= 0) {
        problems.push(`${file.name}: an incident can have at most ${MAX_ATTACHMENTS} attachments.`)
        continue
      }
      setUploading(file.name)
      try {
        const added = await uploadAttachment(incidentId, file)
        setAttachments((current) => [...current, added])
        room -= 1
        onChanged?.()
      } catch (requestError) {
        console.error(requestError)
        problems.push(`${file.name}: ${describeApiError(requestError, 'Unable to attach it.')}`)
      }
    }
    setUploading('')
    setErrors(problems)
    if (input.current) input.current.value = ''
  }

  async function remove(attachment) {
    setConfirming(null)
    try {
      await deleteAttachment(incidentId, attachment.id)
      setAttachments((current) => current.filter((item) => item.id !== attachment.id))
      setErrors([])
      onChanged?.()
    } catch (requestError) {
      console.error(requestError)
      setErrors([`${attachment.fileName}: ${describeApiError(requestError, 'Unable to remove it.')}`])
    }
  }

  const canRemove = (attachment) =>
    currentUser?.role === 'ADMIN' || attachment.uploadedById === currentUser?.id
  const full = attachments.length >= MAX_ATTACHMENTS

  return (
    <section className="attachments-section" aria-labelledby="attachments-title">
      <h2 id="attachments-title">
        Attachments{loaded && attachments.length > 0 ? ` (${attachments.length})` : ''}
      </h2>

      {loadError && <p className="error-message">{loadError}</p>}
      {loaded && attachments.length === 0 && !canAttach && (
        <p className="timeline-empty">No attachments.</p>
      )}

      {attachments.length > 0 && (
        <ul className="attachment-list">
          {attachments.map((attachment) => {
            const url = attachmentUrl(incidentId, attachment.id)
            return (
              <li key={attachment.id} className="attachment-item">
                {attachment.image ? (
                  <a href={url} target="_blank" rel="noopener noreferrer" className="attachment-thumb">
                    <img src={url} alt={attachment.fileName} loading="lazy" />
                  </a>
                ) : (
                  <span className="attachment-icon" aria-hidden="true">
                    {attachment.contentType === 'application/pdf' ? 'PDF' : 'TXT'}
                  </span>
                )}
                <div className="attachment-meta">
                  <a href={url} {...(attachment.image ? { target: '_blank', rel: 'noopener noreferrer' } : { download: attachment.fileName })}>
                    {attachment.fileName}
                  </a>
                  <small>
                    {formatBytes(attachment.sizeBytes)} · {attachment.uploadedByName} ·{' '}
                    {formatTime(attachment.createdAt)}
                  </small>
                </div>
                {canRemove(attachment) &&
                  (confirming === attachment.id ? (
                    <span className="attachment-confirm">
                      <button
                        type="button"
                        className="danger-button small-button"
                        onClick={() => remove(attachment)}
                      >
                        Remove
                      </button>
                      <button
                        type="button"
                        className="secondary-button small-button"
                        onClick={() => setConfirming(null)}
                      >
                        Keep
                      </button>
                    </span>
                  ) : (
                    <button
                      type="button"
                      className="secondary-button small-button"
                      aria-label={`Remove ${attachment.fileName}`}
                      onClick={() => setConfirming(attachment.id)}
                    >
                      Remove
                    </button>
                  ))}
              </li>
            )
          })}
        </ul>
      )}

      {canAttach && (
        <div
          className={`attachment-drop${dragging ? ' dragging' : ''}`}
          onDragOver={(event) => {
            event.preventDefault()
            setDragging(true)
          }}
          onDragLeave={() => setDragging(false)}
          onDrop={(event) => {
            event.preventDefault()
            setDragging(false)
            if (!uploading && !full) addFiles(event.dataTransfer.files)
          }}
        >
          <label>
            <span>
              {uploading
                ? `Attaching ${uploading}…`
                : full
                  ? `This incident has the maximum of ${MAX_ATTACHMENTS} attachments.`
                  : 'Drop files here or choose files to attach'}
            </span>
            <input
              ref={input}
              type="file"
              multiple
              accept={ATTACHMENT_ACCEPT}
              disabled={Boolean(uploading) || full}
              onChange={(event) => addFiles(event.target.files)}
            />
          </label>
          <small>Images, PDFs and text or log files, up to 5 MB each.</small>
        </div>
      )}

      {errors.length > 0 && (
        <ul className="error-message attachment-errors" role="alert">
          {errors.map((message) => (
            <li key={message}>{message}</li>
          ))}
        </ul>
      )}
    </section>
  )
}

export default IncidentAttachments
