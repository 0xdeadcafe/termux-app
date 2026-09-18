# Agent Instructions — termux-app Bughunt

> **Read CONSTITUTION.md first** for mission, priorities, and decision rules.
> This file covers *mechanics* — exactly how to operate the tooling.

---

## § 1 — Project Context

| Field | Value |
|---|---|
| Repo | `0xdeadcafe/termux-app` (fork of `termux/termux-app`) |
| Purpose | Security & quality bughunt; produces upstream patches |
| Build | Gradle / Java / Android (`minSdk=21`, `targetSdk=28`) |
| GitHub issues | **Disabled** on this fork — use `bd` instead |
| Active account | `nick_vectra` (primary), `0xdeadcafe` (alt) |

---

## § 2 — Session Start

```bash
bd prime          # Recover full workflow context (run after every clear/compaction)
bd ready          # Find unblocked work
bd show <id>      # Read issue before touching code
bd update <id> --claim  # Atomically claim work
```

---

## § 3 — Beads (`bd`) Reference

Beads is the **only** issue tracker for this project. GitHub Issues are disabled.
Never use TodoWrite, TaskCreate, or markdown TODO lists.

### 3.1 Finding Work
```bash
bd ready                      # Issues with no blockers (start here)
bd list --status=open         # All open issues
bd list --status=in_progress  # Your active work
bd show <id>                  # Full detail + dependencies
bd search <keyword>           # Full-text search across issues
bd blocked                    # Issues waiting on others
bd stale                      # Issues with no recent activity
```

### 3.2 Creating Issues
```bash
bd create \
  --title="<one-line summary>" \
  --description="Why this issue exists and what needs to be done" \
  --type=bug|task|feature \
  --priority=0         # 0=critical, 1=high, 2=medium, 3=low, 4=backlog
```

Priority aliases: `P0`–`P4` also accepted. Do **not** use "high"/"medium"/"low" strings.

Useful optional flags:
```bash
  --acceptance="criteria"   # Checked by --validate
  --design="decisions"      # Record design rationale
  --notes="context"         # Supplementary notes
  --validate                # Lint the issue before saving
```

> **WARNING**: Do NOT use `bd edit` — it opens `$EDITOR` (vim/nano) which blocks agents.
> Use `bd update <id> --field=value` for inline updates instead.

### 3.3 Updating Issues
```bash
bd update <id> --claim                  # Claim (sets assignee + in_progress)
bd update <id> --assignee=username
bd update <id> --title="new title"
bd update <id> --description="..."
bd update <id> --notes="..."
bd update <id> --design="..."
```

### 3.4 Closing Issues
```bash
bd close <id>                           # Mark complete
bd close <id1> <id2> <id3>             # Close multiple at once (more efficient)
bd close <id> --reason="explanation"   # Close with reason (e.g., won't fix)
bd close <id> --suggest-next           # Show newly unblocked issues
```

### 3.5 Dependencies
```bash
bd dep add <issue> <depends-on>   # <issue> is blocked until <depends-on> is closed
bd blocked                        # List all blocked issues
bd show <id>                      # Shows blocking + blocked-by relationships
```

### 3.6 Sync & Collaboration
```bash
bd dolt push      # Push beads state to Dolt remote (run before git push)
bd dolt pull      # Pull latest beads state from remote
```

### 3.7 Persistent Knowledge
```bash
bd remember "insight or decision worth keeping across sessions"
bd memories <keyword>   # Search stored memories
```
Use `bd remember` for anything you'd otherwise put in MEMORY.md. Don't use MEMORY.md.

### 3.8 Quality & Health
```bash
bd lint                          # Check issues for missing sections
bd doctor                        # Check sync, hooks, database health
bd doctor --check=conventions    # Lint, stale, orphan checks
bd preflight                     # Pre-PR checks (lint + stale + orphans)
bd orphans                       # Issues with broken dependency links
bd stats                         # Open/closed/blocked counts
bd human <id>                    # Flag issue for human decision
```

### 3.9 Lifecycle Utilities
```bash
bd defer <id> --until="YYYY-MM-DD"     # Defer to future date
bd supersede <id> --with=<new-id>      # Mark as superseded
bd stale                               # Find inactive issues
```

### 3.10 Structured Workflows
```bash
bd formula list           # See available workflow templates
bd mol pour <name>        # Start a structured workflow from a formula
```

---

## § 4 — GitHub CLI (`gh`) Reference

GitHub Issues are **disabled** on this fork. Use `gh` for PRs, repo metadata, Actions, and
upstream interaction.

