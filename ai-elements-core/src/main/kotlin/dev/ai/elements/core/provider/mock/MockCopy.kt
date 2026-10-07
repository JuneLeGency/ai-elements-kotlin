package dev.ai.elements.core.provider.mock

import java.util.Locale

/**
 * What the offline agent says, in the language of the prompt, as a real model answers in kind:
 * Japanese when the prompt has kana, Chinese (Traditional or Simplified, by its characters and
 * then by `locale`) when it has Han characters, English otherwise.
 */
internal class MockCopy private constructor(val language: Language) {
    enum class Language { EN, ZH_HANS, ZH_HANT, JA }

    private fun pick(en: String, hans: String, hant: String, ja: String) = when (language) {
        Language.EN -> en
        Language.ZH_HANS -> hans
        Language.ZH_HANT -> hant
        Language.JA -> ja
    }

    val planTitle get() = pick("Answer plan", "回答计划", "回答計畫", "回答プラン")
    val planSteps get() = listOf(
        pick("Think about the request", "理解问题", "理解問題", "リクエストを理解する"),
        pick("Call a tool", "调用工具", "呼叫工具", "ツールを呼び出す"),
        pick("Write the answer", "撰写回答", "撰寫回答", "回答を書く"),
    )
    fun planDescription(steps: Int) = pick("$steps steps", "$steps 个步骤", "$steps 個步驟", "$steps ステップ")

    fun reasoning(prompt: String, intent: Intent, tool: String?) = pick(
        "The user asked: \"$prompt\". I should " + when (intent) {
            Intent.NAMED -> "call the $tool tool the user named"
            Intent.COPY -> "copy the text to the clipboard, which needs the user's approval"
            Intent.MATH -> "evaluate the expression with the calculator tool"
            Intent.TIME -> "check the current time with a tool"
        } + ", then answer with a structured Markdown overview and a Mermaid diagram of the agent loop.",
        "用户问：“$prompt”。我应该" + when (intent) {
            Intent.NAMED -> "调用用户指定的 $tool 工具"
            Intent.COPY -> "把文字复制到剪贴板，这需要用户确认"
            Intent.MATH -> "用计算器工具算出这个表达式"
            Intent.TIME -> "先用工具查一下当前时间"
        } + "，然后用结构化的 Markdown 和一张智能体循环的 Mermaid 图来回答。",
        "使用者問：「$prompt」。我應該" + when (intent) {
            Intent.NAMED -> "呼叫使用者指定的 $tool 工具"
            Intent.COPY -> "把文字複製到剪貼簿，這需要使用者確認"
            Intent.MATH -> "用計算機工具算出這個運算式"
            Intent.TIME -> "先用工具查詢目前時間"
        } + "，然後用結構化的 Markdown 和一張代理循環的 Mermaid 圖來回答。",
        "ユーザーの質問：「$prompt」。" + when (intent) {
            Intent.NAMED -> "指定された $tool ツールを呼び出し"
            Intent.COPY -> "テキストをクリップボードにコピーし（ユーザーの承認が必要）"
            Intent.MATH -> "計算ツールで式を評価し"
            Intent.TIME -> "ツールで現在時刻を確認し"
        } + "、構造化した Markdown とエージェントループの Mermaid 図で答えます。",
    )

    enum class Intent { NAMED, COPY, MATH, TIME }

    val copiedText get() = pick("Hello from the AI Elements agent", "来自 AI Elements 智能体的问候", "來自 AI Elements 代理的問候", "AI Elements エージェントからこんにちは")

