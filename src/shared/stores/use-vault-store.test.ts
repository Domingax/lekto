import { describe, it, expect, beforeEach } from 'vitest'
import { useVaultStore } from './use-vault-store'
import type { BookEntity } from '@/entities'

const book1: BookEntity = { id: '1', title: 'Book One', fileName: 'one.epub', language: 'en', coverPath: null, createdAt: 1000 }
const book2: BookEntity = { id: '2', title: 'Book Two', fileName: 'two.epub', language: 'fr', coverPath: null, createdAt: 2000 }
const book3: BookEntity = { id: '3', title: 'Book Three', fileName: 'three.epub', language: 'de', coverPath: null, createdAt: 3000 }

describe('useVaultStore', () => {
  beforeEach(() => {
    useVaultStore.setState({
      vaultPath: null,
      isVaultReady: false,
      books: [],
    })
  })

  it('has correct initial state', () => {
    const state = useVaultStore.getState()
    expect(state.vaultPath).toBeNull()
    expect(state.isVaultReady).toBe(false)
  })

  it('setVaultPath sets vaultPath and isVaultReady', () => {
    useVaultStore.getState().setVaultPath('lekto-vault')
    const state = useVaultStore.getState()
    expect(state.vaultPath).toBe('lekto-vault')
    expect(state.isVaultReady).toBe(true)
  })

  it('clearVault resets all state', () => {
    useVaultStore.getState().setVaultPath('lekto-vault')
    useVaultStore.getState().clearVault()
    const state = useVaultStore.getState()
    expect(state.vaultPath).toBeNull()
    expect(state.isVaultReady).toBe(false)
  })

  it('initial books state is empty array', () => {
    expect(useVaultStore.getState().books).toEqual([])
  })

  it('setBooks replaces the books list', () => {
    useVaultStore.getState().setBooks([book1, book2])
    expect(useVaultStore.getState().books).toEqual([book1, book2])
  })

  it('addBook appends to the existing list', () => {
    useVaultStore.getState().setBooks([book1])
    useVaultStore.getState().addBook(book3)
    expect(useVaultStore.getState().books).toEqual([book1, book3])
  })
})
