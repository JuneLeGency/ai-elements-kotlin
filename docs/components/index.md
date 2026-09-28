# Components

Every element with the sample the demo app's **Components** screen shows, grouped as there.
The pictures are rendered from that code by a test, so they show what the library draws.
Each entry names its main API; the [API reference](../api/index.html) has every parameter.

| Group | Components |
|---|---|
| [Conversation](conversation.md) | [Conversation](conversation.md#conversation), [Messages](conversation.md#messages), [Prompt input](conversation.md#prompt-input), [Suggestions](conversation.md#suggestions), [Empty state](conversation.md#empty-state), [Branch](conversation.md#branch), [Checkpoint](conversation.md#checkpoint), [Queue](conversation.md#queue), [Open in chat](conversation.md#open-in-chat), [Model selector](conversation.md#model-selector), [Context (token usage)](conversation.md#context-token-usage), [Loading indicators](conversation.md#loading-indicators), [Shimmer](conversation.md#shimmer) |
| [Message content](content.md) | [Markdown](content.md#markdown), [Code block](content.md#code-block), [Math (KaTeX)](content.md#math-katex), [Mermaid · flowchart](content.md#mermaid-flowchart), [Mermaid · sequence](content.md#mermaid-sequence), [Mermaid · class](content.md#mermaid-class), [Mermaid · pie](content.md#mermaid-pie), [Mermaid · streaming](content.md#mermaid-streaming), [Reasoning](content.md#reasoning), [Sources](content.md#sources), [Inline citation](content.md#inline-citation) |
| [Tools and the agent's computer](tools.md) | [Tool calls](tools.md#tool-calls), [Sub-agents](tools.md#sub-agents), [Agent's computer](tools.md#agents-computer), [Agent's computer · in a reply](tools.md#agents-computer-in-a-reply), [Step views](tools.md#step-views), [Agent](tools.md#agent) |
| [Human in the loop](human-in-the-loop.md) | [Confirmation (tool approval)](human-in-the-loop.md#confirmation-tool-approval), [Question](human-in-the-loop.md#question), [Input request (form)](human-in-the-loop.md#input-request-form) |
| [Agent structure](agent-structure.md) | [Plan](agent-structure.md#plan), [Task](agent-structure.md#task), [Chain of thought](agent-structure.md#chain-of-thought), [Data parts (data-plan, data-task)](agent-structure.md#data-parts-data-plan-data-task) |
| [Generative UI](generative-ui.md) | [JSX · Text and layout](generative-ui.md#jsx-text-and-layout), [JSX · Form with bindings and an action](generative-ui.md#jsx-form-with-bindings-and-an-action), [JSX · Choices, slider, date and tabs](generative-ui.md#jsx-choices-slider-date-and-tabs), [JSX · A row of cards from data](generative-ui.md#jsx-a-row-of-cards-from-data), [JSX · Streaming](generative-ui.md#jsx-streaming), [A2UI surface](generative-ui.md#a2ui-surface), [Artifact](generative-ui.md#artifact), [Web preview](generative-ui.md#web-preview) |
| [Attachments and media](attachments-and-media.md) | [Image](attachments-and-media.md#image), [Attachments](attachments-and-media.md#attachments), [Video](attachments-and-media.md#video), [Document (PDF)](attachments-and-media.md#document-pdf), [Audio player + Transcription](attachments-and-media.md#audio-player-transcription) |
| [Voice](voice.md) | [Voice mode](voice.md#voice-mode), [Persona](voice.md#persona), [Speech input](voice.md#speech-input), [Mic & voice selectors](voice.md#mic-voice-selectors) |
| [Developer tools](developer-tools.md) | [Terminal](developer-tools.md#terminal), [Stack trace](developer-tools.md#stack-trace), [Test results](developer-tools.md#test-results), [File tree](developer-tools.md#file-tree), [Commit](developer-tools.md#commit), [Schema display](developer-tools.md#schema-display), [Package info](developer-tools.md#package-info), [Environment variables](developer-tools.md#environment-variables), [Sandbox](developer-tools.md#sandbox), [Snippet](developer-tools.md#snippet) |
| [Workflow](workflow.md) | [Canvas / Node / Edge](workflow.md#canvas-node-edge) |

Where the names differ from [Vercel AI Elements](https://elements.ai-sdk.dev), each entry says
which of its components it corresponds to.

!!! note "MCP Apps"
    Interactive views of MCP tools render in a sandboxed WebView and need their MCP server, so they
    are not in this catalog. See [MCP Apps](../protocols/mcp-apps.md); the demo app shows them with the
    reference server.
