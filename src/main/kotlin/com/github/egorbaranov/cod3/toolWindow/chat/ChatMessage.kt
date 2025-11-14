package com.github.egorbaranov.cod3.toolWindow.chat

import javax.swing.Icon

/**
 * Minimal representation of a chat turn that can be fed back into Koog prompts.
 */
data class ChatMessage(
    val role: ChatRole,
    val content: String,
    val attachments: List<ChatAttachment> = emptyList()
)

data class ChatAttachment(
    val title: String,
    val content: String,
    val icon: Icon? = null,
    val navigationPath: String? = null
)

enum class ChatRole {
    USER,
    ASSISTANT
}
