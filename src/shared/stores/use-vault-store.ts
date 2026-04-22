import { create } from 'zustand'
import type { BookEntity } from '@/entities'

interface VaultState {
  vaultPath: string | null
  isVaultReady: boolean
  books: BookEntity[]
  setVaultPath: (path: string) => void
  clearVault: () => void
  setBooks: (books: BookEntity[]) => void
  addBook: (book: BookEntity) => void
}

export const useVaultStore = create<VaultState>((set) => ({
  vaultPath: null,
  isVaultReady: false,
  books: [],

  setVaultPath: (path: string) =>
    set({ vaultPath: path, isVaultReady: true }),

  clearVault: () =>
    set({ vaultPath: null, isVaultReady: false }),

  setBooks: (books: BookEntity[]) =>
    set({ books }),

  addBook: (book: BookEntity) =>
    set((s) => ({ books: [...s.books, book] })),
}))
