package com.runtime.conversation

import com.runtime.model.ModelToolCall

/**
 * Parses text-based tool calls emitted by models that don't use OpenAI native
 * function calling. Supports Claude-style XML format:
 *
 *   <tool_call>
 *     <function=search>
 *       <parameter=query>hello world</parameter>
 *     </function>
 *   </tool_call>
 *
 * Also maps common tool names to our registered tools.
 */
object TextToolCallParser {

    /**
     * Alias → canonical tool name mapping.
     * Add more as needed for different model families.
     */
    private val toolAliases = mapOf(
        // Web search
        "search" to "web_search",
        "web_search" to "web_search",
        "google" to "web_search",
        "bing" to "web_search",
        "duckduckgo" to "web_search",
        "ddg" to "web_search",

        // HTTP fetch / URL open
        "open" to "http_fetch",
        "fetch" to "http_fetch",
        "url" to "http_fetch",
        "http" to "http_fetch",
        "http_fetch" to "http_fetch",
        "web_fetch" to "http_fetch",
        "get_url" to "http_fetch",
        "visit" to "http_fetch",
        "browse" to "http_fetch",

        // File read
        "read" to "read_text_file",
        "cat" to "read_text_file",
        "read_file" to "read_text_file",
        "read_text_file" to "read_text_file",

        // Current time
        "time" to "current_time",
        "now" to "current_time",
        "date" to "current_time",
        "datetime" to "current_time",
        "current_time" to "current_time"
    )

    /**
     * Parsed tool call with optional aliased name.
     */
    data class ParsedCall(
        val originalName: String,
        val mappedName: String?,
        val arguments: Map<String, String>,
        val rawMatch: String,  // The original XML substring for text stripping
        val id: String
    )

    /**
     * Parse Claude-style XML tool calls from text.
     * Returns the list of calls found and the text with all tool_call blocks removed.
     */
    fun parse(text: String): Pair<List<ParsedCall>, String> {
        val calls = mutableListOf<ParsedCall>()

        // Match <tool_call> ... </tool_call> blocks (non-greedy)
        val blockPattern = Regex(
            """<tool_call>\s*(.*?)\s*</tool_call>""",
            RegexOption.DOT_MATCHES_ALL
        )

        var idCounter = 0
        for (match in blockPattern.findAll(text)) {
            val inner = match.groupValues[1]

            // Extract function name: <function=NAME>
            val funcMatch = Regex("""<function=(\w+)>""").find(inner) ?: continue
            val funcName = funcMatch.groupValues[1]

            // Extract parameters: <parameter=KEY>VALUE</parameter>
            val paramPattern = Regex(
                """<parameter=(\w+)>(.*?)</parameter>""",
                RegexOption.DOT_MATCHES_ALL
            )
            val args = paramPattern.findAll(inner).associate {
                it.groupValues[1] to it.groupValues[2].trim()
            }

            val mapped = toolAliases[funcName.lowercase()]

            calls.add(ParsedCall(
                originalName = funcName,
                mappedName = mapped,
                arguments = args,
                rawMatch = match.value,
                id = "text-call-${idCounter++}"
            ))
        }

        // Strip all tool_call blocks from the text
        val cleanedText = blockPattern.replace(text, "").trim()

        return calls to cleanedText
    }

    /**
     * Convert parsed calls to ModelToolCall (for executor compatibility).
     * Only includes calls that successfully mapped to a registered tool.
     */
    fun toModelToolCalls(
        calls: List<ParsedCall>,
        isToolRegistered: (String) -> Boolean
    ): List<ModelToolCall> {
        return calls.mapNotNull { call ->
            val targetName = call.mappedName ?: call.originalName
            if (!isToolRegistered(targetName)) return@mapNotNull null

            // Convert args map to JSON
            val argsJson = buildString {
                append("{")
                call.arguments.entries.forEachIndexed { i, (k, v) ->
                    if (i > 0) append(",")
                    append("\"").append(k).append("\":\"")
                    append(escapeJson(v))
                    append("\"")
                }
                append("}")
            }

            ModelToolCall(
                id = call.id,
                name = targetName,
                argumentsJson = argsJson
            )
        }
    }

    private fun escapeJson(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "")
        .replace("\t", "\\t")
}
