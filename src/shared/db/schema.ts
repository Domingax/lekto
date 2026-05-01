import { sqliteTable, text, integer, index, primaryKey } from 'drizzle-orm/sqlite-core'

// Lookup table — pre-seeded with ISO 639-1 codes via seed-languages.ts
export const languages = sqliteTable('languages', {
  code: text('code').primaryKey(),  // ISO 639-1: 'en', 'fr', 'es', ...
  name: text('name').notNull(),     // 'English', 'French', 'Spanish', ...
})

export const books = sqliteTable('books', {
  id: text('id').primaryKey(),                 // UUID
  title: text('title').notNull(),
  author: text('author'),
  fileName: text('file_name').notNull(),
  language: text('language').notNull().references(() => languages.code),
  coverPath: text('cover_path'),
  createdAt: integer('created_at').notNull(),  // Unix timestamp (seconds)
}, (table) => [
  index('books_language_idx').on(table.language),
])

export const sections = sqliteTable('sections', {
  id: text('id').primaryKey(),
  bookId: text('book_id').notNull().references(() => books.id, { onDelete: 'cascade' }),
  index: integer('index').notNull(),
  title: text('title'),
})

export const tokens = sqliteTable('tokens', {
  id: text('id').primaryKey(),
  sectionId: text('section_id').notNull().references(() => sections.id, { onDelete: 'cascade' }),
  index: integer('index').notNull(),
  type: text('type').notNull(),                // 'word' | 'punctuation' | 'whitespace'
  text: text('text').notNull(),
  wordKey: text('word_key'),                   // null for non-word tokens
}, (table) => [
  index('tokens_section_idx').on(table.sectionId, table.index),
])

export const vocabulary = sqliteTable('vocabulary', {
  wordKey: text('word_key').notNull(),
  language: text('language').notNull().references(() => languages.code),
  status: integer('status').notNull().default(0), // 0=Unknown..4=Mastered, 5=Known
  translation: text('translation'),
  notes: text('notes'),
  updatedAt: integer('updated_at').notNull(),  // Unix timestamp (seconds)
}, (table) => [
  primaryKey({ columns: [table.wordKey, table.language] }),
  index('vocabulary_language_idx').on(table.language),
])

export const readingProgress = sqliteTable('reading_progress', {
  bookId: text('book_id').primaryKey().references(() => books.id, { onDelete: 'cascade' }),
  sectionId: text('section_id').notNull().references(() => sections.id, { onDelete: 'cascade' }),
  tokenIndex: integer('token_index').notNull(), // ordinal position within the section
  updatedAt: integer('updated_at').notNull(),   // Unix timestamp (seconds)
})