    fun answer(tool: String, result: String): String {
        val heading = pick("Offline agent demo", "离线智能体演示", "離線代理示範", "オフラインエージェントのデモ")
        val said = pick(
            "The agent called **`$tool`** and got `$result`.",
            "智能体调用了 **`$tool`**，结果是 `$result`。",
            "代理呼叫了 **`$tool`**，結果是 `$result`。",
            "エージェントは **`$tool`** を呼び出し、`$result` を得ました。",
        )
        val happened = pick("What just happened", "刚才发生了什么", "剛才發生了什麼", "いま起きたこと")
        val steps = pick(
            "1. **Reasoning** streamed into the collapsible *Thinking* block.\n2. A **tool call** ran on-device and its input/output are shown above.\n3. This answer streams as GitHub-flavoured Markdown.",
            "1. **推理过程**流式显示在可折叠的“思考”区块中。\n2. **工具调用**在本机运行，输入和输出显示在上方。\n3. 这段回答以 GitHub 风格的 Markdown 流式输出。",
            "1. **推理過程**串流顯示在可收合的「思考」區塊中。\n2. **工具呼叫**在本機執行，輸入和輸出顯示在上方。\n3. 這段回答以 GitHub 風格的 Markdown 串流輸出。",
            "1. **推論**は折りたためる「思考」ブロックにストリーミングされます。\n2. **ツール呼び出し**は端末上で実行され、入力と出力は上に表示されます。\n3. この回答は GitHub 形式の Markdown でストリーミングされます。",
        )
        val credits = pick(
            "The UI follows AI Elements [1] with Material 3 Expressive styling [2]; diagrams render with Mermaid [3].",
            "界面遵循 AI Elements [1]，采用 Material 3 Expressive 风格 [2]；图表由 Mermaid [3] 渲染。",
            "介面遵循 AI Elements [1]，採用 Material 3 Expressive 風格 [2]；圖表由 Mermaid [3] 繪製。",
            "UI は AI Elements [1] に沿い、Material 3 Expressive のスタイル [2] を使います。図は Mermaid [3] で描画します。",
        )
        val table = pick(
            "| Provider | Where the agent loop runs | Protocol |\n|---|---|---|\n| Agent server | PydanticAI (server) | UI Message Stream (SSE) |\n| OpenAI-compatible | On-device | Chat Completions (SSE) |\n| Anthropic | On-device | Messages API (SSE) |\n| Offline demo | On-device | — |",
            "| 服务 | 智能体循环运行在 | 协议 |\n|---|---|---|\n| 智能体服务器 | PydanticAI（服务器） | UI Message Stream (SSE) |\n| OpenAI 兼容 | 本机 | Chat Completions (SSE) |\n| Anthropic | 本机 | Messages API (SSE) |\n| 离线演示 | 本机 | — |",
            "| 服務 | 代理循環執行於 | 協定 |\n|---|---|---|\n| 代理伺服器 | PydanticAI（伺服器） | UI Message Stream (SSE) |\n| OpenAI 相容 | 本機 | Chat Completions (SSE) |\n| Anthropic | 本機 | Messages API (SSE) |\n| 離線示範 | 本機 | — |",
            "| サービス | エージェントループの実行場所 | プロトコル |\n|---|---|---|\n| エージェントサーバー | PydanticAI（サーバー） | UI Message Stream (SSE) |\n| OpenAI 互換 | 端末上 | Chat Completions (SSE) |\n| Anthropic | 端末上 | Messages API (SSE) |\n| オフラインデモ | 端末上 | — |",
        )
        val user = pick("User", "用户", "使用者", "ユーザー")
        val tools = pick("Agent tools", "智能体工具", "代理工具", "エージェントのツール")
        val ui = pick("Markdown + Mermaid UI", "Markdown + Mermaid 界面", "Markdown + Mermaid 介面", "Markdown + Mermaid UI")
        val tip = pick(
            "Switch providers from the menu in the top bar — the conversation is kept.",
            "可以在顶栏的菜单中切换服务，对话会保留。",
            "可以在頂端列的選單中切換服務，對話會保留。",
            "上部バーのメニューでサービスを切り替えても、会話はそのまま残ります。",
        )
        val trip = pick("Plan a trip to Kyoto", "规划一次京都旅行", "規劃一趟京都旅行", "京都旅行を計画して")
        return """
            |## $heading
            |
            |$said
            |
            |### $happened
            |$steps
            |
            |$credits
            |
            |$table
            |
            |```mermaid
            |flowchart LR
            |    U([$user]) --> C[ChatController]
            |    C --> B{Backend}
            |    B -->|tool_calls| T[[$tools]]
            |    T -->|results| B
            |    B -->|text / reasoning| R[$ui]
            |```
            |
            |```kotlin
            |val controller = ChatController(backend = { provider.createBackend(key) }, scope)
            |controller.send("$trip")
            |```
            |
            |> $tip
        """.trimMargin()
    }

