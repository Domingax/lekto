#!/bin/bash
# AGENTS.md enforcement hook
# Runs after every Edit/Write — mechanically checks automatable anti-patterns.
# Non-automatable rules (throw semantics, FSD layer direction, boolean args,
# magic numbers) are left to code review.

INPUT=$(cat)

echo "$INPUT" | python3 -c "
import sys, json, os, re

try:
    data = json.load(sys.stdin)
except Exception:
    sys.exit(0)

file_path = data.get('tool_input', {}).get('file_path', '')
if not file_path or not (file_path.endswith('.ts') or file_path.endswith('.tsx')):
    sys.exit(0)

try:
    with open(file_path) as f:
        content = f.read()
except Exception:
    sys.exit(0)

violations = []
is_platform = 'shared/platform/' in file_path
is_db       = 'shared/db/' in file_path
is_tsx      = file_path.endswith('.tsx')
is_migration = 'migrations/' in file_path or file_path.endswith('.sql')

# ── Rule 1: Direct Capacitor/Tauri plugin imports outside shared/platform/ ──
if not is_platform and not is_db:
    forbidden_imports = [
        '@capacitor/filesystem',
        '@capacitor/preferences',
        '@capacitor-community/sqlite',
        '@capawesome/capacitor',
        '@tauri-apps/plugin-fs',
        '@tauri-apps/plugin-sql',
        '@tauri-apps/plugin-stronghold',
        '@tauri-apps/plugin-store',
    ]
    hits = [p for p in forbidden_imports if p in content]
    if hits:
        violations.append(
            'Rule 1 — Platform boundary: direct Capacitor/Tauri plugin import outside shared/platform/\n'
            f'   Imports found: {hits}\n'
            '   Fix: move the call to shared/platform/<capability>/<capability>.android.ts or .desktop.ts'
        )

# ── Rule 2: .then()/.catch() chains in feature code ──
# Exception: allowed inside shared/platform/ adapter wrappers
if not is_platform:
    # Match .then( or .catch( not preceded by // (commented out)
    chain_matches = re.findall(r'(?<!\/\/ )\.(then|catch)\s*\(', content)
    if chain_matches:
        violations.append(
            'Rule 2 — No .then()/.catch() chains: use async/await instead\n'
            f'   Found {len(chain_matches)} occurrence(s)\n'
            '   Exception: allowed only inside shared/platform/ adapter wrappers'
        )

# ── Rule 3: useGlobalStore ──
if 'useGlobalStore' in content:
    violations.append(
        'Rule 3 — No useGlobalStore: use domain-specific stores (useReaderStore, useVaultStore, useAppStore)\n'
        '   Fix: create or reuse the appropriate domain store'
    )

# ── Rule 4: SQLite queries inside React components (.tsx) ──
if is_tsx:
    db_patterns = ['db.select(', 'db.insert(', 'db.update(', 'db.delete(', '.from(booksTable', '.from(tokensTable']
    hits = [p for p in db_patterns if p in content]
    if hits:
        violations.append(
            'Rule 4 — No SQLite queries inside React components\n'
            f'   Patterns found: {hits}\n'
            '   Fix: read from Zustand store; hydrate from SQLite in feature model layer, not in render'
        )

# ── Rule 5: Raw sql\`\` template literals (outside migrations) ──
if not is_migration and not is_platform and not is_db:
    if re.search(r'\bsql\`', content):
        violations.append(
            'Rule 5 — No raw sql\`\` template literals: use Drizzle typed query builder\n'
            '   Fix: replace with db.select().from(...), db.insert(...).values(...), etc.'
        )

if violations:
    name = os.path.basename(file_path)
    print(f'⚠️  AGENTS.md violation(s) in {name}:')
    for i, v in enumerate(violations, 1):
        print(f'   [{i}] {v}')
    sys.exit(1)
"
