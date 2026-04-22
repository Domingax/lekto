# Smoke Tests — Desktop (Tauri)

Manual validation checklist for platform adapters and features that cannot be covered by the unit test suite (Tauri IPC, file system, native dialogs).

**Prerequisites:**
- Active graphical session (X11 or Wayland)
- GNOME Keyring (or any Secret Service-compatible daemon) running — required by the `secure-storage` adapter
- `npm run tauri:dev` — live Tauri window

---

## Vault — First Launch

1. `npm run tauri:dev`
2. On first launch, the app should display the vault creation screen (no existing vault found)
3. Create a vault → confirm navigation to the library screen
4. Quit and relaunch → app should reopen directly on the library screen (vault path persisted via `preferencesAdapter`)

## Vault — Open Existing Vault

1. `npm run tauri:dev` (delete the `preferences` store to simulate first launch if needed)
2. On the vault setup screen, click **"Open existing vault"**
3. A native folder picker opens; select a folder that contains a `lekto.db` file → confirm navigation to the library screen with all previous data
4. Select a folder without `lekto.db` → confirm the inline error "This folder does not contain a valid Lekto vault" is shown and the screen stays open

If any of these steps fail, the most likely culprits are the filesystem adapter (vault path resolution) or the secure-storage adapter (GNOME Keyring not available).

## EPUB Import (story 3-1)

1. `npm run tauri:dev` (vault already configured)
2. On the library screen, click **"Import EPUB"**
3. Select a valid `.epub` file → confirm the language detection dialog appears
4. Confirm the language → book should appear in the library list with title, language code, and "0%"
5. Relaunch the app → the imported book should still appear (persisted in `lekto.db`)
