package com.github.egorbaranov.cod3.api.entity.openai

data class OpenAIError(
    val message: String,
    val type: String,
    val param: String?,
    val code: String?
)