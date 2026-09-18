# Project Constitution — termux-app Bughunt

> This document is the authoritative guide for all contributors (human and AI) working in this
> repository. It defines our mission, principles, workflow rules, triage standards, and
> decision-making process. When in doubt, re-read this file.

---

## § 1 — Mission

This repository is a **security and quality bughunt fork** of
[`termux/termux-app`](https://github.com/termux/termux-app).

Our goal is to:
1. Systematically identify, reproduce, fix, and document bugs — especially security
   vulnerabilities — in the Termux Android application.
2. Produce clean, well-justified patches that can be submitted upstream.
3. Build a living record of findings so future contributors (human or AI) can pick up where
   we left off without losing context.

We do **not** maintain a distribution. We are a patch lab.

---

## § 2 — Core Principles

### 2.1 Security First
Security bugs are always the highest priority. A medium-complexity security fix blocks all
cosmetic or ergonomic work until resolved or explicitly deferred.

### 2.2 Upstream Compatibility
Every fix must be written as if it will be submitted to `termux/termux-app` as a pull request.
- Respect the existing code style (Java, Android SDK conventions).
- Do not introduce new dependencies without strong justification.
- Prefer minimal, surgical diffs over large refactors.

### 2.3 Evidence-Based Work
No issue gets created without a specific file, line number, and reproduction rationale.
No issue gets closed without a code change or a documented reason for deferral.

### 2.4 Persistent Context
Context lives in `bd` (beads), not in agent memories, markdown TODO lists, or chat history.
Use `bd remember` for cross-session insights. Use `bd create` for every actionable work item.

### 2.5 One Source of Truth per Domain
| Domain | Tool |
|---|---|
| Issue / task tracking | `bd` (beads) |
| Pull requests & repo metadata | `gh` (GitHub CLI) |
| Code history | `git` |
| Persistent knowledge | `bd remember` / `bd memories` |

---

## § 3 — Issue Taxonomy

### Severity Tiers (maps to `bd` priority)

| Priority | bd value | Examples |
|---|---|---|
| **Critical** | `0` / `P0` | RCE, zip-slip, arbitrary file write, intent hijack |
| **High** | `1` / `P1` | PendingIntent without FLAG_IMMUTABLE, path traversal |
| **Medium** | `2` / `P2` | NPE crash paths, missing null checks, logic errors |
| **Low** | `3` / `P3` | Code style, dead code, minor performance |
| **Backlog** | `4` / `P4` | Nice-to-haves, upstream-only concerns |

### Issue Types
- `bug` — Existing incorrect behavior
- `security` — Use type `bug` with priority ≤ 1
- `task` — Mechanical work (import, test scaffolding, docs)
- `feature` — New capability (rare in a bughunt repo)

---

## § 4 — Workflow

### 4.1 Starting a Work Session
```bash
bd prime                    # Recover full context
bd ready                    # Find unblocked work
bd show <id>                # Read the issue thoroughly
bd update <id> --claim      # Claim before touching code
```

### 4.2 Doing the Work
- Create a `bd` issue **before** writing code — always.
- Keep commits focused: one logical change per commit.
- Reference the issue ID in every commit message: `Fixes beads-NNN: <description>`.
- Run the build and any relevant tests before marking complete.

### 4.3 Completing a Work Session
```bash
bd close <id> [<id2> ...]   # Close completed issues
git add -p                  # Stage thoughtfully
git commit -m "..."         # Descriptive commit message
git pull --rebase           # Sync before push
bd dolt push                # Push beads state
git push                    # Push code — MANDATORY
git status                  # Must show "up to date"
```
Work is **not done** until `git push` succeeds. Never stop before this step.

### 4.4 Pull Requests (upstream patches)
```bash
gh pr create --title "Fix: <summary>" \
             --body "$(cat docs/pr-template.md)" \
             --base main \
             --repo termux/termux-app
```
PRs targeting upstream must:
- Reference the upstream issue number if one exists.
- Include a "Before / After" behavior description.
- Pass CI (build + unit tests).

---

## § 5 — Code Standards

### 5.1 Language & SDK
- Java only — no Kotlin unless the surrounding file is already Kotlin.
- `minSdkVersion=21`, `targetSdkVersion=28`, `compileSdkVersion=36`.
- Do not raise `targetSdkVersion` without explicit discussion.

### 5.2 Security Hygiene Checklist
Before submitting any fix, verify:
- [ ] `PendingIntent` flags include `FLAG_IMMUTABLE` (API ≥ 23) or `FLAG_MUTABLE` where required.
- [ ] File paths received from external sources are canonicalized and validated.
- [ ] `Intent` extras used in privileged operations are validated before use.
- [ ] Zip/archive extraction validates that each entry stays within the target directory.
- [ ] No `Log.d/v/i` calls leak sensitive data (tokens, paths, user content).

### 5.3 Test Expectations
- Unit tests live under `app/src/test/` or the module's `test/` equivalent.
- New security-critical logic must include at least one unit test covering the attack path.
- Run with: `./gradlew test` (JVM-only tests run without a device).

### 5.4 Commit Message Format
```
<type>(<scope>): <short summary>

<body — what changed and why>

Fixes beads-NNN
```
Types: `fix`, `security`, `refactor`, `test`, `docs`, `chore`.

---

## § 6 — Decision Rules

### 6.1 Fix vs. Defer
Fix immediately (same session) if:
- Priority 0 or 1 and the fix is ≤ 50 lines with no API risk.

Create a `bd` issue and defer if:
- Fix requires architectural changes.
- Fix affects public API surface used by Termux plugins.
- Human judgment is needed on behavior trade-offs — use `bd human <id>`.

### 6.2 When to Open a PR Upstream
Open upstream PR when:
- The fix is clean, tested, and passes CI.
- The change is general enough to benefit all Termux users.

Hold back if:
- The fix is still under review internally (leave as `in_progress`).
- Upstream has a conflicting open PR for the same issue.

### 6.3 Conflicting Guidance
If this constitution conflicts with `AGENTS.md`, AGENTS.md governs **tooling mechanics**.
This constitution governs **intent and priorities**.

---

## § 7 — Repository Layout

```
termux-app/
├── app/                    # Main application module
├── terminal-emulator/      # VT100/VT220 emulator (Apache 2.0)
├── terminal-view/          # Android View for terminal (Apache 2.0)
├── termux-shared/          # Shared library (GPLv3 + exceptions)
├── docs/                   # Extended documentation
├── .beads/                 # Beads issue tracker data (do not edit manually)
├── AGENTS.md               # AI agent tooling reference (mechanics)
├── CONSTITUTION.md         # This file (intent and principles)
├── CLAUDE.md               # Claude-specific context (auto-generated sections)
└── todo.md                 # Seed list from static analysis (archive; use bd instead)
```

### License
- Core app: **GPLv3 only**
- `terminal-emulator`, `terminal-view`: **Apache 2.0**
- `termux-shared`: see `termux-shared/LICENSE.md`

---

## § 8 — Amendment Process

This constitution may be amended by:
1. Creating a `bd` issue of type `task` titled `"Constitution: <change summary>"`.
2. Editing this file with a clear rationale comment.
3. Committing, pushing, and closing the issue.

No amendment requires a PR — this is a working document for this fork.

---

*Last updated: 2025-09 · Maintained by: 0xdeadcafe*
