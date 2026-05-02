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

---

## Delete dialog — close animation missing (open={true} + conditional render)

**Source:** Adversarial review of fix/delete-dialog-overlay

`LibraryPage.tsx` line 262 renders the delete confirmation `Dialog` with a hard-coded `open={true}` and wraps it in `{pendingDelete && ...}`. Radix never sees a `closed` state transition, so `data-[state=closed]` animation classes never fire — both the backdrop and dialog content vanish instantly on confirm/cancel rather than fading out.

**Fix:** Replace the conditional render + `open={true}` pattern with a controlled `open={!!pendingDelete}` that always mounts the Dialog (so Radix can animate it closed), e.g.:

```tsx
<Dialog open={!!pendingDelete} onOpenChange={(open) => { if (!open) setPendingDelete(null) }}>
  ...
</Dialog>
```

**Surfaces during:** any story touching LibraryPage or the delete-book flow.

---

## index.css — @layer utilities declared before @import "tailwindcss"

**Source:** Adversarial review of fix/delete-dialog-overlay

The `@layer utilities` block in `src/index.css` appears before `@import "tailwindcss"`. In Tailwind CSS v4, `@layer` declarations before the import may be silently ignored by the build pipeline.

**Surfaces during:** any story touching global styles or adding custom utility classes.

