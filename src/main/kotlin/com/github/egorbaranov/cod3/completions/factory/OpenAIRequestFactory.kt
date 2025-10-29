package com.github.egorbaranov.cod3.completions.factory

import ee.carlrobert.llm.client.openai.completion.request.OpenAIChatCompletionMessage
import ee.carlrobert.llm.client.openai.completion.request.OpenAIChatCompletionRequest
import ee.carlrobert.llm.client.openai.completion.request.OpenAIChatCompletionStandardMessage

class OpenAIRequestFactory {


    companion object {

        fun createBasicCompletionRequest(
            messages: List<OpenAIChatCompletionMessage>,
            model: String? = null,
            isStream: Boolean = false,
            overridenPath: String? = null
        ): OpenAIChatCompletionRequest {
            return OpenAIChatCompletionRequest
                .Builder(messages)
                .setModel(model)
                .setStream(isStream)
                .let {
                    if (overridenPath != null) it.setOverriddenPath(overridenPath) else it
                }
                .build()
        }
    }
}


data class SystemMessage(val text: String) : OpenAIChatCompletionStandardMessage("system", text)
data class UserMessage(val text: String) : OpenAIChatCompletionStandardMessage("user", text)

data class ToolMessage(val text: String) : OpenAIChatCompletionStandardMessage("tool", text)
data class AssistantMessage(val text: String) : OpenAIChatCompletionStandardMessage("assistant", text)
