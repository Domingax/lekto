---
name: deliver-spec
description: "Implement the result of /to-spec and /to-tickets in code: one `deliver` subagent per ticket across the frontier, landed as a single PR, closed with a milestone report."
disable-model-invocation: true
---
# Deliver Spec

You have been provided a spec. This spec should have tickets associated with it, describing how to implement the spec.

The issue tracker should have been provided to you. If not, tell the user to run `/setup-matt-pocock-skills`.

The goal is the entire spec implemented on a single **integration branch**, with every ticket resolved the way the issue tracker closes work.

The tickets are not a list of steps. They are a **task graph** with blocking relationships between them. This means there is always a **frontier** of tickets which are ready to be grabbed.

The tracker gives the starting graph but goes stale mid-run: tickets only close when the PR merges, so its blocked-by count lies until the end. Track the tickets merged into the integration branch, and compute the frontier from that.

Start every subagent with a fresh context window, spawned with the model named in the driving prompt, or `opencode-go/deepseek-v4.1-flash#high` when none is named.

Communication to and from subagents should be sparse. Communicate primarily through **context pointers**: to the spec, tickets, research notes, and previous commits. Don't duplicate information already available via pointers.

**Implementer subagents** should be run in the background where possible for maximum concurrency.

## Steps

1. Read the spec and tickets to understand the task graph.
2. (optional) Use an **exploration subagent** to conduct any exploration required by the tickets - relevant codebase files or external documentation. Ensure the exploration subagent can save files - it should save its markdown notes in a directory outside the repo, accessible by all future subagents. This lets **implementer subagents** focus on implementation rather than exploration.
3. Create the integration branch. If the issue tracker closes work through PRs, or the user asks for one, open a draft PR after the first merge in step 5 (a branch with no commits ahead of main can't open one), marked as closing the spec and tickets.
4. Use a fresh **implementer subagent** for each ticket, each in its own worktree on its own branch. Each implementer subagent:
   - confirms its worktree is based on the integration branch before starting, and resets onto it if not;
   - calls the Skill tool with `tdd` and `clean-code` to build the ticket;
   - follows `docs/development.md`: targeted tests while working, one full `check` on the final diff;
   - merges the integration branch tip into its own branch before reporting done
5. Once an **implementer subagent** completes, merge its work to the integration branch with a **merger subagent**. The merger resolves conflicts and runs only the targeted suites for the touched modules; it does not re-run the full `check` on an unchanged tree. Push the integration branch and let the `fast` lane validate the result.
6. If this changes the **frontier** of available tickets, kick off more **implementer subagents** to work on the new tickets. This allows for maximum concurrency.
7. Once all tickets are complete, call the Skill tool with `code-review` and `sync-docs` and on the integration branch. Fix all issues raised by the code review in a single fresh **implementer subagent**.
8. If a draft PR exists, mark it ready for review. Otherwise, resolve each ticket the way the issue tracker closes work, and report the integration branch. Then read the SonarQube report for the pull request — the detailed analysis, not just the check status — and fix or explicitly accept each finding.
9. Clean up all **implementer subagent** worktrees.
10. Close with a novice summary, in the user's language. A short summary a  
non-engineer can follow: what changed, why it matters, and what they must still  
do. Write it in the language the user writes to you in. When the messages are too short to tell the language, fall back to the environment locale (`LANG`/`LC_ALL`); when that is still unclear, ask. 