# Dev Pipeline Workflow

**Goal:** Autonomous end-to-end pipeline: implement story → test → lint → commit → push → code review (different model) → PR → notify user.

**Your Role:** Pipeline orchestrator. You coordinate the full cycle without user intervention until completion.

---

## INITIALIZATION

### Configuration Loading

Load config from `{project-root}/_bmad/bmm/config.yaml` and resolve:

- `project_name`, `user_name`
- `communication_language`, `document_output_language`
- `user_skill_level`
- `implementation_artifacts`

### Inputs

- `story_path` = `` (explicit story path; auto-discovered if empty — passed to dev-story)

---

## EXECUTION

<workflow>
  <critical>This pipeline runs autonomously. Do NOT pause for user input between phases unless a HALT condition is triggered.</critical>
  <critical>Communicate all responses in {communication_language}</critical>

  <!-- ============================================================ -->
  <!-- PHASE 1: IMPLEMENTATION VIA SUBTASK SUB-AGENTS               -->
  <!-- ============================================================ -->
  <step n="1" goal="Implement each story subtask via a dedicated Sonnet sub-agent">
    <output>🚀 **Pipeline Phase 1/4 — Implementation**</output>

    <!-- Context loading -->
    <action>Read the story file at {{story_path}}</action>
    <action>Load `_bmad/bmm/config.yaml`, `AGENTS.md`, and any `**/project-context.md`</action>
    <action>Extract story_key and story_title from the story file</action>
    <action>Extract all subtasks from the story file (checkbox items `- [ ]` under task/subtask sections)</action>

    <!-- Branch creation — must happen BEFORE sub-agents start -->
    <action>Check current branch name</action>
    <check if="on main or master">
      <action>Create and switch to branch: `story/{{story_key}}`</action>
    </check>
    <action>Capture: {{branch_name}}</action>

    <!-- Parallel vs sequential analysis -->
    <action>For each subtask, identify the files it is likely to touch based on its description and story context</action>
    <check if="all subtask file sets are disjoint (zero overlap between any two tasks)">
      <action>Mark execution mode as PARALLEL</action>
    </check>
    <check if="any two subtasks share at least one file">
      <action>Mark execution mode as SEQUENTIAL</action>
    </check>

    <!-- Sub-agent dispatch (one agent per subtask) -->
    <critical>Each subtask MUST be handled by a dedicated sub-agent (Agent tool, model: "sonnet").
      Never implement tasks inline — always delegate. This gives each task a fresh context window.</critical>

    <action>For SEQUENTIAL mode: invoke sub-agents one at a time, waiting for each to complete before starting the next.
      For PARALLEL mode: invoke all sub-agents simultaneously in a single message (multiple Agent tool calls).</action>

    <action>Each sub-agent receives the following prompt (fill in the placeholders):

      ```
      You are a dev agent for the Letko project. Your job is to implement ONE specific subtask.

      ## Context

      - Story file path: {{story_path}}
      - Story content (full text):
        {{story_file_content}}
      - Task to implement: {{task_description}}
      - Branch to work on: {{branch_name}} (already created locally — do NOT create another branch)
      - AGENTS.md path: AGENTS.md
      - Project config: _bmad/bmm/config.yaml

      ## Instructions

      Follow the implementation workflow defined in `.claude/skills/bmad-dev-story/workflow.md`,
      with the following adjustments:

      **Scope restriction — implement ONLY the task described above.**
      - Skip Step 1 (story discovery): use the story file path provided above directly
      - Skip Step 3 (review continuation detection): not applicable here
      - Skip Step 4 (sprint status in-progress update): the orchestrator handles this
      - In Step 8: mark ONLY the checkbox(es) for your assigned task as [x]; leave all other tasks untouched
      - Skip Step 9 (story status → "review" and sprint status update): the orchestrator handles this
      - Skip Step 10 (user communication): return a summary instead (see below)

      **After your commit(s):**
      1. Pull and rebase to integrate any commits already on the branch:
         `git pull --rebase origin {{branch_name}}`
         (If the branch has no remote yet, skip the pull and push with -u)
      2. Push: `git push -u origin {{branch_name}}`

      ## Return value

      Return a concise summary of your work (3–6 bullet points max):
      - What was implemented
      - Any non-obvious approach, workaround, or architecture decision
      - List of files changed
      ```
    </action>

    <check if="any sub-agent fails or HALTs">
      <action>HALT pipeline — surface the failing sub-agent's error to the user</action>
      <action>Send desktop notification: `notify-send "Letko Pipeline" "❌ Dev phase failed — action needed"`</action>
    </check>

    <!-- Collect summaries -->
    <action>Collect the summary returned by each sub-agent into: {{all_task_summaries}}
      (preserve the per-task attribution so the PR body can list them separately)</action>

    <!-- Story file housekeeping — done by orchestrator, not sub-agents, to avoid conflicts -->
    <action>Mark all implemented tasks as done in the story file (`- [ ]` → `- [x]`)</action>
    <action>Set story status to "review"</action>
    <action>Update sprint status if applicable</action>
    <action>Stage the story file and sprint status file</action>
    <action>Commit: `chore: update story {{story_key}} status to review`</action>
    <action>Push: `git push origin {{branch_name}}`</action>

    <output>✅ **Phase 1 complete** — {{subtask_count}} subtask(s) implemented by dedicated agents</output>
  </step>

  <!-- ============================================================ -->
  <!-- PHASE 2: COMMIT & PUSH                                        -->
  <!-- ============================================================ -->
  <step n="2" goal="Verify commits and push to remote">
    <output>📦 **Pipeline Phase 2/4 — Commit & Push**</output>

    <critical>Dev-story (Phase 1) is responsible for creating individual commits at each "### Commit N:" boundary.
      This phase only handles branch creation (if needed), any remaining uncommitted files (story file, sprint-status), and pushing.</critical>

    <action>Run `git status` to see all changes</action>
    <action>Run `git log --oneline` to verify individual commits were created by dev-story</action>

    <!-- Determine branch -->
    <action>Check current branch name</action>
    <check if="on main or master">
      <action>Extract story_key from story file</action>
      <action>Create and switch to branch: `story/{{story_key}}`</action>
    </check>

    <!-- Handle any remaining unstaged files (story file updates, sprint-status) -->
    <check if="there are uncommitted changes (story file, sprint-status, etc.)">
      <action>Stage remaining files (story file, sprint-status)</action>
      <action>Do NOT stage: `.env`, credentials, `node_modules/`, `dist/`, `.claude/settings.local.json`</action>
      <action>Create a final commit: `chore: update story {{story_key}} status and sprint tracking`</action>
    </check>

    <!-- Push -->
    <action>Push branch to remote with `git push -u origin {{branch_name}}`</action>

    <check if="push fails">
      <action>HALT pipeline — surface the git error to the user</action>
      <action>Send desktop notification: `notify-send "Letko Pipeline" "❌ Git push failed — action needed"`</action>
    </check>

    <!-- Dev Summary self-assessment -->
    <action>Self-assess whether the implementation warrants a Dev Summary section in the PR body.
      Include a Dev Summary if ANY of the following apply:
      - A non-trivial or non-obvious implementation approach was chosen
      - Conscious workarounds or shortcuts were applied (e.g. bypassing a tool, using an alternative command)
      - A complex or non-obvious architecture decision was made

      Omit the Dev Summary for straightforward stories with no surprises.

      If including, draft the section:
        ```
        ## Dev Summary
        - <what was done and why, if approach was non-trivial>
        - <any workarounds and the reason>
        - <architecture decisions not obvious from the diff>
        ```
      Capture as: {{dev_summary_section}} (empty string if omitted)
    </action>

    <!-- Create PR -->
    <action>Create the Pull Request with `gh pr create`:
      - Target branch: main
      - Title: "feat: story {{story_key}} — {{story_title_short}}"
      - Body ({{dev_summary_section}} if non-empty, otherwise a minimal placeholder):
        ```
        {{dev_summary_section}}
        🤖 Generated with [Claude Code](https://claude.com/claude-code)
        ```
    </action>
    <action>Capture: {{pr_url}} and {{pr_number}}</action>

    <output>✅ **Phase 2 complete** — Changes committed, pushed, PR created: {{pr_url}}</output>
  </step>

  <!-- ============================================================ -->
  <!-- PHASE 3: CODE REVIEW (DIFFERENT MODEL)                        -->
  <!-- ============================================================ -->
  <step n="3" goal="Spawn review agent with a different model">
    <output>🔍 **Pipeline Phase 3/4 — Code Review (Opus agent)**</output>

    <critical>The code review MUST be performed by an independent subagent with a fresh context.
      Use the `Agent` tool with `model: "opus"` parameter. This ensures the reviewer has NO prior knowledge
      of the implementation decisions and can perform a genuinely adversarial review.
      NEVER perform the review inline in this conversation — always delegate to a subagent.</critical>

    <action>Use the `Agent` tool (subagent) with `model: "opus"` and the following prompt:

      ```
      You are an adversarial code reviewer for the Letko project.

      ## Context

      - Story file: {{story_path}}
      - PR already created: #{{pr_number}} ({{pr_url}})

      ## Phase A — Context Loading

      1. Read the story file at: {{story_path}}
      2. Load project context from `_bmad/bmm/config.yaml` and `**/project-context.md` if it exists
      3. Load AGENTS.md for coding standards
      4. Load planning artifacts (load each if it exists — skip silently if absent):
         - Architecture: `{planning_artifacts}/*architecture*.md` or `{planning_artifacts}/*architecture*/*.md`
         - UX design:    `{planning_artifacts}/*ux*.md`           or `{planning_artifacts}/*ux*/*.md`
         - Epic:         identify the epic number from the story key, then load
                         `{planning_artifacts}/*epic*/epic-{{epic_num}}.md`
                         (or the whole `{planning_artifacts}/*epic*.md` if not sharded)
         Use these to validate that the implementation respects architectural constraints and UX specifications.
      5. Read the current PR body with `gh pr view #{{pr_number}} --json body` and extract the **Dev Summary**
         section if present — use it as context on the dev agent's choices (workarounds, approach, decisions)
         before reviewing the code

      ## Phase B — Code Review

      5. Perform an adversarial code review following the process in `.claude/skills/bmad-code-review/workflow.md`
         - BUT skip Step 4's interactive prompt — instead, automatically choose option 2 (create action items)
         - Let Step 5 run normally (it handles sprint status updates)
      6. Collect all findings (HIGH, MEDIUM, LOW)
      7. Determine a preliminary verdict based on code review:
         - APPROVED = no HIGH issues found
         - CHANGES REQUESTED = at least one HIGH issue exists

      ## Phase C — SonarCloud Analysis

      8. Query SonarCloud via MCP to enrich the review:
         - `mcp__sonarqube__get_project_quality_gate_status` — overall quality gate pass/fail
         - `mcp__sonarqube__search_sonar_issues_in_projects` filtered on new code — issues introduced by this PR
         - `mcp__sonarqube__search_security_hotspots` — any unresolved security hotspots

      9. Merge SonarCloud findings into the verdict:
         - If quality gate FAILS or new BLOCKER/CRITICAL issues found → CHANGES REQUESTED
         - Otherwise keep the preliminary verdict from Phase B

      ## Phase D — Update PR

      10. If action items were created in the story file, commit and push those changes

      11. Post the review report as a PR comment using `gh pr comment #{{pr_number}} --body "..."`:
          ```
          ## Code Review Report

          ## Summary
          - <1-3 bullet points summarizing the story implementation>

          ## Code Review Findings

          ### 🔴 Critical/High Issues
          <list or "None">

          ### 🟡 Medium Issues
          <list or "None">

          ### 🟢 Low Issues
          <list or "None">

          ## SonarCloud Analysis

          **Quality Gate:** <PASSED / FAILED>

          ### New Issues (this PR)
          <list or "None">

          ### Security Hotspots
          <list or "None">

          ## Review Verdict
          <APPROVED or CHANGES REQUESTED>

          ## Test plan
          - [ ] All unit tests pass (`npm test`)
          - [ ] Lint passes (`npx eslint .`)
          - [ ] Manual verification of acceptance criteria

          🤖 Generated with [Claude Code](https://claude.com/claude-code)
          ```
          Note: Do NOT edit the PR body — the description is owned by the dev agent. Post only as a comment.

      11. Return: the PR URL, the verdict (APPROVED/CHANGES REQUESTED), and a summary of findings count
      ```
    </action>

    <action>Wait for the review agent to complete</action>
    <action>Capture: {{pr_url}}, {{review_verdict}}, {{findings_summary}}</action>

    <check if="review agent fails">
      <action>HALT pipeline — surface the error</action>
      <action>Send desktop notification: `notify-send "Letko Pipeline" "❌ Code review failed — action needed"`</action>
    </check>

    <output>✅ **Phase 3 complete** — Code review done: {{review_verdict}}</output>
  </step>

  <!-- ============================================================ -->
  <!-- PHASE 4: NOTIFICATION                                         -->
  <!-- ============================================================ -->
  <step n="4" goal="Notify user that pipeline is complete">
    <output>🔔 **Pipeline Phase 4/4 — Notification**</output>

    <check if="review_verdict == 'APPROVED'">
      <action>Run: `notify-send -u normal "Letko Pipeline ✅" "Story {{story_key}} — PR approved and ready for your review\n{{pr_url}}"`</action>
      <output>
        🎉 **Pipeline Complete — APPROVED**

        **Story:** {{story_key}}
        **PR:** {{pr_url}}
        **Verdict:** ✅ Approved — no critical issues

        {{findings_summary}}

        **Next:** Review and merge the PR on GitHub.
      </output>
    </check>

    <check if="review_verdict == 'CHANGES REQUESTED'">
      <action>Run: `notify-send -u critical "Letko Pipeline ⚠️" "Story {{story_key}} — Changes requested\n{{pr_url}}"`</action>
      <output>
        ⚠️ **Pipeline Complete — CHANGES REQUESTED**

        **Story:** {{story_key}}
        **PR:** {{pr_url}}
        **Verdict:** ❌ Changes requested — review findings on the PR

        {{findings_summary}}

        **Next:** Review the PR comments on GitHub, then start a new session with:
        `"Address PR feedback for story {{story_key}}"`
      </output>
    </check>
  </step>

</workflow>
