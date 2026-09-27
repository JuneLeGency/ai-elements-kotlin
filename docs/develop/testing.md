# Testing

```bash
./gradlew testDebugUnitTest lintDebug                 # unit and recorded-fixture tests, lint
./gradlew :demo:connectedDebugAndroidTest            # UI end to end on an emulator or device
```

## Recorded fixtures

Protocol behaviour is tested against responses recorded from real implementations (see
[Recording fixtures](reference-server.md#recording-fixtures)): AI SDK and AG-UI streams from the
Pydantic AI adapters, MCP elicitation from the official `mcp` SDK, A2UI surfaces checked by the
official `a2ui-core` processor, and Agent Client Protocol sessions replayed to the official ACP
Kotlin SDK.

## Live tests

Live tests run against real servers and are skipped unless you pass their address:

```bash
cd server && uv run uvicorn main:app --port 8788     # in another terminal

./gradlew :ai-elements-core:testDebugUnitTest --tests '*LiveHarnessServerTest*' -PliveAgentServer=http://localhost:8788
./gradlew :ai-elements-core:testDebugUnitTest --tests '*LiveMcpTest*' -PliveMcp=http://localhost:8788/mcp -PliveMcpLegacy=http://localhost:8790/mcp
./gradlew :ai-elements-core:testDebugUnitTest --tests '*LiveMcpOAuthTest*' -PliveMcpOAuth=http://127.0.0.1:8791/mcp
./gradlew :ai-elements-a2a:testDebugUnitTest -PliveA2a=http://localhost:8788
./gradlew :ai-elements-acp:testDebugUnitTest --tests '*LiveAcpTest*' \
    -PliveAcpCommand="uv run --directory server python acp_agent.py" -PliveAcp=ws://localhost:8788/acp
```

## End-to-end tests

`demo/src/androidTest` drives the demo app. Cases that need the reference server skip when it is
unreachable; pass its address with `-e agentServer http://<host>:8788` (the default is the
emulator's `10.0.2.2`). `ScreenshotMatrixTest` captures the same conversation in light and dark, in
every language (`-e screenshots true`); the screenshots on this site come from it.

!!! tip "Xiaomi / HyperOS devices"
    Instrumentation started in the background needs
    `adb shell appops set dev.ai.elements.demo 10021 allow` (reset with `… 10021 default`), and the
    device must stay awake and in portrait during the run.

## API reference

```bash
./gradlew :dokkaGenerate      # build/dokka/html
```
