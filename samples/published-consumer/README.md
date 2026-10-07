# Published artifact consumers

This is a separate Gradle build, deliberately not included by the main settings file.
It shares the tested toolchain version catalog and the two minimal app source files, but has
**no project dependencies, composite substitution or Maven Local fallback**. All AI Elements
coordinates resolve exclusively from the repository passed with `-PartifactRepository`.

From the repository root:

```bash
tools/check-published-consumer.sh
# Or validate a candidate version without publishing remotely:
tools/check-published-consumer.sh 0.3.0
```

The script publishes to `build/consumer-repository`, then compiles and shrinks release apps:

| App | Purpose |
|---|---|
| `ui-only` | Only UI + transitive chat, offline backend, minSdk 24 |
| `pure-client` | Minimal AG-UI app, minSdk 24 |
| `in-app-agent` | Minimal harness with filesystem and planning, minSdk 26 |
| `integrations` | Resolves every optional artifact together; desugaring, native packaging and R8 |

Use a JDK 21 and the Android SDK specified in Installation. Set `ANDROID_HOME` (or an untracked
`local.properties` in this directory). The reference server is needed only to run the pure-client
sample, not to compile it. The in-app agent needs the local Ollama model described in `samples/README.md`.
