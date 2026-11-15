import { useId, useRef, useState } from 'react'
import { mentionQueryAt } from '../utils/mentions'

/**
 * A textarea that suggests people after "@". `suggest(query)` returns the
 * people to offer; picking one writes "@Name " into the text and calls
 * `onPick(person)`. Arrow keys move, Enter or Tab picks, Escape closes.
 */
function MentionTextarea({ value, onChange, suggest, onPick, ...textareaProps }) {
  const [query, setQuery] = useState(null)
  const [active, setActive] = useState(0)
  const textarea = useRef(null)
  const listId = useId()

  const options = query ? suggest(query.query) : []
  const open = options.length > 0
  const activeIndex = Math.min(active, options.length - 1)

  function updateQuery(text, caret) {
    const next = mentionQueryAt(text, caret)
    setQuery(next)
    if (next?.query !== query?.query) setActive(0)
  }

  function pick(person) {
    const caret = textarea.current?.selectionStart ?? value.length
    const inserted = `@${person.displayName} `
    const text = value.slice(0, query.start) + inserted + value.slice(caret)
    const position = query.start + inserted.length
    onChange(text)
    onPick(person)
    setQuery(null)
    // Put the caret after the name once React has updated the text.
    requestAnimationFrame(() => {
      textarea.current?.focus()
      textarea.current?.setSelectionRange(position, position)
    })
  }

  function handleKeyDown(event) {
    if (!open) return
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      setActive((activeIndex + 1) % options.length)
    } else if (event.key === 'ArrowUp') {
      event.preventDefault()
      setActive((activeIndex - 1 + options.length) % options.length)
    } else if (event.key === 'Enter' || event.key === 'Tab') {
      event.preventDefault()
      pick(options[activeIndex])
    } else if (event.key === 'Escape') {
      event.preventDefault()
      setQuery(null)
    }
  }

  return (
    <div className="mention-input">
      <textarea
        {...textareaProps}
        ref={textarea}
        role="combobox"
        aria-autocomplete="list"
        aria-expanded={open}
        aria-controls={open ? listId : undefined}
        aria-activedescendant={open ? `${listId}-${activeIndex}` : undefined}
        value={value}
        onChange={(event) => {
          onChange(event.target.value)
          updateQuery(event.target.value, event.target.selectionStart)
        }}
        onKeyDown={handleKeyDown}
        onKeyUp={(event) => {
          if (!['ArrowUp', 'ArrowDown', 'Enter', 'Tab', 'Escape'].includes(event.key)) {
            updateQuery(event.target.value, event.target.selectionStart)
          }
        }}
        onClick={(event) => updateQuery(event.target.value, event.target.selectionStart)}
        onBlur={() => setQuery(null)}
      />
      {open && (
        <ul id={listId} className="mention-suggestions" role="listbox" aria-label="People to mention">
          {options.map((person, index) => (
            <li
              key={person.id}
              id={`${listId}-${index}`}
              role="option"
              aria-selected={index === activeIndex}
              className={index === activeIndex ? 'active' : ''}
              // mousedown, so the textarea doesn't lose focus (and close this) first.
              onMouseDown={(event) => {
                event.preventDefault()
                pick(person)
              }}
              onMouseEnter={() => setActive(index)}
            >
              <span>{person.displayName}</span>
              <small>{person.role === 'USER' ? 'reporter' : person.role.toLowerCase()}</small>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

export default MentionTextarea
