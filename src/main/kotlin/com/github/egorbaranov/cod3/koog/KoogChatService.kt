package com.github.egorbaranov.cod3.koog

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.streaming.StreamFrame
import com.github.egorbaranov.cod3.settings.PluginSettingsState
import com.github.egorbaranov.cod3.toolWindow.chat.ChatMessage
import com.github.egorbaranov.cod3.toolWindow.chat.ChatRole
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import java.util.*

@Service(Service.Level.PROJECT)
class KoogChatService() : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.IO), Disposable {

    private val logger = Logger.getInstance(KoogChatService::class.java)

    fun stream(
        history: List<ChatMessage>,
        listener: (KoogChatStreamEvent) -> Unit
    ): Job {
        val settings = PluginSettingsState.getInstance()
        val modelEntry = KoogModelCatalog.resolveEntry(settings.koogModelId)
        val executor = try {
            KoogExecutorFactory.create(settings, modelEntry.provider)
        } catch (e: IllegalStateException) {
            return launch { listener(KoogChatStreamEvent.Error(e)) }
        }
        val model = modelEntry.model
        val systemPrompt = buildSystemPrompt(settings.koogSystemPrompt)
        val prompt = buildPrompt(history, systemPrompt)

        return launch {
            val buffer = StringBuilder()
            try {
                executor.executeStreaming(prompt, model, emptyList<ToolDescriptor>()).collect { frame ->
                    when (frame) {
                        is StreamFrame.Append -> {
                            val chunk = frame.text.trim()
                            if (chunk.isNotEmpty()) {
                                buffer.append(chunk)
                                listener(KoogChatStreamEvent.ContentDelta(chunk))
                            }
                        }

                        is StreamFrame.End -> Unit
                        is StreamFrame.ToolCall -> Unit
                    }
                }
                listener(KoogChatStreamEvent.Completed(buffer.toString().trim()))
            } catch (ex: Exception) {
                logger.warn("Koog chat streaming failed", ex)
                listener(KoogChatStreamEvent.Error(ex))
            }
        }
    }

    private fun buildPrompt(history: List<ChatMessage>, systemPrompt: String) =
        prompt(
            id = "cod3-chat-${UUID.randomUUID()}",
        ) {
            system(systemPrompt)
            history.forEach { message ->
                when (message.role) {
                    ChatRole.USER -> user(message.content.trim())
                    ChatRole.ASSISTANT -> assistant(message.content.trim())
                }
            }
        }

    companion object {
        private const val DEFAULT_SYSTEM_PROMPT =
            "You are Cod3, an IDE-native software engineer focused on precise, minimal edits."
        private const val CODE_BLOCK_STYLE_PROMPT =
            "When using Markdown code fences, never include language identifiers. " +
                "Use plain ``` only."

        private fun buildSystemPrompt(custom: String): String = buildString {
            if (custom.isBlank()) {
                append(DEFAULT_SYSTEM_PROMPT)
            } else {
                append(custom.trim())
            }
            appendLine()
            appendLine()
            append(CODE_BLOCK_STYLE_PROMPT)
        }
    }
    override fun dispose() {
        coroutineContext.cancel()
    }
}

sealed interface KoogChatStreamEvent {
    data class ContentDelta(val text: String) : KoogChatStreamEvent
    data class Completed(val response: String) : KoogChatStreamEvent
    data class Error(val throwable: Throwable) : KoogChatStreamEvent
}

internal fun Project.koogChatService(): KoogChatService = service()
