# In-app agent

`harness-core`'s `AgentHarness` runs an agent on the device: a model binding plus capabilities
(instructions and tools) becomes a `ChatBackend`. Capabilities mirror
[Pydantic AI Harness](https://github.com/pydantic/pydantic-ai-harness), with the same tool names,
arguments and behaviour, so on-device and server agents render identically.

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:in-app-agent"
```

## Capabilities

| Capability | Artifact | Tools |
|---|---|---|
| `FileSystem` | `harness-filesystem` | `read_file`, `write_file`, `edit_file`, `list_directory`, `search_files`, `find_files`, `create_directory`, `file_info` |
| `Shell` + `AlpineSandbox` | `harness-shell`, `harness-sandbox-proot` | `run_command`, `start_command`, `check_command`, `stop_command` |
| `Memory` | `harness-memory` | `write_memory`, `read_memory`, `delete_memory`, `search_memory` |
| `Planning` | `harness-planning` | `write_plan`, `read_plan`, `add_task`, `update_task_status(es)`, `remove_task` |
| `WebBrowser` | `harness-browser` | `navigate`, `snapshot`, `click`, `type_text`, `get_text`, `screenshot`, … |
| `DeviceTools` | `harness-device` | device info, clipboard, calendar, contacts, location, alarms, notifications |
| `Speech` | `harness-speech` | `speak`, `stop_speaking` |
| `Scheduler` | `harness-scheduler` | `schedule_task`, `list_scheduled_tasks`, `cancel_scheduled_task` |
| `Skills` | `ai-elements-core` | `load_capability` |
| `AskUser` | `ai-elements-core` | `ask_user_question` |
| `McpToolset` | `ai-elements-core` | the tools of the user's MCP servers |

A `Capability` is instructions plus tools; write your own by implementing it, or pass plain
`AgentTool`s.

## Sub-agents

- `localSubAgents`: agents with their own instructions (and optionally another model) that the model
  delegates to with `delegate_task(agent_name, task)`. The roster is a static instruction.
- `remoteSubAgents`: ready-made agents, for example [A2A agents](../protocols/a2a.md#as-a-sub-agent).

Delegations render as `Subagent` cards with the nested run.

## Skills

[Agent Skills](https://agentskills.io) are folders with a `SKILL.md` (YAML frontmatter plus
instructions). `Skills` lists them in the instructions and the model loads one with
`load_capability(id)`, which returns `# Skill: <name>` and the body. Load them from assets, files or
a `.zip`; the demo bundles the skills in the repository's `skills/` folder.

## Files and folders

`FileSystem` works in the app's workspace and in folders the user shares through the Storage Access
Framework (`SharedFolders`), mounted at `/mnt/<name>`. Writes ask for approval.

## The browser

`WebBrowser` gives the agent a browser on an off-screen `WebView`, with the tools of the Pydantic AI
Harness browser: `navigate`, `snapshot` (interactive elements with `aria-ref` handles), `click`,
`type_text`, `press_key`, `select_option`, `hover`, `wait_for`, `get_text`, `scroll`, `go_back`,
`go_forward` and `screenshot`. Only `http(s)` pages load.

The browser presents a desktop page by default (`BrowserViewport.Desktop`: 1280 × 720 CSS pixels,
Playwright's default viewport, which the Harness browser uses). Desktop pages give agents more per
screen, and sites serve their full layout. The browser asks for it the way Chrome's "Desktop site"
does, with Chrome's desktop user agent at the WebView's version. Pass `BrowserViewport.Mobile` for
the phone layout.

Screenshots are taken at CSS-pixel resolution, so an `x,y` the model reads off an image is where
`click("x,y")` lands. The step view opens a screenshot full screen to zoom on a phone.

Screenshots go to two places:

- **To the model.** `screenshot(full_page?)` returns the viewport, or the whole page, as a PNG for
  vision models, and `screenshotOnNavigate = true` adds one to every `navigate`. Both follow the
  Harness contract: the text says `Screenshot captured. URL: …` and the image goes to the model as
  Pydantic AI `ToolReturn.content`. On-device model loops send it in a user message after the tool
  results, which works with every model API. Images over 5 MB become a short error instead.
- **To the user.** With `screenshots = true` (the default), every call that changes the page also
  attaches a smaller JPEG of the viewport for the agent's computer view. The element the call acted
  on is outlined, the way agent computer views such as Manus mark each action. The model does not
  get these.

A tool of your own returns an image to the model the same way, with
`ToolCallContext.current()?.content(mediaType, url)`.

The demo's offline agent shows a scripted browser-use run without a model. Tap **Browse a web page**:
it opens a page, reads it with `snapshot`, follows a link and takes a `screenshot`. Each step then
appears on the agent's computer.

## The Linux sandbox

`AlpineSandbox` runs Alpine Linux through PRoot, with the workspace at `/workspace`. The agent can
install packages with `apk add` and run code. The root filesystem (about 4 MB, pinned and
checksum-verified) downloads on first use. Package native libraries extracted
(`jniLibs.useLegacyPackaging = true`).

## Scheduled runs

`Scheduler` lets the agent schedule background runs on WorkManager. Implement `ScheduledAgentHost`
on your `Application` so a run can build its agent while the app is closed (`runHeadless`).

## Model bindings

The model is a `ModelBinding`: `ProviderProfile.model(apiKey = …)` for the built-in loop, or
`ai-elements-koog` to run the loop on JetBrains Koog. See [Model APIs](../protocols/model-apis.md).
