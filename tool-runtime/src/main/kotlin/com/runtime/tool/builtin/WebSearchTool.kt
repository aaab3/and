package com.runtime.tool.builtin

import com.runtime.core.AppError
import com.runtime.core.AppResult
import com.runtime.core.ErrorCodes
import com.runtime.tool.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Web search tool using DuckDuckGo HTML (no API key needed).
 * Returns top search result snippets.
 */
class WebSearchTool(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) : ToolHandler {

    override val manifest = ToolManifest(
        name = "web_search",
        description = "Search the web and return top results with titles and snippets. Use this when you need current information.",
        inputSchemaJson = """{"type":"object","properties":{"query":{"type":"string","description":"The search query"}},"required":["query"]}""",
        outputSchemaJson = """{"type":"object","properties":{"results":{"type":"array","items":{"type":"object","properties":{"title":{"type":"string"},"url":{"type":"string"},"snippet":{"type":"string"}}}}}}""",
        requiresUserConfirmation = false,
        timeoutMs = 15_000
    )

    override suspend fun execute(request: ToolExecutionRequest): AppResult<ToolExecutionResult> = withContext(Dispatchers.IO) {
        val args = parseArgs(request.argumentsJson)
        val query = args["query"]
        if (query.isNullOrBlank()) {
            return@withContext AppResult.Failure(AppError(ErrorCodes.INVALID_INPUT, "query is required"))
        }

        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://html.duckduckgo.com/html/?q=$encoded"

            val httpRequest = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .get()
                .build()

            client.newCall(httpRequest).execute().use { response ->
                val html = response.body?.string() ?: ""
                val results = parseDuckDuckGoResults(html)

                if (results.isEmpty()) {
                    return@use AppResult.Success(
                        ToolExecutionResult(outputJson = """{"results":[],"message":"No results found"}""")
                    )
                }

                val json = buildString {
                    append("""{"results":[""")
                    results.take(5).forEachIndexed { i, r ->
                        if (i > 0) append(",")
                        append("""{"title":${escapeJson(r.title)},"url":${escapeJson(r.url)},"snippet":${escapeJson(r.snippet)}}""")
                    }
                    append("]}")
                }

                AppResult.Success(ToolExecutionResult(outputJson = json))
            }
        } catch (e: Exception) {
            AppResult.Failure(
                AppError(ErrorCodes.EXECUTION_ERROR, "Search failed: ${e.message?.take(100)}")
            )
        }
    }

    private data class SearchResult(val title: String, val url: String, val snippet: String)

    private fun parseDuckDuckGoResults(html: String): List<SearchResult> {
        val results = mutableListOf<SearchResult>()

        // Parse DuckDuckGo HTML results - they use class="result__a" for links
        // and class="result__snippet" for snippets
        val linkPattern = Regex("""class="result__a"[^>]*href="([^"]*)"[^>]*>([^<]*)</a>""")
        val snippetPattern = Regex("""class="result__snippet"[^>]*>(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)

        val links = linkPattern.findAll(html).toList()
        val snippets = snippetPattern.findAll(html).toList()

        for (i in links.indices) {
            val link = links[i]
            val rawUrl = link.groupValues[1]
            val title = link.groupValues[2].trim().stripHtml()
            val snippet = snippets.getOrNull(i)?.groupValues?.get(1)?.trim()?.stripHtml() ?: ""

            // DuckDuckGo wraps URLs in a redirect, extract the actual URL
            val actualUrl = extractActualUrl(rawUrl)

            if (title.isNotBlank() && actualUrl.isNotBlank()) {
                results.add(SearchResult(title, actualUrl, snippet.take(200)))
            }
        }

        return results
    }

    private fun extractActualUrl(rawUrl: String): String {
        // DuckDuckGo format: //duckduckgo.com/l/?uddg=ENCODED_URL&...
        if (rawUrl.contains("uddg=")) {
            val start = rawUrl.indexOf("uddg=") + 5
            val end = rawUrl.indexOf("&", start).let { if (it < 0) rawUrl.length else it }
            return try {
                java.net.URLDecoder.decode(rawUrl.substring(start, end), "UTF-8")
            } catch (_: Exception) {
                rawUrl
            }
        }
        return rawUrl
    }

    private fun String.stripHtml(): String {
        return this.replace(Regex("<[^>]*>"), "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#x27;", "'")
            .replace("&nbsp;", " ")
            .trim()
    }

    private fun escapeJson(s: String): String {
        val escaped = s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }
}
