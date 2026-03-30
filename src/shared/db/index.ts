import { Capacitor } from "@capacitor/core";
import { drizzle } from "drizzle-orm/sqlite-proxy";
import type { SqliteRemoteDatabase } from "drizzle-orm/sqlite-proxy";
import { ok, err } from "neverthrow";
import type { Result } from "neverthrow";
import * as schema from "./schema";

export type DrizzleDb = SqliteRemoteDatabase<typeof schema>;

let _db: DrizzleDb | null = null;

async function createWebDb(): Promise<DrizzleDb> {
  const { default: sqlite3InitModule } =
    await import("@sqlite.org/sqlite-wasm");
  const sqlite3 = await sqlite3InitModule();

  // OPFS requires COOP/COEP headers — already configured in vite.config.ts
  const oo = sqlite3.oo1;
  const DbClass = oo.OpfsDb ?? oo.DB;
  const rawDb = new DbClass("/lekto.db", "ct");

  return drizzle(
    async (sql, params, method) => {
      let stmt;
      try {
        stmt = rawDb.prepare(sql);
        if (method === "run") {
          if (params.length) stmt.bind(params);
          stmt.stepReset();
          return { rows: [] };
        }
        const rows: unknown[][] = [];
        if (params.length) stmt.bind(params);
        while (stmt.step()) {
          rows.push(stmt.get([]));
        }
        stmt.reset();
        return { rows };
      } finally {
        stmt?.finalize();
      }
    },
    { schema },
  );
}

async function createAndroidDb(): Promise<DrizzleDb> {
  const { CapacitorSQLite, SQLiteConnection } =
    await import("@capacitor-community/sqlite");
  const sqlite = new SQLiteConnection(CapacitorSQLite);
  const connection = await sqlite.createConnection(
    "lekto",
    false,
    "no-encryption",
    1,
    false,
  );
  await connection.open();

  return drizzle(
    async (sql, params, method) => {
      if (method === "run") {
        await connection.run(sql, params, false);
        return { rows: [] };
      }
      const result = await connection.query(sql, params);
      const rows = (result.values ?? []) as Record<string, unknown>[];
      if (method === "values") {
        return { rows: rows.map((row) => Object.values(row)) };
      }
      return { rows };
    },
    { schema },
  );
}

export async function initDb(): Promise<Result<DrizzleDb, string>> {
  if (_db) return ok(_db);
  try {
    _db = Capacitor.isNativePlatform()
      ? await createAndroidDb()
      : await createWebDb();
    return ok(_db);
  } catch (e) {
    return err(e instanceof Error ? e.message : String(e));
  }
}

export function getDb(): DrizzleDb {
  if (!_db) throw new Error("DB not initialized — call initDb() first");
  return _db;
}

export * as schema from "./schema";
export { runMigrations } from "./migrate";
export { seedLanguages } from "./seed-languages";
