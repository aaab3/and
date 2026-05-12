package com.runtime.android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.serialization.json.*
import java.util.concurrent.TimeUnit

/**
 * Fetches available model IDs from a provider's /v1/models endpoint.
 */
object ModelListFetcher {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun fetchModels(baseUrl: String, apiKey: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val url = baseUrl.trimEnd('/') + "/models"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                parseModelIds(body)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseModelIds(json: String): List<String> {
        return try {
            val root = Json.parseToJsonElement(json).jsonObject
            val data = root["data"]?.jsonArray ?: return emptyList()
            data.mapNotNull { item ->
                item.jsonObject["id"]?.jsonPrimitive?.contentOrNull
            }.sorted()
        } catch (_: Exception) {
            emptyList()
        }
    }
}
