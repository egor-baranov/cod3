package com.github.egorbaranov.cod3.completions

import com.github.egorbaranov.cod3.settings.PluginSettingsState
import ee.carlrobert.llm.client.anthropic.completion.ClaudeCompletionRequest
import ee.carlrobert.llm.client.google.completion.GoogleCompletionRequest
import ee.carlrobert.llm.client.google.models.GoogleModel
import ee.carlrobert.llm.client.llama.completion.LlamaCompletionRequest
import ee.carlrobert.llm.client.ollama.completion.request.OllamaCompletionRequest
import ee.carlrobert.llm.client.openai.completion.request.OpenAIChatCompletionRequest
import ee.carlrobert.llm.completion.CompletionEventListener
import ee.carlrobert.llm.completion.CompletionRequest
import okhttp3.sse.EventSource

class CompletionsRequestService(
    private val settingsState: PluginSettingsState
) {

    fun getChatCompletionAsync(
        request: CompletionRequest,
        eventListener: CompletionEventListener<String?>?
    ): EventSource {
        val completionClientProvider = CompletionClientProvider()

        if (request is OpenAIChatCompletionRequest) {
            return completionClientProvider.getOpenAIClient().getChatCompletionAsync(request, eventListener)
        }

        if (request is GoogleCompletionRequest) {
            return completionClientProvider.getGoogleClient().getChatCompletionAsync(
                request,
                settingsState.googleModel,
                eventListener
            )
        }

        if (request is ClaudeCompletionRequest) {
            return completionClientProvider.getClaudeClient().getCompletionAsync(request, eventListener)
        }

        if (request is OllamaCompletionRequest) {
            return completionClientProvider.getOllamaClient().getCompletionAsync(request, eventListener)
        }

        if (request is LlamaCompletionRequest) {
            return completionClientProvider.getLlamaClient().getChatCompletionAsync(request, eventListener)
        }

        throw IllegalStateException("Unknown request type: " + request.javaClass)
    }
}