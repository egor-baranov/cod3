package com.github.egorbaranov.cod3.api.entity.openai

import com.github.egorbaranov.cod3.api.entity.ToolCall
import com.google.gson.annotations.SerializedName

data class Message(
    val role: String,
    val content: String,
    @SerializedName("tool_calls") val toolCalls: List<ToolCall>? = null
)