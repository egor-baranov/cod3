package com.github.egorbaranov.cod3.api.entity.openai

data class OpenAIResponse(
    val choices: List<Choice>?,
    val error: OpenAIError?
) {
    data class Choice(
        val message: Message
    )
}