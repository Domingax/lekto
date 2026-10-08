# Lekto user guide — agent guide

This directory is the **end-user guide**: a VitePress site published to GitHub
Pages (ADR-0024). It is a **documented surface**, so a change to a user-visible
behaviour updates the page(s) named below in the same pull request, the way
`/sync-docs` reconciles `CONTEXT.md`, the ADRs and the build and test docs.

## Rules

- Never author an ADR, a `CONTEXT.md` or a research report here. The internal
  material lives in `docs/` and is never a source of this site.
- Use the terms in `CONTEXT.md` exactly; do not invent synonyms.
- English is the guide's language. The `fr/` tree is reserved for the future
  French translation and holds only a placeholder page.
- `AGENTS.md` and `README.md` are excluded from the build (`srcExclude` in
  `.vitepress/config.mts`), so they are not published.
- Run the site's checks from this directory: `npm test`,
  `npm run check:licences` and `npm run build`. The build fails on a dead
  internal link.

## Pages

| Page | File |
| ---- | ---- |
| Home and download | `index.md` |
| Getting started | `getting-started.md` |
| Import | `import.md` |
| Library | `library.md` |
| Reading | `reading.md` |
| Word lookup | `word-lookup.md` |
| Vocabulary | `vocabulary.md` |
| Pronunciation | `pronunciation.md` |
| LLM provider and privacy | `llm-provider.md` |
| Vault and sync | `vault-and-sync.md` |
| Settings and FAQ | `settings-and-faq.md` |

## Feature → page map

| A change to… | Updates |
| ------------ | ------- |
| Importing a book, the supported formats or extraction | `import.md` |
| The Library list, ordering or opening a book | `library.md` |
| The reader, pagination or the Reading position | `reading.md` |
| The word lookup panel or Dictionary shortcuts | `word-lookup.md` |
| Saving, searching, filtering or deleting vocabulary | `vocabulary.md` |
| Pronunciation and its unavailable-language message | `pronunciation.md` |
| The LLM provider, API key or Translation shortcut | `llm-provider.md` |
| The Vault, export/import or syncing to a WebDAV server | `vault-and-sync.md` |
| Settings, attribution or a common failure | `settings-and-faq.md` |
| First launch or the download links | `getting-started.md`, `index.md` |
