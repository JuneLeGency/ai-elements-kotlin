# 开发者工具

终端、堆栈、测试、文件、提交与依赖信息。

## 终端输出 { #terminal }

展示命令输出的颜色、进度重绘和链接。

![终端输出](../assets/components/terminal.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `Terminal(output, title, status, exitCode)` |
| AI Elements | `Terminal` |
| 依赖模块 | `ai-elements-ui` |
| API 文档 | [Terminal](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.code/-terminal.html) |

传入累计输出。支持 ANSI 样式和有界滚动历史；这是输出视图，不是可交互终端。

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.ui.code.Terminal
import dev.ai.elements.ui.code.TerminalStatus
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-terminal"
```

## 异常堆栈 { #stack-trace }

区分应用与依赖的堆栈帧，支持折叠。

![异常堆栈](../assets/components/stack-trace.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `StackTrace(trace)` |
| AI Elements | `StackTrace` |
| 依赖模块 | `ai-elements-ui` |
| API 文档 | [StackTrace](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.code/-stack-trace.html) |

传入原始堆栈文字。组件只展示信息，不会上报崩溃报告。

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.ui.code.StackTrace
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-stack-trace"
```

## 测试结果 { #test-results }

展示测试套件中的成功、失败与跳过项目。

![测试结果](../assets/components/test-results.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `TestResults(suites, durationMs)` |
| AI Elements | `TestResults` |
| 依赖模块 | `ai-elements-ui` |
| API 文档 | [TestResults](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.code/-test-results.html) |

提供实际结果和毫秒耗时；组件本身不会运行测试。

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.ui.code.TestCaseResult
import dev.ai.elements.ui.code.TestResults
import dev.ai.elements.ui.code.TestStatus
import dev.ai.elements.ui.code.TestSuiteResult
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-test-results"
```

## 文件树 { #file-tree }

展示项目目录、选中文件和变更标记。

![文件树](../assets/components/file-tree.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `FileTree(nodes, expanded, selectedPath, onSelect)` |
| AI Elements | `FileTree` |
| 依赖模块 | `ai-elements-ui` |
| API 文档 | [FileTree](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.code/-file-tree.html) |

节点由调用方提供，选择节点不会读取文件。需要初始展开状态时传入 expanded 路径集合。

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.ai.elements.ui.code.FileNode
import dev.ai.elements.ui.code.FileTree
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-file-tree"
```

## 提交信息 { #commit }

展示 Git 提交说明和文件变更。

![提交信息](../assets/components/commit.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `Commit(hash, message, author, timestampMs, files)` |
| AI Elements | `Commit` |
| 依赖模块 | `ai-elements-ui` |
| API 文档 | [Commit](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.code/-commit.html) |

哈希、说明与文件列表来自你的源码管理工具；该卡片不会执行 Git 操作。

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.ui.code.Commit
import dev.ai.elements.ui.code.CommitFile
import dev.ai.elements.ui.code.FileChange
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-commit"
```

## 接口结构 { #schema-display }

展示接口参数、请求体和响应示例。

![接口结构](../assets/components/schema-display.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `SchemaDisplay(method, path, parameters, requestBody, responseBody)` |
| AI Elements | `SchemaDisplay` |
| 依赖模块 | `ai-elements-ui` |
| API 文档 | [SchemaDisplay](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.code/-schema-display.html) |

这是结构说明，不会验证或调用接口。requestBody 与 responseBody 是示例文字。

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.ui.code.SchemaDisplay
import dev.ai.elements.ui.code.SchemaParameter
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-schema-display"
```

## 依赖信息 { #package-info }

展示依赖版本变化与大小。

![依赖信息](../assets/components/package-info.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `PackageInfo(name, fromVersion, toVersion, change)` |
| AI Elements | `PackageInfo` |
| 依赖模块 | `ai-elements-ui` |
| API 文档 | [PackageInfo](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.code/-package-info.html) |

变更分类和大小由应用提供；组件不执行依赖解析或升级。

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.ui.code.PackageChange
import dev.ai.elements.ui.code.PackageInfo
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-package-info"
```

## 环境变量 { #environment-variables }

展示环境变量，并默认遮挡敏感值。

![环境变量](../assets/components/environment-variables.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `EnvironmentVariables(variables)` |
| AI Elements | `EnvironmentVariables` |
| 依赖模块 | `ai-elements-ui` |
| API 文档 | [EnvironmentVariables](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.code/-environment-variables.html) |

secret 只影响视觉遮挡，不提供加密。示例和截图中不要放真实凭据。

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.ui.code.EnvironmentVariable
import dev.ai.elements.ui.code.EnvironmentVariables
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-environment-variables"
```

## 代码与运行结果 { #sandbox }

通过标签页展示代码及其输出。

![代码与运行结果](../assets/components/sandbox.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `Sandbox(title, tabs)` |
| AI Elements | `Sandbox` |
| 依赖模块 | `ai-elements-ui` |
| API 文档 | [Sandbox](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.code/-sandbox.html) |

该组件只提供展示。实际执行由 harness-shell 或你的 backend 完成。

```kotlin
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.ai.elements.ui.code.Sandbox
import dev.ai.elements.ui.code.SandboxTab
import dev.ai.elements.ui.markdown.CodeBlock
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-sandbox"
```

## 可复制命令 { #snippet }

展示一行便于复制的命令。

![可复制命令](../assets/components/snippet.webp){ loading=lazy width="400" }

| | |
|---|---|
| API | `Snippet(text, prefix)` |
| AI Elements | `Snippet` |
| 依赖模块 | `ai-elements-ui` |
| API 文档 | [Snippet](/ai-elements-kotlin/api/ai-elements-ui/dev.ai.elements.ui.code/-snippet.html) |

点击复制不会执行命令。

```kotlin
import androidx.compose.runtime.Composable
import dev.ai.elements.ui.code.Snippet
```

```kotlin
--8<-- "demo/src/main/kotlin/dev/ai/elements/demo/samples/DocsSamples.kt:component-snippet"
```

[Read this page in English](/ai-elements-kotlin/components/developer-tools/)
