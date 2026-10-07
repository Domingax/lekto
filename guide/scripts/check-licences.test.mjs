// The npm licence gate's own tests, so the gate is proven before it gates the
// guide's build. Run with `npm test` (Node's built-in test runner).
import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import {
  disallowedPackages,
  isAllowed,
  licenceOf,
  readInstalledPackages,
} from "./check-licences.mjs";

test("allows the permissive licences the guide's toolchain uses", () => {
  for (const licence of ["MIT", "ISC", "Apache-2.0", "BSD-2-Clause", "BSD-3-Clause"]) {
    assert.equal(isAllowed(licence), true, licence);
  }
});

test("rejects a licence that is not AGPL-compatible", () => {
  assert.equal(isAllowed("CC-BY-NC-4.0"), false);
  assert.equal(isAllowed("SSPL-1.0"), false);
  assert.equal(isAllowed("GPL-2.0-only"), false);
});

test("treats an OR expression as compatible when one branch is", () => {
  assert.equal(isAllowed("(MIT OR CC-BY-NC-4.0)"), true);
});

test("requires every term of an AND expression to be compatible", () => {
  assert.equal(isAllowed("MIT AND CC0-1.0"), true);
  assert.equal(isAllowed("MIT AND GPL-2.0-only"), false);
});

test("ignores a WITH exception and accepts its base licence", () => {
  assert.equal(isAllowed("Apache-2.0 WITH LLVM-exception"), true);
});

test("rejects an empty or pointer-only licence field", () => {
  assert.equal(isAllowed(null), false);
  assert.equal(isAllowed(""), false);
  assert.equal(isAllowed("SEE LICENSE IN LICENSE"), false);
});

test("reads the licence from the string, object and array shapes", () => {
  assert.equal(licenceOf({ license: "MIT" }), "MIT");
  assert.equal(licenceOf({ license: { type: "ISC" } }), "ISC");
  assert.equal(licenceOf({ licenses: [{ type: "MIT" }, { type: "Apache-2.0" }] }), "MIT OR Apache-2.0");
  assert.equal(licenceOf({}), null);
});

test("reads installed packages and reports the disallowed ones", () => {
  const root = mkdtempSync(join(tmpdir(), "lekto-guide-licences-"));
  try {
    const write = (path, manifest) => {
      mkdirSync(join(root, "node_modules", path), { recursive: true });
      writeFileSync(join(root, "node_modules", path, "package.json"), JSON.stringify(manifest));
    };
    write("good", { name: "good", version: "1.0.0", license: "MIT" });
    write("@scope/scoped", { name: "@scope/scoped", version: "2.0.0", license: "ISC" });
    write("bad", { name: "bad", version: "3.0.0", license: "CC-BY-NC-4.0" });
    // A package's own nested `node_modules` is walked too.
    write("bad/node_modules/nested", { name: "nested", version: "4.0.0", license: "Apache-2.0" });

    const packages = readInstalledPackages(root);
    const names = packages.map((pkg) => pkg.name).sort();
    assert.deepEqual(names, ["@scope/scoped", "bad", "good", "nested"]);

    const disallowed = disallowedPackages(packages);
    assert.deepEqual(
      disallowed.map((pkg) => pkg.name),
      ["bad"],
    );
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});
