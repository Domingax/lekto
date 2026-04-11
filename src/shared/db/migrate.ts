import { sql } from "drizzle-orm";
import { ok, err } from "neverthrow";
import type { Result } from "neverthrow";
import { getDb } from "./index";

const MIGRATIONS_TABLE = "__drizzle_migrations";

const migrationFiles = import.meta.glob("./migrations/*.sql", {
  query: "?raw",
  import: "default",
  eager: true,
}) as Record<string, string>;

const journalFiles = import.meta.glob("./migrations/meta/_journal.json", {
  import: "default",
  eager: true,
}) as Record<string, Journal>;

type Journal = { entries: JournalEntry[] };
type JournalEntry = { when: number; tag: string; breakpoints: boolean };
type Migration = { statements: string[]; createdAt: number; tag: string };

function parseSqlStatements(rawSql: string, breakpoints: boolean): string[] {
  if (!breakpoints) return [rawSql.trim()].filter(Boolean);
  return rawSql
    .split("--> statement-breakpoint")
    .map((s) => s.trim())
    .filter(Boolean);
}

function entryToMigration(entry: JournalEntry): Migration {
  const fileKey = Object.keys(migrationFiles).find((k) =>
    k.includes(entry.tag),
  );
  const rawSql = fileKey ? (migrationFiles[fileKey] ?? "") : "";
  return {
    statements: parseSqlStatements(rawSql, entry.breakpoints),
    createdAt: entry.when,
    tag: entry.tag,
  };
}

function loadMigrations(): Migration[] {
  const journal = Object.values(journalFiles)[0];
  return journal ? journal.entries.map(entryToMigration) : [];
}

async function createMigrationsTable(): Promise<void> {
  await getDb().run(
    sql.raw(`CREATE TABLE IF NOT EXISTS ${MIGRATIONS_TABLE} (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      hash text NOT NULL,
      created_at numeric
    )`),
  );
}

async function fetchLastAppliedAt(): Promise<number | undefined> {
  const rows = await getDb().values<[number, string, number]>(
    sql.raw(`SELECT id, hash, created_at FROM ${MIGRATIONS_TABLE} ORDER BY created_at DESC LIMIT 1`),
  );
  return rows[0]?.[2];
}

function recordMigrationQuery(tag: string, createdAt: number): string {
  return `INSERT INTO \`${MIGRATIONS_TABLE}\` ("hash", "created_at") VALUES('${tag}', '${createdAt}')`;
}

function collectPendingQueries(
  migrations: Migration[],
  lastAppliedAt: number | undefined,
): string[] {
  return migrations
    .filter((m) => lastAppliedAt === undefined || lastAppliedAt < m.createdAt)
    .flatMap((m) => [
      ...m.statements,
      recordMigrationQuery(m.tag, m.createdAt),
    ]);
}

export async function runMigrations(): Promise<Result<void, string>> {
  try {
    await createMigrationsTable();
    const lastAppliedAt = await fetchLastAppliedAt();
    const pendingQueries = collectPendingQueries(
      loadMigrations(),
      lastAppliedAt,
    );
    for (const query of pendingQueries) {
      await getDb().run(sql.raw(query));
    }
    return ok(undefined);
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    let cause = '';
    if (e instanceof Error && e.cause != null) {
      let causeMessage: string;
      if (e.cause instanceof Error) {
        causeMessage = e.cause.message;
      } else if (typeof e.cause === 'object') {
        causeMessage = JSON.stringify(e.cause);
      } else {
        causeMessage = String(e.cause);
      }
      cause = ` → ${causeMessage}`;
    }
    return err(msg + cause);
  }
}
