#!/usr/bin/env node
// The guide's npm dependency-licence gate (ADR-0011, ADR-0024).
//
// `./gradlew checkDependencyLicences` reads only the Gradle dependency graph, so
// it cannot see the Node toolchain VitePress brings. This script closes that gap:
// it reads every installed package's declared licence and fails on one that is not
// AGPL-3.0-compatible. The accepted set follows the same policy as
// `config/dependency-licences.txt` (ADR-0011) — a permissive core plus the
// project's own licence — and is deliberately strict: an id missing here fails the
// gate and is added only after a hand review.
//
// Run from the guide root after `npm ci`:
//
//   node scripts/check-licences.mjs
//
import { existsSync, readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";
import { pathToFileURL } from "node:url";

// Licences that may be combined with AGPL-3.0 code (ADR-0011), mirroring the
// permissive core of `config/dependency-licences.txt`, plus the project's own
// licence. A build-only dependency is never conveyed with the app, but the same
// "free and AGPL-compatible" bar is applied rather than a weaker one.
export const ALLOWED_LICENCES = new Set([
  // Permissive licences the FSF lists as GNU GPL-compatible.
  "MIT",
  "ISC",
  "Apache-2.0",
  "BSD-2-Clause",
  "BSD-3-Clause",
  "0BSD",
  "Zlib",
  "Unlicense",
  "CC0-1.0",
  "Unicode-3.0",
  // Lekto's own licence.
  "AGPL-3.0",
  "AGPL-3.0-only",
  "AGPL-3.0-or-later",
]);

// The handful of non-SPDX spellings that still appear on npm, keyed lower-case.
const ALIASES = new Map([
  ["apache 2.0", "Apache-2.0"],
  ["apache2", "Apache-2.0"],
  ["bsd", "BSD-3-Clause"],
  ["mit/x11", "MIT"],
  ["x11", "MIT"],
]);

/** Normalise one SPDX identifier: drop a trailing `+`, then map an alias. */
function normaliseId(term) {
  const trimmed = term.trim().replace(/\+$/, "");
  return ALIASES.get(trimmed.toLowerCase()) ?? trimmed;
}

/**
 * Split an SPDX expression on `operator` at any depth, case-insensitively.
 * Parentheses are treated as whitespace: `(MIT OR ISC)` yields the same branches
 * as `MIT OR ISC`, which is what we want for an allowlist decision.
 */
function splitOn(expression, operator) {
  const separator = new RegExp(`\\s+${operator}\\s+`, "gi");
  return expression
    .replace(/[()]/g, " ")
    .split(separator)
    .map((part) => part.trim())
    .filter(Boolean);
}

/**
 * Is an SPDX expression AGPL-3.0-compatible?
 *
 * `OR` needs one compatible branch; `AND` needs every compatible term. A `WITH`
 * exception is ignored, since it narrows rather than widens the licence, and an
 * unknown id is not compatible. An empty or `SEE LICENSE IN ...` field is not
 * compatible, so a licence-less package fails the gate rather than passing it.
 */
export function isAllowed(expression, allowed = ALLOWED_LICENCES) {
  if (typeof expression !== "string" || expression.trim() === "") return false;
  const upper = expression.toUpperCase();
  if (upper.startsWith("SEE LICENSE IN")) return false;
  const allowedIds = new Set([...allowed].map((id) => id.toLowerCase()));
  return splitOn(expression, "OR").some((branch) =>
    splitOn(branch, "AND").every((term) =>
      allowedIds.has(normaliseId(term.split(/\s+WITH\s+/i)[0]).toLowerCase()),
    ),
  );
}

function idOf(value) {
  if (typeof value === "string") return value;
  if (value && typeof value === "object" && typeof value.type === "string") {
    return value.type;
  }
  return null;
}

/** Read the declared licence from a `package.json`, old or new shape. */
export function licenceOf(manifest) {
  const licence = manifest.license ?? manifest.licenses;
  if (typeof licence === "string") return licence;
  if (Array.isArray(licence)) {
    const ids = licence.map(idOf).filter(Boolean);
    return ids.length > 0 ? ids.join(" OR ") : null;
  }
  return idOf(licence);
}

/** The packages installed under `root/node_modules`, one entry each. */
export function readInstalledPackages(root) {
  const packages = [];
  const seen = new Set();

  const visit = (directory) => {
    const manifest = join(directory, "package.json");
    if (existsSync(manifest)) {
      const pkg = JSON.parse(readFileSync(manifest, "utf8"));
      const key = `${pkg.name}@${pkg.version ?? ""}`;
      if (pkg.name && !seen.has(key)) {
        seen.add(key);
        packages.push({
          name: pkg.name,
          version: pkg.version ?? "",
          licence: licenceOf(pkg),
        });
      }
    }
    packagesFrom(join(directory, "node_modules"), visit);
  };

  packagesFrom(join(root, "node_modules"), visit);
  return packages;
}

function packagesFrom(modulesDirectory, visit) {
  if (!existsSync(modulesDirectory)) return;
  for (const entry of readdirSync(modulesDirectory, { withFileTypes: true })) {
    if (!entry.isDirectory() || entry.name.startsWith(".")) continue;
    if (entry.name.startsWith("@")) {
      for (const scoped of readdirSync(join(modulesDirectory, entry.name), {
        withFileTypes: true,
      })) {
        if (scoped.isDirectory()) {
          visit(join(modulesDirectory, entry.name, scoped.name));
        }
      }
    } else {
      visit(join(modulesDirectory, entry.name));
    }
  }
}

/** The packages in `packages` whose licence is not AGPL-3.0-compatible. */
export function disallowedPackages(packages, allowed = ALLOWED_LICENCES) {
  return packages.filter((pkg) => !isAllowed(pkg.licence, allowed));
}

function main() {
  const packages = readInstalledPackages(process.cwd());
  if (packages.length === 0) {
    console.error(
      "No packages found under node_modules. Run `npm ci` first, from the guide root.",
    );
    return 2;
  }
  const disallowed = disallowedPackages(packages);
  if (disallowed.length > 0) {
    console.error("Dependencies with an AGPL-3.0-incompatible licence:");
    for (const pkg of disallowed) {
      console.error(`  ${pkg.name}@${pkg.version}: ${pkg.licence ?? "(none declared)"}`);
    }
    console.error("Review each one and add it to ALLOWED_LICENCES only if it is compatible.");
    return 1;
  }
  console.log(`All ${packages.length} npm dependencies are AGPL-3.0-compatible.`);
  return 0;
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  process.exitCode = main();
}
