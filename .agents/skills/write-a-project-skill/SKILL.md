---
name: write-a-project-skill
description: Add, change or drop a Lekto project skill. Use when a recurring task should become a skill — adding a sync driver, a dictionary language pair, a CI lane — or when curating the ledger in docs/agents/skills.md.
---

A **project skill** encodes a task this repository repeats. Project skills live in
`.agents/skills/<name>/`, beside the vendored engineering skills, and are governed by
the ledger at [`docs/agents/skills.md`](../../../docs/agents/skills.md) — the single
source of truth for what ships, defers and is rejected. Read the ledger before you
touch the set.

The writing standard is `/writing-for-agents`: steps end on a completion criterion,
reference is consulted on demand, each meaning has one home, and a leading word beats
a restated sentence. A skill that restates `AGENTS.md` or `docs/build.md` is a cache
that goes stale — point at the source of truth instead.

## Add or change a skill

1. **Name the task.** The name is the recurring task's verb phrase
   (`add-a-sync-driver`, not `sync-driver`), so the ledger reads as a list of work.
2. **Write `SKILL.md`** at `.agents/skills/<name>/SKILL.md`. Frontmatter carries
   `name`, and a `description` holding the trigger branches when the skill is
   model-invoked.
3. **Write `agents/openai.yaml`** mirroring the vendored skills: `interface.display_name`
   and `interface.short_description`, plus `policy.allow_implicit_invocation: false`
   when only a human may fire it.
4. **Choose invocation.** Leave `disable-model-invocation` out when an agent must
   reach the skill on its own, and carry the trigger branches in the description;
   set it to `true` when only a human types the name, and keep the description a
   one-line summary.
5. **Register it** in the ledger as a `Shipped` row naming what it encodes. If its
   subject does not exist yet, stop and add a `Deferred` row naming what it waits
   on — never ship a skill you cannot exercise.
6. **Exercise it once against the real repository.** Run the skill on a real
   instance and record the outcome in its row.

Done when `SKILL.md` and `agents/openai.yaml` exist, the ledger row names the
exercise, and the exercise produced a real change or a recorded run.

## Drop a skill

Delete the directory and remove its entry from `skills-lock.json` when it is
vendored, then record it under `Rejected` with the reason. The ledger defines what
justifies a drop; apply it, and keep the reason to one line.
