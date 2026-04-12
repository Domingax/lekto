# Dev Pipeline Workflow

**Goal:** Autonomous dev pipeline: implement story → test → lint → commit → push → create PR → notify user.

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
  <!-- PHASE 1: IMPLEMENTATION                                       -->
  <!-- ============================================================ -->
  <step n="1" goal="Implement the story using bmad-dev-story">
    <output>🚀 **Pipeline Phase 1/2 — Implementation**</output>

    <action>Invoke the `/bmad-dev-story` skill with the story path (if provided)</action>
    <action>Let dev-story run to completion — all tasks, tests, lint, validation</action>
    <action>Dev-story will mark the story status as "review" when done</action>

    <check if="dev-story HALTs or fails">
      <action>HALT pipeline — surface the dev-story error to the user</action>
      <action>Send desktop notification: `notify-send "Lekto Pipeline" "❌ Dev phase failed — action needed"`</action>
    </check>

    <output>✅ **Phase 1 complete** — Story implemented and validated</output>
  </step>

  <!-- ============================================================ -->
  <!-- PHASE 2: DOCUMENT, COMMIT & PUSH                              -->
  <!-- ============================================================ -->
  <step n="2" goal="Document, commit, and push to remote">
    <output>📦 **Pipeline Phase 2/2 — Document, Commit & Push**</output>

    <critical>Dev-story (Phase 1) is responsible for creating individual commits at each "### Commit N:" boundary.
      This phase only handles branch creation (if needed), documentation updates, any remaining uncommitted files (story file, sprint-status, docs), and pushing.</critical>

    <action>Run `git status` to see all changes</action>
    <action>Run `git log --oneline` to verify individual commits were created by dev-story</action>

    <!-- Determine branch -->
    <action>Check current branch name</action>
    <check if="on main or master">
      <action>Extract story_key from story file</action>
      <action>Create and switch to branch: `story/{{story_key}}`</action>
    </check>

    <!-- Documentation assessment (AI-3) -->
    <action>Assess documentation impact from the implemented story.
      Review the story tasks, commits, and any divergence from the plan.
      Update or create documentation if ANY of the following triggers apply:
      - New user-visible behavior, command, or feature introduced
      - New library, tool, or external dependency added
      - Public API or function contract changed (signature, return semantics)
      - Implementation diverged from the story plan (different library, approach, or protocol)
      - Non-trivial architectural decision made

      Documents to check and update if affected:
      - README — user-facing setup, usage, testing, or development sections
      - `AGENTS.md` — developer conventions, architecture rules, constraints
      - `architecture.md` — system-level design decisions
      - Dedicated document — create one if none of the above covers the subject

      Documentation is a story deliverable: it ships in the same PR as the code.
      If no trigger applies, skip — do not add placeholder content.
    </action>

    <!-- Handle any remaining unstaged files (story file updates, sprint-status, docs) -->
    <check if="there are uncommitted changes (story file, sprint-status, docs, etc.)">
      <action>Stage remaining files (story file, sprint-status, updated or created docs)</action>
      <action>Do NOT stage: `.env`, credentials, `node_modules/`, `dist/`, `.claude/settings.local.json`</action>
      <action>Create a final commit: `chore: update story {{story_key}} status, docs, and sprint tracking`</action>
    </check>

    <!-- Push -->
    <action>Push branch to remote with `git push -u origin {{branch_name}}`</action>

    <check if="push fails">
      <action>HALT pipeline — surface the git error to the user</action>
      <action>Send desktop notification: `notify-send "Lekto Pipeline" "❌ Git push failed — action needed"`</action>
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
      - Body ({{dev_summary_section}} prepended if non-empty, then placeholder for review):
        ```
        {{dev_summary_section}}

        🤖 Generated with [Claude Code](https://claude.com/claude-code)
        ```
    </action>
    <action>Capture: {{pr_url}} and {{pr_number}}</action>

    <!-- Notify user -->
    <action>Run: `notify-send -u normal "Lekto Pipeline ✅" "Story {{story_key}} — Dev complete, PR ready\n{{pr_url}}"`</action>

    <output>
      ✅ **Pipeline Dev Complete**

      **Story:** {{story_key}}
      **PR:** {{pr_url}}

      Run `/dev-pipeline-review` to launch the code review phase.
    </output>
  </step>

</workflow>
