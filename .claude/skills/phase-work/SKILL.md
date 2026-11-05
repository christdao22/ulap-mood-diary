---
name: phase-work
description: Use when working through PHASES.md, e.g. "next task", "go", "continue Phase 1", or a named PHASES.md task. Work one task (or a few tightly related tasks) at a time, then stop and give a paste-ready commit message and a beginner-friendly recap of what was done and why.
---

# Working through PHASES.md

[PHASES.md](../../../PHASES.md) is the single tracker: build order, task boxes and progress. The owner knows TypeScript/Node better than Kotlin. Treat each task as a lesson as well as a task.

## Per task

1. **Pick the task.** Take the next unchecked box in PHASES.md, or the one the user names, and say which one it is before you start. If a task is too big for one sitting, split it into sub-tasks (2a, 2b, …), explain the split, and do only the first.
2. **Related tasks are fine.** Fold in a closely related task only when it touches the same files or would be awkward to do separately, for example a Room entity and its DAO. Say that you're combining them. Don't chain unrelated tasks.
3. **Check what blocks it.** If the task depends on a decision that is still Proposed (D15 onward in [docs/ulap-reference.md §13](../../../docs/ulap-reference.md#13-decision-log)), name the decision and ask before building. If the decision doesn't change this task, follow the current docs and say so. If the task is marked 🔒, say that it needs two reviewers before merging.
4. **Build.** Follow the phase's **Spec:** links. Use the `reference-coding` skill for non-trivial code. Use the agent that fits (CLAUDE.md lists them). Get a `wellbeing-reviewer` pass on any new or changed user-facing text.
5. **Verify.** Run the checks in CLAUDE.md's Commands section before calling the task done. Report failures with their output. If part of it can't be verified here (it needs a real phone, a GitHub remote, a native speaker), say so.
6. **Stop.** Don't start the next task until the user says "go" (or "keep going", which means continue without stopping).

## At the end of each task

### 1. Commit message

Make it ready to paste and match the repo's style (Conventional Commits, see `git log`):

```
type(scope): short imperative summary, phrased as the outcome

- area: what changed and why it matters
- docs: which doc was updated, if any
```

Types: `feat`, `fix`, `build`, `test`, `docs`, `refactor`, `chore`, `ci`. Scopes: the proposed list in [ulap-reference §10](../../../docs/ulap-reference.md#10-conventions) (e.g. `android`, `server`, `shared`, `sync`, `checkin`, `phases`).

The user runs the commit. Don't commit unless they ask.

### 2. Recap: what I did

Write it for an Android beginner, plainly and briefly, under these headings:

- **What changed**: each file created or changed, as a link, one line each.
- **Why**: the problem this solves, and the doc section it implements.
- **New concepts**: each Android, Kotlin, Gradle, Ktor or tooling idea that appears for the first time, in a few sentences. Compare it to its TypeScript/Node counterpart where one exists. For example, a Gradle version catalog is like a shared dependency list in `package.json`, a Hilt module is like registering providers in a DI container, `suspend fun` is like `async function`, and a Ktor plugin is like Hono middleware. Skip concepts that earlier tasks already explained.
- **Try it yourself**: the commands to run, or what to click in Android Studio, to see it working.
- **Left open**: anything deferred, skipped or unverified, and which task will pick it up.
- **Next**: the next task.

### 3. Tick the box

Tick it only when the [Definition of Done](../../../PHASES.md#definition-of-done) is met, including its Phase 1 exception. When every rule is met except the commit itself (Phase 1) or the merged pull request (later phases), tick it in the same change, because committing or merging completes it. The README §12 PR checklist asks for the tick inside the PR. If anything else is missing (CI, a native-speaker review, a second reviewer), leave the box unticked and list the missing piece under **Left open**.

When you tick a box, update that phase's **Done** count and **Status** in the progress overview, as "How to update this file" at the end of PHASES.md describes.

### 4. Record progress

Overwrite the Status / Next / Open lines in the `phase-work-style` memory so the next session starts in the right place. Replace, don't append. Keep that file under ~1.5 KB. Git log is the history.

Then suggest the user run `/clear` before the next task, so it starts with a small context.
