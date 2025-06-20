package com.github.egorbaranov.cod3.api.entity.openai

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

data class OpenAIRequest(
    val model: String,
    val messages: List<Message>,
    val tools: List<JsonObject>? = null,
    @SerializedName("tool_choice") val toolChoice: String? = null
)