    fun longSection(i: Int) = pick(
        "\n\n### Section $i\n\nThis is paragraph $i of a long streamed answer. It keeps growing so the conversation must follow the bottom while you watch, and stay perfectly still once you scroll up to read. Item **$i** has `inline code`, a [link](https://example.com/$i) and some *emphasis*.",
        "\n\n### 第 $i 节\n\n这是一段长回答的第 $i 段。它会不断变长：你在看时，对话要跟住底部；你往上滚动阅读时，它要保持不动。第 **$i** 项有 `行内代码`、一个[链接](https://example.com/$i)和一些*强调*。",
        "\n\n### 第 $i 節\n\n這是一段長回答的第 $i 段。它會不斷變長：你在看時，對話要跟住底部；你往上捲動閱讀時，它要保持不動。第 **$i** 項有 `行內程式碼`、一個[連結](https://example.com/$i)和一些*強調*。",
        "\n\n### セクション $i\n\n長い回答の第 $i 段落です。伸び続けるので、見ている間は会話が末尾に追従し、上にスクロールして読んでいる間は動かないことが大切です。項目 **$i** には `インラインコード`、[リンク](https://example.com/$i)、*強調* があります。",
    )

    // The scripted browser run.
    fun browseReasoning(url: String) = pick(
        "The user wants me to use the browser. I'll open $url, read the page structure, follow a link and check the result visually.",
        "用户希望我使用浏览器。我会打开 $url，读取页面结构，点开一个链接，再截图确认结果。",
        "使用者希望我使用瀏覽器。我會開啟 $url，讀取頁面結構，點開一個連結，再截圖確認結果。",
        "ユーザーはブラウザの操作を求めています。$url を開いてページ構造を読み、リンクをたどって、結果をスクリーンショットで確認します。",
    )
    val browseOpening get() = pick("I'll open the page first.", "我先打开这个网页。", "我先開啟這個網頁。", "まずページを開きます。")
    fun browseFailed(error: String) = pick(
        "The page did not open ($error). Check the network and try again.",
        "网页没有打开（$error）。请检查网络后重试。",
        "網頁沒有開啟（$error）。請檢查網路後重試。",
        "ページを開けませんでした（$error）。ネットワークを確認してもう一度お試しください。",
    )
    fun browseFollow(link: String) = pick(
        "The page has a link, “$link”. I'll follow it.",
        "页面上有一个链接“$link”，我点开看看。",
        "頁面上有一個連結「$link」，我點開看看。",
        "ページに「$link」というリンクがあります。開いてみます。",
    )
    fun browseDone(url: String, link: String?, shot: String) = pick(
        "Done. I opened **$url**, read its structure with `snapshot`" + (link?.let { ", clicked **$it**" } ?: "") +
            " and took a `screenshot` ($shot).\n\nOpen the agent's computer below to step through the run: each step has its screenshot, with the element the agent acted on outlined.",
        "完成了。我打开了 **$url**，用 `snapshot` 读取了页面结构" + (link?.let { "，点击了 **$it**" } ?: "") +
            "，并截了图（`screenshot`：$shot）。\n\n打开下方“智能体的电脑”可以逐步回看：每一步都有截图，智能体操作的元素会被框出来。",
        "完成了。我開啟了 **$url**，用 `snapshot` 讀取了頁面結構" + (link?.let { "，點擊了 **$it**" } ?: "") +
            "，並截了圖（`screenshot`：$shot）。\n\n開啟下方「代理的電腦」可以逐步回看：每一步都有截圖，代理操作的元素會被框出來。",
        "完了しました。**$url** を開き、`snapshot` でページ構造を読み取り" + (link?.let { "、**$it** をクリックし" } ?: "") +
            "、`screenshot` を撮りました（$shot）。\n\n下の「エージェントのコンピューター」を開くと、各ステップをスクリーンショット付きで振り返れます。操作した要素は枠で示されます。",
    )

