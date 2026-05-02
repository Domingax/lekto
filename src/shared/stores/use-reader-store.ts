import { create } from 'zustand'
import type { SectionEntity } from '@/entities/section'
import type { TokenEntity } from '@/entities/token'

interface ReaderState {
  bookId: string | null
  currentSectionId: string | null
  tokenIndex: number
  sections: SectionEntity[]
  tokens: TokenEntity[]
  isChromeVisible: boolean
  setPosition: (input: { bookId: string; sectionId: string; tokenIndex: number }) => void
  setSections: (sections: SectionEntity[]) => void
  setTokens: (tokens: TokenEntity[]) => void
  toggleChrome: () => void
  clear: () => void
}

export const useReaderStore = create<ReaderState>((set) => ({
  bookId: null,
  currentSectionId: null,
  tokenIndex: 0,
  sections: [],
  tokens: [],
  isChromeVisible: true,
  setPosition: ({ bookId, sectionId, tokenIndex }) =>
    set({ bookId, currentSectionId: sectionId, tokenIndex }),
  setSections: (sections) => set({ sections }),
  setTokens: (tokens) => set({ tokens }),
  toggleChrome: () => set((s) => ({ isChromeVisible: !s.isChromeVisible })),
  clear: () =>
    set({ bookId: null, currentSectionId: null, tokenIndex: 0, sections: [], tokens: [], isChromeVisible: true }),
}))
