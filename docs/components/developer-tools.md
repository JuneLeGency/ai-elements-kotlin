# Developer tools

Elements for coding agents: terminals, stack traces, tests, files, commits and more.

## Terminal

Command output as a terminal shows it: colours, progress bars, links.

![Terminal](../assets/components/terminal.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `Terminal(output, title, status, exitCode)` |
| AI Elements | `Terminal` |

## Stack trace

A stack trace with app frames first and library frames folded.

![Stack trace](../assets/components/stack-trace.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `StackTrace(trace)` |
| AI Elements | `StackTrace` |

## Test results

Test suites with passed, failed and skipped cases.

![Test results](../assets/components/test-results.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `TestResults(suites, durationMs)` |
| AI Elements | `TestResults` |

## File tree

A project's files, with change badges.

![File tree](../assets/components/file-tree.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `FileTree(nodes, expanded, selectedPath, onSelect)` |
| AI Elements | `FileTree` |

## Commit

A commit with its message and changed files.

![Commit](../assets/components/commit.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `Commit(hash, message, author, timestampMs, files)` |
| AI Elements | `Commit` |

## Schema display

An API endpoint: parameters, request and response.

![Schema display](../assets/components/schema-display.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `SchemaDisplay(method, path, parameters, requestBody, responseBody)` |
| AI Elements | `SchemaDisplay` |

## Package info

A dependency change and its size.

![Package info](../assets/components/package-info.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `PackageInfo(name, fromVersion, toVersion, change)` |
| AI Elements | `PackageInfo` |

## Environment variables

Environment variables with secrets masked until revealed.

![Environment variables](../assets/components/environment-variables.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `EnvironmentVariables(variables)` |
| AI Elements | `EnvironmentVariables` |

## Sandbox

Code and its output in tabs.

![Sandbox](../assets/components/sandbox.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `Sandbox(title, tabs)` |
| AI Elements | `Sandbox` |

## Snippet

A one-line command to copy.

![Snippet](../assets/components/snippet.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `Snippet(text, prefix)` |
| AI Elements | `Snippet` |
