import { useEffect, useRef } from 'react'
import { playHover, playClick, playDelete } from '../utils/sounds'

/**
 * Mounts global event delegation for sound effects.
 * Any element with class `sound__hover` plays a hover sound on mouseenter.
 * Any element with class `sound__click` plays a click sound on click.
 * Any element with class `sound__delete` plays a delete sound on click.
 *
 * Uses `mouseover` (bubbles) instead of `mouseenter` for delegation,
 * with a ref to deduplicate repeated firings on the same element.
 */
export function SoundEffects() {
  const lastHovered = useRef<EventTarget | null>(null)

  useEffect(() => {
    function onMouseOver(e: MouseEvent) {
      const target = (e.target as Element).closest('.sound__hover')
      if (target && target !== lastHovered.current) {
        lastHovered.current = target
        playHover()
      }
      if (!target) {
        lastHovered.current = null
      }
    }

    function onClick(e: MouseEvent) {
      const el = e.target as Element
      if (el.closest('.sound__delete')) {
        playDelete()
      } else if (el.closest('.sound__click')) {
        playClick()
      }
    }

    document.addEventListener('mouseover', onMouseOver)
    document.addEventListener('click', onClick)
    return () => {
      document.removeEventListener('mouseover', onMouseOver)
      document.removeEventListener('click', onClick)
    }
  }, [])

  return null
}
