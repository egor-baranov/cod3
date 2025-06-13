package com.github.egorbaranov.cod3.completions

import com.intellij.openapi.application.ApplicationManager
import ee.carlrobert.llm.client.anthropic.completion.ClaudeCompletionRequest
import ee.carlrobert.llm.client.codegpt.request.chat.ChatCompletionRequest
import ee.carlrobert.llm.client.google.completion.GoogleCompletionRequest
import ee.carlrobert.llm.client.llama.completion.LlamaCompletionRequest
import ee.carlrobert.llm.client.openai.completion.request.OpenAIChatCompletionRequest
import ee.carlrobert.llm.completion.CompletionEventListener
import ee.carlrobert.llm.completion.CompletionRequest
import okhttp3.sse.EventSource

class CompletionsRequestService {

    fun getChatCompletionAsync(
        request: CompletionRequest,
        eventListener: CompletionEventListener<String?>?
    ): EventSource {
//        if (request is OpenAIChatCompletionRequest) {
//            return when (GeneralSettings.getSelectedService()) {
//                OPENAI -> CompletionClientProvider.getOpenAIClient()
//                    .getChatCompletionAsync(request, eventListener)
//
//                OLLAMA -> CompletionClientProvider.getOllamaClient()
//                    .getChatCompletionAsync(request, eventListener)
//
//                else -> throw RuntimeException("Unknown service selected")
//            }
//        }
//        if (request is ChatCompletionRequest) {
//            return CompletionClientProvider.getCodeGPTClient()
//                .getChatCompletionAsync(request, eventListener)
//        }
//        if (request is CustomOpenAIRequest) {
//            return getCustomOpenAIChatCompletionAsync(request.getRequest(), eventListener)
//        }
//        if (request is ClaudeCompletionRequest) {
//            return CompletionClientProvider.getClaudeClient().getCompletionAsync(
//                request,
//                eventListener
//            )
//        }
//        if (request is GoogleCompletionRequest) {
//            return CompletionClientProvider.getGoogleClient().getChatCompletionAsync(
//                request,
//                ApplicationManager.getApplication().getService<GoogleSettings?>(GoogleSettings::class.java)
//                    .getState()
//                    .getModel(),
//                eventListener
//            )
//        }
//        if (request is LlamaCompletionRequest) {
//            return CompletionClientProvider.getLlamaClient().getChatCompletionAsync(
//                request,
//                eventListener
//            )
//        }

        throw IllegalStateException("Unknown request type: " + request.javaClass)
    }
}