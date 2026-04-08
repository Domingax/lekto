# Deferred Work

---

## Android vault — tree URI permissions (ACTION_OPEN_DOCUMENT_TREE)

**Source:** Adversarial review of `fix/android-directory-picker` (PR #15)

`FilePicker.pickDirectory()` returns a content tree URI (e.g. `content://com.android.externalstorage.documents/tree/primary%3A...`). On Android, access to a tree URI granted via `ACTION_OPEN_DOCUMENT_TREE` does **not** persist across app restarts unless `takePersistableUriPermission` is explicitly called and the URI is stored.

Before implementing vault read/write on Android, verify:
- Whether `@capawesome/capacitor-file-picker` calls `takePersistableUriPermission` internally after `pickDirectory()`
- Whether the Capacitor Filesystem plugin accepts raw tree URIs or requires a document URI derived from it
- If neither handles persistence, add a native call to persist the URI permission and store the URI in secure storage

**Surfaces during:** any story implementing vault file read/write on Android.

