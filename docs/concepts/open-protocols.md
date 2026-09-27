# Open protocols only

Everything that crosses a process boundary follows a public, versioned specification, exactly as
specified. The library is a client of those specifications, not a protocol of its own.

| Concern | Specification |
|---|---|
| Agent ↔ UI streaming | Vercel AI SDK UI Message Stream (v5 / v6), v4 Data Stream; AG-UI 1.x |
| Tools and context from servers | MCP 2026-07-28 Streamable HTTP, with fallback to the 2025-xx session revisions |
| Interactive tool views | MCP Apps 2026-01-26 |
| Generative UI | A2UI v1.0 |
| Agent ↔ agent | A2A 1.0 JSON-RPC, with 0.3 compatibility |
| Editor-style clients of coding agents | Agent Client Protocol v1 |
| Skills | Agent Skills (`SKILL.md` + YAML frontmatter) |
| Sign-in | OAuth 2.1, RFC 6749, 7636, 8252, 8628, 8414, 9728, 7591, 8707 |
| Model APIs | OpenAI Chat Completions / Responses, Anthropic Messages, Gemini, Ollama |

## Rules

1. **No invented wire formats.** No custom event types, ad-hoc JSON envelopes, bespoke headers or
   custom SSE framing. A feature that seems to need one uses the spec's extension point instead:
    - AI SDK: `data-*` parts, `message-metadata`, preliminary tool output, tool approval;
    - AG-UI: `STATE_SNAPSHOT` / `STATE_DELTA`, `ACTIVITY_*`, `SUBAGENT_*`, interrupts and
      `RunAgentInput.resume`, `CUSTOM` as a last resort;
    - MCP: `_meta`, tool annotations, `notifications/progress`;
    - A2A: `metadata`, `DataPart`, artifacts;
    - ACP: `_meta`.
2. **The current revision of each spec**, with the documented fallbacks for older peers.
3. **Internal types are mappings, not protocols.** `ChatEvent`, `Message` / `Part` and
   `ToolCallContext` are in-process models that each backend maps its spec onto. They are never
   serialized onto the network.
4. **Official SDKs and reference implementations** over hand-written code: the AG-UI `kotlin-core`
   types, `a2a-java-sdk`, the ACP Kotlin SDK; on the server, Pydantic AI and Pydantic AI Harness with
   the official `mcp`, `a2a-sdk` and ACP SDKs.
5. **Every protocol change ships with a fixture test** recorded from a real implementation, plus a
   live test when a public endpoint or the reference server can exercise it.

## Server-side conventions

On-device capabilities mirror [Pydantic AI Harness](https://github.com/pydantic/pydantic-ai-harness),
so a server agent and an on-device agent render identically:

- sub-agents: one `delegate_task(agent_name, task)` tool, with the roster as a static instruction;
- skills: `load_capability(id)`, returning `# Skill: <name>` and the body;
- capabilities: instructions plus tools.

## Security defaults

Tools that change data require approval unless the user opts out. MCP tool annotations are
untrusted hints. PKCE `S256` is mandatory. Secrets are never logged.
