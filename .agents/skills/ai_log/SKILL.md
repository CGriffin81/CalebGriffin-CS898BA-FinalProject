---
name: ai_log
description: >
  AI_Log.md table logging skill. Use when user wants prompt history recorded in
  AI_Log.md, when a prompt must be appended as a new row, or when the log format
  needs to stay table-based with consistent columns.
---

Always append new prompt entry to end of AI_Log.md table.

## Format

- Date/Time: CST timestamp for current prompt.
- AI Tool: include model name, for example `GitHub Copilot (GPT-5.4 mini)`.
- Prompt Type: short label for request kind.
- Full Prompt: full user prompt text.
- Result Synopsis: short outcome summary.
- Design/Code Changes: short description of file or content changes.

## Behavior

- Preserve table structure.
- Add one row per prompt.
- Keep newest row at bottom.
- Use the active project context when describing result and changes.
