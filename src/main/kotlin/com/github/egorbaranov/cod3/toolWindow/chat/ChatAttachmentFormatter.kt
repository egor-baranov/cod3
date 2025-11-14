package com.github.egorbaranov.cod3.toolWindow.chat

internal fun formatMessageWithAttachments(
    text: String,
    attachments: List<ChatAttachment>
): String {
    val trimmed = text.trim()
    if (attachments.isEmpty()) return trimmed

    return buildString {
        if (trimmed.isNotEmpty()) {
            append(trimmed)
            appendLine()
            appendLine()
        }
        attachments.forEachIndexed { index, attachment ->
            val title = attachment.title.ifBlank { "Snippet ${index + 1}" }
            appendLine("Attachment #${index + 1}: $title")
            appendLine("```")
            appendLine(attachment.content.trim())
            appendLine("```")
            if (index < attachments.lastIndex) {
                appendLine()
            }
        }
    }.trim()
}
