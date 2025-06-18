package com.github.egorbaranov.cod3.completions.factory

import ee.carlrobert.llm.client.openai.completion.request.OpenAIChatCompletionRequest
import ee.carlrobert.llm.client.openai.completion.request.OpenAIChatCompletionStandardMessage

class OpenAIRequestFactory {


    companion object {

        fun createBasicCompletionRequest(
            messages: List<OpenAIChatCompletionStandardMessage>,
            model: String? = null,
            isStream: Boolean = false
        ): OpenAIChatCompletionRequest {
            return OpenAIChatCompletionRequest
                .Builder(messages)
                .setModel(model)
                .setStream(isStream)
                .build()
        }
    }
}


data class SystemMessage(val text: String) : OpenAIChatCompletionStandardMessage("system", text)
data class UserMessage(val text: String) : OpenAIChatCompletionStandardMessage("user", text)
data class AssistantUser(val text: String) : OpenAIChatCompletionStandardMessage("assistant", text)