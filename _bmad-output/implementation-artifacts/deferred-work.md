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

---

## migrate.ts — pre-existing code quality issues

**Source:** Adversarial review of Sonar lint fixes (story/2-1-desktop-vault-setup-and-web-cleanup)

Pre-existing issues surfaced during review, not caused by the Sonar fixes:

- **SQL injection latente** (`recordMigrationQuery`): requête construite par concaténation de chaîne — un `tag` avec apostrophe serait injecté. Utiliser des paramètres liés ou a minima sanitizer le tag.
- **Collision de timestamp** (`collectPendingQueries`): le filtre `lastAppliedAt < m.createdAt` rejoue une migration si son `createdAt` est identique au dernier enregistré (condition `<` exclut l'égalité). Passer à `<=` ou filtrer par `hash`.
- **Index colonne implicite** (`fetchLastAppliedAt`): `rows[0]?.[2]` repose sur l'ordre des colonnes du SELECT — fragile si la requête est modifiée.
- **Casts non vérifiés**: `import.meta.glob(…) as Record<string, string>` et `as Record<string, Journal>` — aucune garde de type à l'exécution.

**Surfaces during:** toute histoire touchant la couche de migration DB.

