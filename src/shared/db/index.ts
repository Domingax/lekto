import { Capacitor } from "@capacitor/core";
import { drizzle } from "drizzle-orm/sqlite-proxy";
import type { SqliteRemoteDatabase } from "drizzle-orm/sqlite-proxy";
import { ok, err } from "neverthrow";
import type { Result } from "neverthrow";
import { isTauri, preferencesAdapter } from "@/shared/platform";
import * as schema from "./schema";

export type DrizzleDb = SqliteRemoteDatabase<typeof schema>;

// Must match VAULT_PATH_KEY in features/sync-vault
const VAULT_PATH_KEY = "vault_path";

let _db: DrizzleDb | null = null;

export function resetDb(): void {
  _db = null;
}

export async function initDbForNewVault(vaultPath: string): Promise<Result<DrizzleDb, string>> {
  _db = null;
  try {
    _db = await createDesktopDb(vaultPath);
    return ok(_db);
  } catch (e) {
    return err(e instanceof Error ? e.message : String(e));
  }
}

export async function importAndroidVaultDb(vaultPath: string): Promise<Result<DrizzleDb, string>> {
  _db = null;
  try {
    const { Filesystem } = await import('@capacitor/filesystem');
    const { CapacitorSQLite, SQLiteConnection } = await import('@capacitor-community/sqlite');
    const sqlite = new SQLiteConnection(CapacitorSQLite);

    // Read the binary DB from the external vault path (absolute path, no directory)
    const fileResult = await Filesystem.readFile({ path: `${vaultPath}/lekto.db` });
    const base64Data =
      typeof fileResult.data === 'string'
        ? fileResult.data
        : await (fileResult.data as Blob).text();

    // Close existing connections and delete internal DB if present
    const isDbResult = await sqlite.isDatabase('lekto');
    if (isDbResult.result) {
      await sqlite.closeAllConnections();
      await sqlite.deleteDatabase('lekto');
    }

    // Write binary DB to internal SQLite storage path used by @capacitor-community/sqlite
    // Android internal databases dir: /data/user/0/com.lekto.app/databases/lektoSQLite.db
    await Filesystem.writeFile({ path: '/data/user/0/com.lekto.app/databases/lektoSQLite.db', data: base64Data });

    // Reinitialize the Android DB connection
    _db = await createAndroidDb();
    return ok(_db);
  } catch (e) {
    return err(e instanceof Error ? e.message : String(e));
  }
}

async function createDesktopDb(vaultPath: string): Promise<DrizzleDb> {
  const { default: Database } = await import("@tauri-apps/plugin-sql");
  const db = await Database.load(`sqlite:${vaultPath}/lekto.db`);

  return drizzle(
    async (sql, params, method) => {
      if (method === "run") {
        await db.execute(sql, params as unknown[]);
        return { rows: [] };
      }
      const rows = await db.select<Record<string, unknown>[]>(
        sql,
        params as unknown[],
      );
      if (method === "values") {
        return { rows: rows.map((row: Record<string, unknown>) => Object.values(row)) };
      }
      return { rows };
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

export async function initDb(): Promise<Result<DrizzleDb | null, string>> {
  if (_db) return ok(_db);
  try {
    if (isTauri()) {
      const vaultResult = await preferencesAdapter.get(VAULT_PATH_KEY);
      if (vaultResult.isErr()) return ok(null); // No vault configured yet — first launch
      _db = await createDesktopDb(vaultResult.value);
    } else if (Capacitor.isNativePlatform()) {
      _db = await createAndroidDb();
    } else {
      throw new Error("Unsupported platform");
    }
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
