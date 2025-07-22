package com.github.egorbaranov.cod3.completions.codeCompletion
import com.github.egorbaranov.cod3.completions.CompletionClientProvider
import com.intellij.openapi.components.Service
import ee.carlrobert.llm.client.openai.completion.OpenAIChatCompletionModel
import ee.carlrobert.llm.client.openai.completion.request.OpenAITextCompletionRequest
import ee.carlrobert.llm.completion.CompletionEventListener
import okhttp3.sse.EventSource

@Service(Service.Level.PROJECT)
class CodeCompletionService {

    fun getCodeCompletionAsync(
        prefix: String,
        suffix: String,
        eventListener: CompletionEventListener<String>
    ): EventSource {
        return CompletionClientProvider().getOpenAIClient()
            .getCompletionAsync(buildOpenAIRequest(prefix, suffix), eventListener)
    }

    fun buildOpenAIRequest(prefix: String, suffix: String): OpenAITextCompletionRequest {
        return OpenAITextCompletionRequest.Builder(prefix)
            .setSuffix(suffix)
            .setStream(true)
            .setTemperature(0.0)
            .setPresencePenalty(0.0)
            .build()
    }
}