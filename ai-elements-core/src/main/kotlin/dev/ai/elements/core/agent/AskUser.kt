package dev.ai.elements.core.agent

import dev.ai.elements.core.chat.InputRequest
import dev.ai.elements.core.chat.InputResponse
import dev.ai.elements.core.http.BackendJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Lets the model ask the user multiple-choice questions mid-run — Pydantic AI Harness `AskUser`,
 * with the same tool (`ask_user_question`), schema, limits, instruction and result, so prompts and
 * servers written for it work unchanged.
 *
 * The question reaches the user through the chat's [dev.ai.elements.core.chat.ToolApprover.input]
 * as an [InputRequest] whose schema is a standard JSON Schema object: one property per question
 * (keyed by `header`), a single choice (`oneOf` of `{const, title, description}`) or a multiple
 * choice (`array` of `anyOf` those), plus a plain `{"type": "string"}` alternative for the user's
 * own answer. The result maps each header to the picked labels, or to a one-item list with the
 * user's own answer; a declined question returns [DECLINED].
 *
 * On a device that talks to an agent server, pass [tool] as an AG-UI frontend tool or an AI SDK
 * client-side tool: the server's model asks, the device answers — no protocol extension needed.
 */
class AskUser : Capability {
    override val instructions: String = INSTRUCTIONS

    override suspend fun tools(): List<AgentTool> = listOf(tool)

    companion object {
        const val TOOL_NAME = "ask_user_question"
        const val MAX_QUESTIONS = 10
        const val MIN_OPTIONS = 2
        const val MAX_OPTIONS = 6
        const val MAX_HEADER_LENGTH = 25
        const val MAX_QUESTION_LENGTH = 500
        const val MAX_LABEL_LENGTH = 50
        const val MAX_DESCRIPTION_LENGTH = 200

        const val DECLINED = "The user declined to answer. Continue without the answer, or ask differently if it is essential."

        const val INSTRUCTIONS =
            "When the task is ambiguous and the answer is not in the workspace, call `ask_user_question` with concrete " +
                "options rather than guessing or burying the question in prose. Batch related questions into one call. " +
                "If the user declines, make a reasonable choice and say which one you made."

        /** The `ask_user_question` tool on its own, e.g. to advertise as a frontend / client-side tool. */
        val tool: AgentTool = AskUserQuestionTool
    }
}

/** One question of an `ask_user_question` call. */
data class AskUserQuestion(val header: String, val question: String, val options: List<Option>, val multiSelect: Boolean) {
    data class Option(val label: String, val description: String? = null)
}

private object AskUserQuestionTool : AgentTool {
    override val name = AskUser.TOOL_NAME
    override val title = "Ask the user"
    override val description =
        "Ask the user one or more multiple-choice questions and wait for the answers.\n\n" +
            "Use it when the task is ambiguous and the answer cannot be found in the workspace.\n" +
            "Offer concrete options; the answerer may also accept a custom answer. The result maps\n" +
            "each question's `header` to picked labels or a one-item list containing the custom\n" +
            "answer, or explains that the user declined."

    // Pydantic AI Harness `AskUserToolset` parameters, as its model sees them.
    override val parameters: JsonObject = BackendJson.parseToJsonElement(
        """{"${'$'}defs":{"Question":{"additionalProperties":false,"description":"One multiple-choice question.","properties":{"header":{"description":"Short label naming the question, unique within the call; the answer is keyed by it.","maxLength":25,"minLength":1,"type":"string"},"question":{"description":"The full question text.","maxLength":500,"minLength":1,"type":"string"},"options":{"description":"The choices to offer.","items":{"${'$'}ref":"#/${'$'}defs/QuestionOption"},"maxItems":6,"minItems":2,"type":"array"},"multi_select":{"default":false,"description":"Whether the user may pick more than one option.","type":"boolean"}},"required":["header","question","options"],"title":"Question","type":"object"},"QuestionOption":{"additionalProperties":false,"description":"One choice the user can pick.","properties":{"label":{"description":"Short option name, one to five words.","maxLength":50,"minLength":1,"type":"string"},"description":{"anyOf":[{"maxLength":200,"type":"string"},{"type":"null"}],"default":null,"description":"What choosing this option means."}},"required":["label"],"title":"QuestionOption","type":"object"}},"additionalProperties":false,"properties":{"questions":{"description":"One to ten questions, each with a unique `header`, two to six options,\nand `multi_select` when several answers are allowed.","items":{"${'$'}ref":"#/${'$'}defs/Question"},"maxItems":10,"minItems":1,"type":"array"}},"required":["questions"],"type":"object"}""",
    ).jsonObject

    override suspend fun execute(arguments: JsonObject): String {
        val questions = parse(arguments)
        val context = ToolCallContext.current() ?: return AskUser.DECLINED
        val request = InputRequest(
            id = context.toolCallId,
            message = questions.singleOrNull()?.question ?: questions.joinToString(" · ") { it.header },
            schema = schema(questions),
        )
        return when (val response = context.approver.input(request)) {
            is InputResponse.Accept -> answers(questions, response.content ?: JsonObject(emptyMap())).toString()
            InputResponse.Decline, InputResponse.Cancel -> AskUser.DECLINED
        }
    }

