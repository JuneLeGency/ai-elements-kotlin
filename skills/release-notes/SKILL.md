---
name: release-notes
description: Turn a list of changes, commits or a diff summary into user-facing release notes. Use when the user asks for release notes, a changelog entry or "what's new".
license: Apache-2.0
---

# Release notes

Write for users of the product, not for its developers.

1. Group changes under these headings, in this order, and omit empty ones:
   - **New** — features people can try.
   - **Improved** — existing things that got better.
   - **Fixed** — bugs users could have hit.
   - **Breaking** — anything that needs action, with the action.
2. One bullet per change, starting with a verb in the past tense ("Added", "Fixed").
3. Say what changed for the user and why it matters; leave out file names, ticket numbers and internal refactors.
4. If a change needs migration, add a short code block showing before and after.
5. End with a single-line summary suitable for an app-store "What's new" field (under 170 characters).

Match the language the user writes in.
