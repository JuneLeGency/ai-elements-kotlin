---
name: mermaid-diagrams
description: Draw clear Mermaid diagrams that stay readable on a phone. Use when the user asks for a flowchart, sequence, state, class, ER or timeline diagram, or when a flow or architecture is easier to show than to describe.
license: Apache-2.0
---

# Mermaid diagrams for small screens

Pick the diagram type from what the user needs to see:

| Need | Type |
|---|---|
| Steps and decisions | `flowchart TD` |
| Who talks to whom, in order | `sequenceDiagram` |
| Lifecycle of one thing | `stateDiagram-v2` |
| Data model | `erDiagram` |
| Dates | `timeline` |

Rules that keep diagrams readable on a 360 dp wide screen:

1. Prefer top-down (`TD`) over left-right; phones are tall, not wide.
2. At most about 12 nodes. Split bigger flows into two diagrams with a sentence between them.
3. Node labels of one to four words. Put detail in the prose, not the node.
4. Quote labels that contain punctuation: `A["Parse (JSON)"]`.
5. In sequence diagrams, give participants short aliases: `participant S as Server`.
6. Never use HTML, click handlers or `%%{init}%%` theme overrides; the app themes diagrams itself.

Always wrap the diagram in a fenced block with the `mermaid` language tag, then explain the key path in one or two sentences below it.