    /** Validates like Harness; a call outside the limits goes back to the model as a tool error. */
    fun parse(arguments: JsonObject): List<AskUserQuestion> {
        val items = arguments["questions"] as? JsonArray ?: throw IllegalArgumentException("`questions` is required")
        require(items.size in 1..AskUser.MAX_QUESTIONS) { "Ask 1 to ${AskUser.MAX_QUESTIONS} questions" }
        val questions = items.map { item ->
            val q = item.jsonObject
            val header = q.text("header").trim()
            val question = q.text("question").trim()
            require(header.isNotEmpty() && header.length <= AskUser.MAX_HEADER_LENGTH && '\n' !in header) { "Invalid header: $header" }
            require(question.isNotEmpty() && question.length <= AskUser.MAX_QUESTION_LENGTH) { "Invalid question text" }
            val options = (q["options"] as? JsonArray ?: throw IllegalArgumentException("`options` is required")).map { o ->
                val label = o.jsonObject.text("label").trim()
                val description = (o.jsonObject["description"] as? JsonPrimitive)?.contentOrNull?.trim()
                require(label.isNotEmpty() && label.length <= AskUser.MAX_LABEL_LENGTH && '\n' !in label) { "Invalid option label: $label" }
                require(description == null || description.length <= AskUser.MAX_DESCRIPTION_LENGTH) { "Option description too long" }
                AskUserQuestion.Option(label, description?.ifEmpty { null })
            }
            require(options.size in AskUser.MIN_OPTIONS..AskUser.MAX_OPTIONS) { "Offer ${AskUser.MIN_OPTIONS} to ${AskUser.MAX_OPTIONS} options" }
            require(options.map { it.label }.toSet().size == options.size) { "Option labels must be unique" }
            val allowed = setOf("header", "question", "options", "multi_select")
            require(q.keys.all { it in allowed }) { "Unknown question fields: ${q.keys - allowed}" }
            AskUserQuestion(header, question, options, (q["multi_select"] as? JsonPrimitive)?.booleanOrNull ?: false)
        }
        require(questions.map { it.header }.toSet().size == questions.size) { "Question headers must be unique" }
        (questions.flatMap { listOf(it.header, it.question) + it.options.flatMap { o -> listOfNotNull(o.label, o.description) } })
            .forEach { s -> require(s.none { it.isISOControl() && it != '\n' }) { "Control characters are not allowed" } }
        return questions
    }

    /** One standard JSON Schema property per question; the user's own answer is the plain string alternative. */
    fun schema(questions: List<AskUserQuestion>): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            questions.forEach { q ->
                putJsonObject(q.header) {
                    put("title", q.header)
                    if (questions.size > 1) put("description", q.question)
                    if (q.multiSelect) {
                        put("type", "array")
                        put("minItems", 1)
                        putJsonObject("items") { choices(q) }
                    } else {
                        put("type", "string")
                        choices(q)
                    }
                }
            }
        }
        putJsonArray("required") { questions.forEach { add(it.header) } }
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.choices(q: AskUserQuestion) {
        putJsonArray("anyOf") {
            q.options.forEach { o ->
                addJsonObject {
                    put("const", o.label)
                    put("title", o.label)
                    o.description?.let { put("description", it) }
                }
            }
            addJsonObject { put("type", "string") }
        }
    }

    /** Harness's result: header → picked labels, or a one-item list with the user's own answer. */
    fun answers(questions: List<AskUserQuestion>, content: JsonObject): JsonObject = buildJsonObject {
        questions.forEach { q ->
            val picked = when (val value = content[q.header]) {
                is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                is JsonPrimitive -> listOfNotNull(value.contentOrNull)
                else -> emptyList()
            }.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            val labels = q.options.map { it.label }.toSet()
            val custom = picked.firstOrNull { it !in labels }
            putJsonArray(q.header) {
                if (custom != null) add(custom) else picked.forEach { add(it) }
            }
        }
    }

    private fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull ?: throw IllegalArgumentException("`$key` is required")
}

/** The questions of an `ask_user_question` call's [arguments], validated as Pydantic AI Harness does. */
fun askUserQuestions(arguments: JsonObject): List<AskUserQuestion> = AskUserQuestionTool.parse(arguments)

/** The [InputRequest] schema for [questions]: standard JSON Schema, one property per question. */
fun askUserSchema(questions: List<AskUserQuestion>): JsonObject = AskUserQuestionTool.schema(questions)

/** The `ask_user_question` result for the user's [content] (an [InputResponse.Accept] of [askUserSchema]). */
fun askUserAnswers(questions: List<AskUserQuestion>, content: JsonObject): JsonObject = AskUserQuestionTool.answers(questions, content)
