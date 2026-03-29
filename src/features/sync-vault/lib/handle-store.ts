const DB_NAME = 'lekto-meta'
const STORE_NAME = 'handles'
const VAULT_HANDLE_KEY = 'vault'

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, 1)
    req.onupgradeneeded = () => req.result.createObjectStore(STORE_NAME)
    req.onsuccess = () => resolve(req.result)
    req.onerror = () => reject(new Error(req.error?.message ?? 'Failed to open IDB'))
  })
}

export async function storeVaultHandle(handle: FileSystemDirectoryHandle): Promise<void> {
  const db = await openDb()
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_NAME, 'readwrite')
    tx.objectStore(STORE_NAME).put(handle, VAULT_HANDLE_KEY)
    tx.oncomplete = () => { db.close(); resolve() }
    tx.onerror = () => { db.close(); reject(new Error(tx.error?.message ?? 'IDB transaction failed')) }
  })
}

export async function loadVaultHandle(): Promise<FileSystemDirectoryHandle | null> {
  try {
    const db = await openDb()
    return new Promise((resolve, reject) => {
      const tx = db.transaction(STORE_NAME, 'readonly')
      const req = tx.objectStore(STORE_NAME).get(VAULT_HANDLE_KEY)
      req.onsuccess = () => {
        db.close()
        resolve((req.result as FileSystemDirectoryHandle | undefined) ?? null)
      }
      req.onerror = () => { db.close(); reject(new Error(req.error?.message ?? 'IDB request failed')) }
    })
  } catch {
    return null
  }
}

export async function clearVaultHandle(): Promise<void> {
  try {
    const db = await openDb()
    return new Promise((resolve, reject) => {
      const tx = db.transaction(STORE_NAME, 'readwrite')
      tx.objectStore(STORE_NAME).delete(VAULT_HANDLE_KEY)
      tx.oncomplete = () => { db.close(); resolve() }
      tx.onerror = () => { db.close(); reject(new Error(tx.error?.message ?? 'IDB transaction failed')) }
    })
  } catch {
    // IDB unavailable — nothing to clear
  }
}
