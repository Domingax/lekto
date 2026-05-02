import { useRef } from 'react'
import { useReaderStore } from '@/shared/stores'

const SWIPE_THRESHOLD_PX = 50

export function useReaderView() {
  const pointerStartX = useRef<number | null>(null)

  function navigateForward() {
    const { sections, currentSectionId, bookId, setPosition } = useReaderStore.getState()
    const idx = sections.findIndex((s) => s.id === currentSectionId)
    const next = sections[idx + 1]
    if (next && bookId) setPosition({ bookId, sectionId: next.id, tokenIndex: 0 })
  }

  function navigatePrev() {
    const { sections, currentSectionId, bookId, setPosition } = useReaderStore.getState()
    const idx = sections.findIndex((s) => s.id === currentSectionId)
    const prev = sections[idx - 1]
    if (prev && bookId) setPosition({ bookId, sectionId: prev.id, tokenIndex: 0 })
  }

  function handlePointerDown(e: React.PointerEvent) {
    pointerStartX.current = e.clientX
  }

  function handlePointerUp(e: React.PointerEvent) {
    if (pointerStartX.current === null) return
    const deltaX = e.clientX - pointerStartX.current
    if (Math.abs(deltaX) >= SWIPE_THRESHOLD_PX) {
      if (deltaX < 0) navigateForward()
      else navigatePrev()
    }
    pointerStartX.current = null
  }

  return { navigateForward, navigatePrev, handlePointerDown, handlePointerUp }
}
