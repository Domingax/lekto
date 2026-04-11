import '@testing-library/jest-dom'

// Node.js 22+ exposes a non-functional stub `localStorage` that shadows
// jsdom's implementation. Replace it with a simple in-memory implementation.
const _localStorageStore = new Map<string, string>()
Object.defineProperty(globalThis, 'localStorage', {
  value: {
    getItem: (key: string) => _localStorageStore.get(key) ?? null,
    setItem: (key: string, value: string) => { _localStorageStore.set(key, value) },
    removeItem: (key: string) => { _localStorageStore.delete(key) },
    clear: () => { _localStorageStore.clear() },
    get length() { return _localStorageStore.size },
    key: (index: number) => [..._localStorageStore.keys()][index] ?? null,
  } satisfies Storage,
  writable: true,
  configurable: true,
})
