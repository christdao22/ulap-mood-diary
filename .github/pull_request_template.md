<!--
Thank you for helping make Ulap kinder and better.

The rules behind this template live in README §12 (Contributing guidelines).
README owns them: if you change this checklist, change README §12 in the same pull request.

Title: we squash merge, so the PR title becomes the commit on main.
Write it as a Conventional Commit, e.g. "feat(checkin): add reset-to-default link".

Checklist: tick what you did. Strike through (~~like this~~) items that don't apply.
-->

## What changed and why

<!-- The problem, the change, and the doc section it implements, e.g. (technical-plan.md §8). -->

## PHASES.md task

<!-- The box this ticks, e.g. "Phase 2 › Local data › CheckInDao". Link the issue if there is one: Closes #123 -->

## Screenshots

<!-- UI changes: English and one other language (Filipino or Bisaya). A short video is fine. Write "No UI changes" if there are none. -->

## Tests

<!-- Tests added or updated. A bug fix comes with a test that fails before the fix (README §11). -->

## 🔒 Two approvals

- [ ] This touches auth, sync, encryption or safety (the hotline), so it needs **two approvals** before merging.

## Checklist

- [ ] `./gradlew build` passes
- [ ] New strings added to `values/`, `values-fil/` and `values-b+ceb/`
- [ ] TalkBack labels for new interactive elements
- [ ] Touch targets ≥ 48dp
- [ ] Tone follows the voice guide (validate → offer, no judgment words)
- [ ] No sensitive data in logs
- [ ] Docs updated if needed (follow the rules in README §17)
- [ ] Related task ticked in `PHASES.md`
