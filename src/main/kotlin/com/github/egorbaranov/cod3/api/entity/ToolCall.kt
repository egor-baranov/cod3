package com.github.egorbaranov.cod3.api.entity

data class ToolCall(
    val id: String,
    val type: String,
    val function: FunctionCall
) {
    data class FunctionCall(
        val name: String,
        val arguments: String
    )
}