### 4.1 Pull Requests (this fork → upstream)
```bash
# Create PR against upstream
gh pr create \
  --repo termux/termux-app \
  --base master \
  --title "Fix: <summary>" \
  --body "$(cat docs/pr-template.md)"

# Create PR within this fork (e.g., feature branch → main)
gh pr create --title "..." --body "..." --base main

# List PRs
gh pr list
gh pr list --repo termux/termux-app   # Upstream PRs

# View a PR
gh pr view <number>
gh pr view <number> --web             # Open in browser

# Check PR status / CI
gh pr checks <number>

# Review
gh pr review <number> --approve
gh pr review <number> --request-changes --body "..."

# Merge
gh pr merge <number> --squash --delete-branch
gh pr merge <number> --rebase

# Checkout a PR branch locally
gh pr checkout <number>
```

### 4.2 Repository
```bash
gh repo view                          # View this repo
gh repo view termux/termux-app        # View upstream

# Clone upstream fresh
gh repo clone termux/termux-app /tmp/upstream

# Sync fork with upstream
git fetch upstream
git rebase upstream/master
```

### 4.3 GitHub Actions / CI
```bash
gh run list                           # Recent workflow runs
gh run view <run-id>                  # Details of a run
gh run watch <run-id>                 # Live tail a running workflow
gh workflow list                      # All defined workflows
gh workflow run <workflow-name>       # Manually trigger a workflow
```

### 4.4 Releases
```bash
gh release list
gh release view <tag>
gh release create <tag> --title "..." --notes "..." --draft
```

### 4.5 Search (upstream research)
```bash
# Search upstream issues for prior art before creating a fix
gh search issues "zip slip" --repo termux/termux-app
gh search issues "PendingIntent FLAG_IMMUTABLE" --repo termux/termux-app
gh search prs "path traversal" --repo termux/termux-app
```

### 4.6 API (advanced / scripting)
```bash
# Raw REST call
gh api repos/termux/termux-app/issues/<number>

# GraphQL
gh api graphql -f query='{ viewer { login } }'
```

---

## § 5 — Git Workflow

### Commit Message Format
```
<type>(<scope>): <short summary>

<body>

Fixes beads-NNN
```
Types: `fix`, `security`, `refactor`, `test`, `docs`, `chore`

### Branch Strategy
- Work directly on `main` for small focused fixes.
- Create a branch for anything touching > 1 logical unit:
  ```bash
  git checkout -b fix/beads-NNN-short-description
  ```

---

## § 6 — Build & Test

```bash
# Build (requires Android SDK / ANDROID_HOME set)
./gradlew assembleDebug

# Unit tests (JVM only — no device needed)
./gradlew test

# Specific module tests
./gradlew :app:test
./gradlew :terminal-emulator:test
./gradlew :termux-shared:test

# Lint
./gradlew lint

# Full check
./gradlew check
```

---

## § 7 — Non-Interactive Shell Commands

Shell aliases often add `-i` (interactive) to `cp`, `mv`, `rm`, causing agents to hang
waiting for confirmation. **Always use force flags.**

```bash
# File operations
cp -f source dest           # NOT: cp source dest
mv -f source dest           # NOT: mv source dest
rm -f file                  # NOT: rm file
rm -rf directory            # NOT: rm -r directory
cp -rf source/ dest/        # NOT: cp -r source/ dest/

# Other potentially prompting commands
apt-get install -y <pkg>                   # -y skips confirmation
ssh -o BatchMode=yes host cmd             # Fail instead of prompting
scp -o BatchMode=yes src host:dest        # Same
HOMEBREW_NO_AUTO_UPDATE=1 brew install    # Skip update prompt
```

---

## § 8 — Session Close Protocol

**Work is NOT done until `git push` succeeds.** Complete all steps:

```bash
# 1. File issues for anything unfinished
bd create --title="..." --description="..." --type=task

# 2. Run quality gates (if code changed)
./gradlew test lint

# 3. Close completed issues
bd close <id1> <id2> ...

# 4. Commit and push
git status
git add -p                    # Stage thoughtfully
git commit -m "fix(scope): summary  Fixes beads-NNN"
git pull --rebase
bd dolt push                  # Push beads BEFORE git push
git push
git status                    # MUST show "up to date with origin"
```

**CRITICAL RULES:**
- NEVER stop before `git push` — stranded local commits lose work.
- NEVER say "ready to push when you are" — YOU must push.
- If push fails, resolve the conflict and retry until it succeeds.

---

## § 9 — Triage Seed

A static-analysis seed list is in `todo.md`. To convert items into `bd` issues:
```bash
bd create --title="Security: Zip Slip in TermuxInstaller" \
  --description="No canonical path validation at TermuxInstaller.java:182. See todo.md §1" \
  --type=bug --priority=0
```
Do NOT leave work only in `todo.md` — it is an archive, not a tracker.
