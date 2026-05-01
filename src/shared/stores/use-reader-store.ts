import { create } from 'zustand'

interface ReaderState {
  bookId: string | null
  currentSectionId: string | null
  tokenIndex: number
  setPosition: (input: { bookId: string; sectionId: string; tokenIndex: number }) => void
  clear: () => void
}

export const useReaderStore = create<ReaderState>((set) => ({
  bookId: null,
  currentSectionId: null,
  tokenIndex: 0,
  setPosition: ({ bookId, sectionId, tokenIndex }) =>
    set({ bookId, currentSectionId: sectionId, tokenIndex }),
  clear: () =>
    set({ bookId: null, currentSectionId: null, tokenIndex: 0 }),
}))
