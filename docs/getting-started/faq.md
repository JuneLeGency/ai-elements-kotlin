# Frequently asked questions

## Can I use only the UI?

Yes. `ai-elements-ui` depends on `ai-elements-chat`, not the networking or agent modules.
Render a single element, or supply your own `ChatBackend`.

## Do I need the reference server or Pydantic AI?

No. They are examples and test infrastructure. A compatible AI SDK / AG-UI / A2A / ACP server,
a model API, or your own backend can supply the messages. Start with [Choose your setup](choose.md).

## Why does the local endpoint fail?

Declare `INTERNET`; allow cleartext HTTP in the debug manifest only when testing an HTTP server.
The emulator reaches your computer through `10.0.2.2`, not its own `localhost`.
Check `/health`, the server bind address and firewall. See [App setup](pure-client.md#app-setup).

## Why does a tool wait forever for approval?

Use `Chat(controller)` or forward `Conversation.onToolDecision` to `controller.respondToApproval`.
Also forward `onInputResponse` for forms. A display-only tool card cannot answer the waiting backend.

## Will a ViewModel restore conversations after process death?

No. It preserves state across configuration changes. Your app must persist messages and pass them
as `initialMessages` when constructing a new controller after process death.

## Why are some modules minSdk 26?

Optional integrations and capabilities use newer platform APIs. See the per-artifact
[installation matrix](installation.md). The tested build toolchain is separate from runtime minSdk.

## Are dependencies all stable releases?

No. The current Material 3 Expressive and Compose versions include alpha releases. The exact tested
versions are listed in Installation. Our [compatibility policy](../develop/api-compatibility.md)
still applies to the library's stable public APIs.

## Can I copy a component example into my own app?

Yes: add its artifact and the printed imports, wrap it in `AiElementsTheme`, and provide the
state/callbacks named in the function parameters. The same examples compile in an independent
Maven consumer build. Screenshots show the Demo's display scenarios, not a separate web implementation.
