# Dev Pipeline Review Workflow

**Goal:** Adversarial code review + SonarCloud analysis + PR comment + notification.

**Your Role:** Pipeline orchestrator for the review phase. You run inline (no subagent).

---

## INITIALIZATION

### Configuration Loading

Load config from `{project-root}/_bmad/bmm/config.yaml` and resolve:

- `project_name`, `user_name`
- `communication_language`

### Inputs

- `story_path` = `` (explicit story path; auto-discovered if empty)

### Auto-Discovery (if inputs not provided)

<action>If `story_path` is empty, find the active story file:
  - Check git branch name for a story key (e.g. `story/8.5` → key `8.5`)
  - Search `_bmad/stories/` for a story file matching that key with status "review"
</action>
<action>Resolve `pr_number` and `pr_url` via `gh pr view --json number,url`</action>

---

## EXECUTION

<workflow>
  <critical>This pipeline runs autonomously. Do NOT pause for user input unless a HALT condition is triggered.</critical>
  <critical>Communicate all responses in {communication_language}</critical>

  <!-- ============================================================ -->
  <!-- PHASE 1: CODE REVIEW (via bmad-code-review)                   -->
  <!-- ============================================================ -->
  <step n="1" goal="Run adversarial code review using bmad-code-review">
    <output>🔍 **Review Pipeline Phase 1/2 — Code Review**</output>

    <action>Read the current PR body with `gh pr view #{{pr_number}} --json body` and extract the **Dev Summary**
      section if present — use it as context on dev choices (workarounds, approach, decisions) before reviewing</action>

    <action>Invoke the `/bmad-code-review` skill with `story_path = {{story_path}}`, running its full workflow with one override:
      - At Step 4's interactive prompt, automatically choose **option 2** (create action items) — do NOT pause for user input
      - Let Step 5 run normally (it handles story status and sprint tracking updates)
    </action>

    <action>After bmad-code-review completes, collect from its output:
      - {{high_count}}, {{medium_count}}, {{low_count}} findings
      - {{action_count}} action items created
      - {{new_status}} (done or in-progress)
    </action>

    <action>Determine verdict:
      - APPROVED = {{new_status}} == "done" (no blocking issues)
      - CHANGES REQUESTED = {{new_status}} == "in-progress" (HIGH/MEDIUM issues remain)
    </action>
    <action>Capture: {{review_verdict}}, {{findings_summary}} = "{{high_count}} High, {{medium_count}} Medium, {{low_count}} Low"</action>

    <check if="bmad-code-review fails or HALTs">
      <action>HALT pipeline — surface the error</action>
      <action>Send desktop notification: `notify-send "Lekto Pipeline" "❌ Code review failed — action needed"`</action>
    </check>

    <output>✅ **Phase 1 complete** — Code review done: {{review_verdict}}</output>
  </step>

  <!-- ============================================================ -->
  <!-- PHASE 2: SONARCLOUD + PR COMMENT + NOTIFICATION               -->
  <!-- ============================================================ -->
  <step n="2" goal="SonarCloud analysis, update PR, notify user">
    <output>📊 **Review Pipeline Phase 2/2 — SonarCloud & PR Update**</output>

    <!-- SonarCloud Analysis -->
    <action>Query SonarCloud via MCP:
      - `mcp__sonarqube__get_project_quality_gate_status` — overall quality gate pass/fail
      - `mcp__sonarqube__search_sonar_issues_in_projects` filtered on new code — issues introduced by this PR
      - `mcp__sonarqube__search_security_hotspots` — any unresolved security hotspots
    </action>
    <action>Merge SonarCloud findings into the verdict:
      - If quality gate FAILS or new BLOCKER/CRITICAL issues found → force CHANGES REQUESTED
      - Otherwise keep {{review_verdict}} from Phase 1
    </action>

    <!-- Commit action items if any were written to story file -->
    <check if="{{action_count}} > 0">
      <action>Commit and push story file changes: `chore: add review action items for story {{story_key}}`</action>
    </check>

    <!-- Post review report as PR comment -->
    <critical>The PR comment must be written entirely in English, regardless of {communication_language}. It is a code artifact visible to all contributors.</critical>
    <action>Post the review report as a PR comment using `gh pr comment #{{pr_number}} --body "..."`:
      ```
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
    </action>

    <!-- Notify user -->
    <check if="review_verdict == 'APPROVED'">
      <action>Run: `notify-send -u normal "Lekto Pipeline ✅" "Story {{story_key}} — PR approved and ready for your review\n{{pr_url}}"`</action>
      <output>
        🎉 **Review Complete — APPROVED**

        **Story:** {{story_key}}
        **PR:** {{pr_url}}
        **Verdict:** ✅ Approved — no critical issues

        {{findings_summary}}

        **Next:** Review and merge the PR on GitHub.
      </output>
    </check>

    <check if="review_verdict == 'CHANGES REQUESTED'">
      <action>Run: `notify-send -u critical "Lekto Pipeline ⚠️" "Story {{story_key}} — Changes requested\n{{pr_url}}"`</action>
      <output>
        ⚠️ **Review Complete — CHANGES REQUESTED**

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
