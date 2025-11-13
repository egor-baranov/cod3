package com.github.egorbaranov.cod3.toolWindow.chat

/**
 * Minimal representation of a chat turn that can be fed back into Koog prompts.
 */
data class ChatMessage(
    val role: ChatRole,
    val content: String
)

enum class ChatRole {
    USER,
    ASSISTANT
}
