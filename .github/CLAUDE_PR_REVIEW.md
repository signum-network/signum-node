# Claude AI PR Review — Setup Guide

Automated Pull Request review using Claude AI with a two-stage workflow that works securely for both internal branches and external fork PRs.

## Architecture

The review uses two workflows to solve GitHub's secret isolation for fork PRs:

```
PR opened/updated (fork or internal)
        │
        ▼
┌──────────────────────────────┐
│  Stage 1: pr-size-check.yml  │  ← pull_request trigger (fork context, NO secrets)
│  - Counts files & additions  │
│  - Detects protocol files    │
│  - Fails if PR too large     │
│  - Uploads PR metadata       │
└──────────┬───────────────────┘
           │ artifact (only on success)
           ▼
┌──────────────────────────────┐
│  Stage 2: pr-review.yml      │  ← workflow_run trigger (base repo context, HAS secrets)
│  - Downloads metadata        │
│  - Collects diff via GH API  │
│  - Loads review guidelines   │
│  - Calls Claude API          │
│  - Posts review comment      │
└──────────────────────────────┘
```

**Why two stages?**
1. **Secret isolation**: GitHub does not expose repository secrets to workflows triggered by fork PRs (`pull_request` event). Stage 1 runs without secrets; Stage 2 runs in the base repo's context where `ANTHROPIC_API_KEY` is available.
2. **Cost savings**: Stage 2 only triggers if Stage 1 passes. Oversized PRs are rejected before any API credits are spent.

## Setup

### 1. Repository Secret

Add `ANTHROPIC_API_KEY` in Settings → Secrets and variables → Actions:
- Get a key from https://console.anthropic.com/
- The workflow uses `claude-sonnet-4-20250514` by default

### 2. Workflow Permissions

In Settings → Actions → General → Workflow permissions:
- Select **"Read and write permissions"**
- Check **"Allow GitHub Actions to create and approve pull requests"**

### 3. Workflow Files

Both must be present on the default branch:
- `.github/workflows/pr-size-check.yml` — Stage 1 (size gate + metadata)
- `.github/workflows/pr-review.yml` — Stage 2 (diff collection + Claude review)

### 4. Branch Protection (recommended)

Add **"PR Size Check"** as a required status check on `develop` and `main` branches. This prevents oversized PRs from being merged even if maintainers bypass the comment.

## Review Instructions

Review criteria are defined in two files, both loaded by Stage 1:

| File | Content |
|------|---------|
| `CLAUDE.md` | Project architecture, coding standards, testing requirements |
| `.github/copilot-instructions.md` | Security review rules, protocol-frozen file list, exploit patterns, PR quality gates |

**Both Claude and Copilot use the same review instructions** from `copilot-instructions.md`. To update review criteria, edit that file — changes take effect on the next PR.

### Review Priorities (in order)

1. **Protocol Integrity** — The Signum protocol is stable. Any changes to consensus, block validation, transaction types, constants, cryptography, AT engine, or P2P protocol files are rejected unless fixing a disclosed CVE. See the frozen file list in `copilot-instructions.md`.

2. **Security** — Backdoors, hardcoded accounts/keys, obfuscated logic, integer overflow/underflow, signature bypass, gas metering bypass, input validation gaps.

3. **Architecture** — Layer violations (API → Services → Stores → DB), missing dependency injection, package structure.

4. **Testing** — New logic without unit tests is rejected.

5. **PR Size** — Max 10 files changed, max 1000 new lines. Oversized PRs are flagged for splitting.

6. **Code Quality** — Clean code, proper error handling, logging.

### Output Format

Claude posts a structured comment with:
- **Verdict**: APPROVE / REQUEST_CHANGES / COMMENT
- **Protocol Impact**: Lists any protocol-frozen files touched
- **Security Findings**: Issues prefixed with a warning marker
- **Architecture & Quality**: Notable findings
- **Action Items**: Numbered list of required changes

## Trigger Conditions

Reviews run when a PR is **opened**, **updated** (new commits), or **reopened** against `develop` or `main`. Draft PRs are skipped.

### File Types Analyzed
`*.java`, `*.gradle`, `*.properties`, `*.md`, `*.json`, `*.ts`, `*.tsx`, `*.js`, `*.yml`

### Diff Size Limit
Diffs are truncated to 50KB. For very large PRs, the review may be incomplete — this is another reason to enforce small PRs.

## Troubleshooting

### No review comment posted

1. Check that **both** workflow runs completed: Actions tab → look for "PR Size Check" and "PR Review: Claude Analysis"
2. Stage 1 (size check) must succeed and upload an artifact before Stage 2 triggers
3. If size check fails, Stage 2 won't run — that's by design (PR is too large)
4. If Stage 2 shows "API returned HTTP 401" → the `ANTHROPIC_API_KEY` is invalid or expired
5. If Stage 2 never runs → check that the workflow name in `pr-review.yml` matches: `workflows: ["PR Size Check"]`

### Stage 2 never triggers

The `workflow_run` trigger only fires for workflows on the **default branch**. Both workflow files must be merged to `main` (or your default branch) before they work.

### API errors

| HTTP Status | Cause | Fix |
|-------------|-------|-----|
| 401 | Invalid or missing API key | Update `ANTHROPIC_API_KEY` secret |
| 429 | Rate limit exceeded | Wait and re-run, or reduce diff size |
| 500+ | Anthropic service issue | Re-run the workflow |

### Debug mode

Add to the workflow environment:
```yaml
env:
  ACTIONS_STEP_DEBUG: true
```

## Customization

### Changing trigger branches

Edit `pr-collect.yml`:
```yaml
branches: [develop, main, release/*]
```

### Changing the Claude model

Edit the `model` field in `pr-review.yml`:
```python
request = {
    "model": "claude-sonnet-4-20250514",  # change here
    ...
}
```

### Adjusting file filters

Edit the `files` list in `pr-collect.yml` under the `changed-files` step.

## Cost

- Typical review: ~$0.01–0.05 per PR (Sonnet, 50KB diff)
- Large PRs hitting the 50KB truncation limit: ~$0.05–0.10
- Monitor usage at https://console.anthropic.com/
