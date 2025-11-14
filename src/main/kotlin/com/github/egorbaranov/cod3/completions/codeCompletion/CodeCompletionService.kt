package com.github.egorbaranov.cod3.completions.codeCompletion

import com.github.egorbaranov.cod3.settings.PluginSettingsState
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Service(Service.Level.PROJECT)
class CodeCompletionService :
    CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.IO),
    Disposable {

    private val logger = Logger.getInstance(CodeCompletionService::class.java)
    private val httpClient = OkHttpClient()
    private val gson = Gson()
    private val jsonMediaType = "application/json".toMediaType()

    fun streamCompletion(
        prefix: String,
        suffix: String,
        onDelta: (String) -> Unit
    ) = launch {
        val settings = PluginSettingsState.getInstance()
        val apiKey = settings.openAIApiKey.trim()
        require(apiKey.isNotEmpty()) { "OpenAI API key is not configured in settings." }

        val model = settings.codeCompletionModelId.ifBlank { settings.koogModelId }
        val prompt = buildPrompt(prefix, suffix)
        val requestPayload = OpenAICompletionRequest(
            model = model,
            prompt = prompt,
            maxTokens = 256,
            temperature = 0.1
        )

        val request = Request.Builder()
            .url(resolveCompletionsUrl(settings))
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(gson.toJson(requestPayload).toRequestBody(jsonMediaType))
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    logger.warn("OpenAI completions request failed: ${response.code} ${response.message}")
                    return@use
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@use

                val parsed = runCatching {
                    gson.fromJson(body, OpenAICompletionResponse::class.java)
                }.getOrElse {
                    logger.warn("Failed to parse OpenAI completions response", it)
                    return@use
                }

                val trimmed = parsed.choices.firstOrNull()?.text.orEmpty().trim()
                if (trimmed.isNotEmpty()) {
                    onDelta(trimmed)
                }
            }
        } catch (ex: Exception) {
            logger.warn("OpenAI completions call failed", ex)
        }
    }

    private fun buildPrompt(prefix: String, suffix: String): String = buildString {
        appendLine("You are Cod3, an inline completion engine.")
        appendLine("Given the prefix (code before the caret) and suffix (code after the caret),")
        appendLine("produce only the code that should be inserted in between.")
        appendLine("Avoid commentary and Markdown language tags.")
        appendLine()
        appendLine("PREFIX:")
        appendLine(prefix)
        appendLine()
        appendLine("SUFFIX:")
        appendLine(suffix)
        appendLine()
        append("COMPLETION:")
    }

    private fun resolveCompletionsUrl(settings: PluginSettingsState): String {
        val base = settings.openAIApiUrl.ifBlank { PluginSettingsState.DEFAULT_OPENAI_API_URL }
        return if (base.contains("chat/completions")) {
            base.replace("chat/completions", "completions")
        } else {
            base
        }
    }

    override fun dispose() {
        coroutineContext.cancel()
    }

    private data class OpenAICompletionRequest(
        val model: String,
        val prompt: String,
        @SerializedName("max_tokens") val maxTokens: Int,
        val temperature: Double,
        val stream: Boolean = false
    )

    private data class OpenAICompletionResponse(
        val choices: List<Choice> = emptyList()
    ) {
        data class Choice(val text: String? = null)
    }
}
