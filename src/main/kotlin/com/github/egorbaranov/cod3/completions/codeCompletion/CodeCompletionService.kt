package com.github.egorbaranov.cod3.completions.codeCompletion

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.streaming.StreamFrame
import com.github.egorbaranov.cod3.koog.KoogExecutorFactory
import com.github.egorbaranov.cod3.koog.KoogModelCatalog
import com.github.egorbaranov.cod3.settings.PluginSettingsState
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

@Service(Service.Level.PROJECT)
class CodeCompletionService :
    CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.IO),
    Disposable {

    private val logger = Logger.getInstance(CodeCompletionService::class.java)

    fun streamCompletion(
        prefix: String,
        suffix: String,
        onDelta: (String) -> Unit
    ): Job {
        val settings = PluginSettingsState.getInstance()
        val modelEntry = KoogModelCatalog.resolveEntry(settings.koogModelId)
        val executor = KoogExecutorFactory.create(settings, modelEntry.provider)
        val model = modelEntry.model
        val prompt = prompt(
            id = "cod3-inline-${System.currentTimeMillis()}",
        ) {
            system(
                """
                You are Cod3, providing inline code completions inside an IDE.
                Consider the prefix as existing code before the caret and the suffix as code after the caret.
                Reply with the code that should be inserted between them with no surrounding commentary.
                """.trimIndent()
            )
            user(
                buildString {
                    appendLine("PREFIX:")
                    appendLine(prefix)
                    appendLine()
                    appendLine("SUFFIX:")
                    appendLine(suffix)
                }
            )
        }

        return launch {
            try {
                executor.executeStreaming(prompt, model, emptyList<ToolDescriptor>()).collect { frame ->
                    if (frame is StreamFrame.Append && frame.text.isNotEmpty()) {
                        onDelta(frame.text)
                    }
                }
            } catch (ex: Exception) {
                logger.warn("Koog inline completion failed", ex)
            }
        }
    }
    override fun dispose() {
        coroutineContext.cancel()
    }
}
