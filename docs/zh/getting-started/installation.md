# 安装与环境

这是 Android / Jetpack Compose 库，不是 Kotlin Multiplatform 库。使用 BOM 统一各模块版本，然后按需引入。

```kotlin title="settings.gradle.kts"
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // 首次公开发布前，在 publishToMavenLocal 后使用：
        mavenLocal()
    }
}
```

```kotlin title="build.gradle.kts"
dependencies {
    implementation(platform("io.github.junelegency:ai-elements-bom:0.3.0-SNAPSHOT"))
    implementation("io.github.junelegency:ai-elements-ui")
    implementation("io.github.junelegency:ai-elements-core")
}
```

!!! note "当前仍为 Snapshot"
    `0.3.0` 尚未公开发布。当前请先在仓库运行 `./gradlew publishToMavenLocal`，并使用 `mavenLocal()`。
    正式发布后会同步更新这里的坐标与安装步骤。

## 环境要求

经过消费工程验证的工具链：JDK 21、Gradle 9.8.0、AGP 9.4.1、Kotlin 2.4.20、compileSdk 37.2、Java 17 字节码。
更旧的消费端构建工具链尚未认证。Compose 1.13.0-alpha03 和 Material 3 1.5.0-alpha29 是预发布依赖；
BOM 对齐的是本库模块，不会替应用统一所有 AndroidX 版本。

启用 Compose compiler 插件和 `buildFeatures.compose = true`，activity 示例还需要
`androidx.activity:activity-compose:1.13.0`。ViewModel 示例使用
`androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0` 与 `lifecycle-runtime-compose:2.11.0`。

## 模块一览

下表前 11 项的 group 为 `io.github.junelegency`。

| 模块 | 职责 | minSdk |
|---|---|---|
| ai-elements-chat | 消息模型、事件、backend、controller；无网络、无 Compose | 24 |
| ai-elements-core | 协议客户端、模型 API、Agent 能力、MCP、OAuth | 24 |
| ai-elements-ui | Compose 组件与主题，只依赖 chat | 24 |
| ai-elements-genui | 原生 A2UI 和 JSX | 26 |
| ai-elements-mcp-apps | MCP Apps 的受限 WebView 与标准交互桥接 | 24 |
| ai-elements-notifications | 后台运行进度及完成通知 | 24 |
| ai-elements-a2a | 官方 Java SDK 上的 A2A 集成，需要 desugaring | 26 |
| ai-elements-acp | 官方 Kotlin SDK 上的 ACP 集成 | 24 |
| ai-elements-koog | JetBrains Koog 适配 | 26 |
| ai-elements-mermaid-native | 实验性原生 Mermaid 渲染器 | 24 |
| ai-elements-bom | 对齐所有模块版本 | — |

以下模块的 group 为 `io.github.junelegency.harness`。

| 模块 | 职责 | minSdk |
|---|---|---|
| harness-core | 把模型和能力组合为 ChatBackend | 24 |
| harness-filesystem | 工作区文件和用户分享的 SAF 目录 | 26 |
| harness-memory | 跨会话记忆 | 26 |
| harness-planning | 任务计划 | 26 |
| harness-shell | 可插拔命令运行时 | 26 |
| harness-sandbox-proot | PRoot / Alpine Linux 沙箱 | 26 |
| harness-browser | WebView 浏览与页面操作 | 26 |
| harness-device | 剪贴板、日历、联系人、定位等设备能力 | 26 |
| harness-speech | 平台 TTS 朗读 | 26 |
| harness-scheduler | WorkManager 后台计划任务 | 26 |

## A2A 的 desugaring

```kotlin
android {
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    packaging.resources.excludes += listOf(
        "META-INF/NOTICE.md", "META-INF/LICENSE.md", "META-INF/INDEX.LIST",
        "META-INF/DEPENDENCIES", "META-INF/beans.xml",
    )
}
dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")
}
```

处理重复元数据时仍要保留依赖的许可证声明，详见仓库 NOTICE。PRoot 是独立可选模块，其可执行文件遵循 GPL-2.0。
使用它时还需 `android { packaging { jniLibs.useLegacyPackaging = true } }`。
库会附带自己的 R8 consumer rules；[独立消费工程](https://github.com/JuneLeGency/ai-elements-kotlin/tree/main/samples/published-consumer)
验证通过 Maven 坐标接入及 R8 release 构建。