    // The answer with interface (JSX).
    val formIntro get() = pick(
        "Here is a booking form you can fill in right here:",
        "这是一个可以直接在这里填写的预订表单：",
        "這是一個可以直接在這裡填寫的預訂表單：",
        "ここでそのまま入力できる予約フォームです：",
    )
    val formOutro get() = pick(
        "It is JSX rendered natively: the fields write into its data model, and **Book** sends them back as an action.",
        "它是原生渲染的 JSX：输入会写入它的数据模型，点 **预订** 会把它们作为操作发回。",
        "它是原生繪製的 JSX：輸入會寫入它的資料模型，按 **預訂** 會把它們作為動作傳回。",
        "ネイティブに描画された JSX です。入力はデータモデルに書き込まれ、**予約** で操作として送り返されます。",
    )
    val bookingJsx get() = """
        <Card>
          <h3>${pick("Stay in Kyoto", "入住京都", "入住京都", "京都に泊まる")}</h3>
          <small>${pick("Hotel Lumen · from \$180 / night", "Hotel Lumen · 每晚 \$180 起", "Hotel Lumen · 每晚 \$180 起", "Hotel Lumen · 1 泊 \$180 から")}</small>
          <input name="guest" placeholder="${pick("Guest name", "入住人姓名", "入住人姓名", "宿泊者名")}" />
          <DateTimeInput label="${pick("Check-in", "入住日期", "入住日期", "チェックイン")}" value={checkin} enableDate={true} />
          <select name="room" label="${pick("Room", "房型", "房型", "部屋")}"><option value="standard">${pick("Standard", "标准间", "標準房", "スタンダード")}</option><option value="deluxe">${pick("Deluxe", "豪华间", "豪華房", "デラックス")}</option><option value="suite">${pick("Suite", "套房", "套房", "スイート")}</option></select>
          <Slider label="${pick("Nights", "晚数", "晚數", "泊数")}" min={1} max={14} value={nights} />
          <div className="flex justify-end">
            <Button variant="primary" onClick={book}>$bookLabel</Button>
          </div>
        </Card>
    """.trimIndent()
    val bookLabel get() = pick("Book", "预订", "預訂", "予約")

    companion object {
        // Characters that exist in only one of the two scripts, common in prompts.
        private const val TRADITIONAL = "們這個來時麼為說對網頁複製簿開圖產飯預訂單體點過還發學會與經見關應將長門書買車東間請寫讓麼幫"
        private const val SIMPLIFIED = "们这个来时么为说对网页复制开图产饭预订单体点过还发学会与经见关应将长门书买车东间请写让帮"

        fun of(prompt: String, locale: Locale = Locale.getDefault()) = MockCopy(
            when {
                prompt.any { it in '぀'..'ヿ' } -> Language.JA
                prompt.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN } -> when {
                    prompt.any { it in TRADITIONAL } -> Language.ZH_HANT
                    prompt.any { it in SIMPLIFIED } -> Language.ZH_HANS
                    locale.script == "Hant" || locale.country in setOf("TW", "HK", "MO") -> Language.ZH_HANT
                    else -> Language.ZH_HANS
                }
                else -> Language.EN
            },
        )
    }
}
