package com.github.egorbaranov.cod3.koog

import ai.koog.prompt.dsl.prompt
import com.github.egorbaranov.cod3.settings.PluginSettingsState
import com.github.egorbaranov.cod3.toolWindow.chat.ChatMessage
import com.github.egorbaranov.cod3.toolWindow.chat.ChatRole
import com.github.egorbaranov.cod3.toolWindow.chat.formatMessageWithAttachments
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.memberFunctions
import ai.koog.agents.core.tools.annotations.Tool as KoogTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import java.util.UUID

@Service(Service.Level.PROJECT)
class KoogAgentService(
    private val project: Project
) : Disposable {

    private val logger = Logger.getInstance(KoogAgentService::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()

    private val toolset = KoogIdeToolset(project)
    private val toolDefinitions = buildToolDefinitions()

    fun run(
        chatId: Int,
        prompt: String,
        history: MutableList<ChatMessage>,
        permissionHandler: ToolPermissionHandler,
        listener: (KoogStreamEvent) -> Unit
    ) = scope.launch {

        val settings = PluginSettingsState.getInstance()
        val modelEntry = KoogModelCatalog.resolveEntry(settings.koogModelId)
        val streamingClient = try {
            KoogExecutorFactory.createStreamingClient(settings, modelEntry.provider)
        } catch (ex: IllegalStateException) {
            listener(KoogStreamEvent.Error(ex))
            return@launch
        }

        val systemPrompt = buildStreamingSystemPrompt(settings.koogSystemPrompt)
        val llmPrompt = buildPrompt(history, systemPrompt)

        val textBuffer = StringBuilder()
        val toolJobs = mutableListOf<kotlinx.coroutines.Job>()
        val parser = StreamingEventParser { payload ->
            val eventType = payload.type?.lowercase() ?: return@StreamingEventParser
            when (eventType) {
                "text" -> {
                    val chunk = payload.content.orEmpty()
                    if (chunk.isNotEmpty()) {
                        textBuffer.append(chunk)
                        listener(KoogStreamEvent.ContentDelta(chunk))
                    }
                }
                "tool" -> {
                    val name = payload.name.orEmpty()
                    val args = payload.arguments
                        ?.mapValues { it.value?.toString().orEmpty() }
                        .orEmpty()
                    toolJobs += launch {
                        executeToolInstruction(name, args, permissionHandler, listener)
                    }
                }
            }
        }

        try {
            streamingClient.executeStreaming(llmPrompt, modelEntry.model).collect { chunk ->
                parser.append(chunk)
            }
            parser.finish()
            toolJobs.forEach { it.join() }

            val finalResponse = textBuffer.toString().trim()
            listener(KoogStreamEvent.Completed(finalResponse))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (ex: Exception) {
            logger.warn("Koog agent streaming failed", ex)
            listener(KoogStreamEvent.Error(ex))
        }
    }

    private suspend fun executeToolInstruction(
        name: String,
        arguments: Map<String, String>,
        permissionHandler: ToolPermissionHandler,
        listener: (KoogStreamEvent) -> Unit
    ) {
        val id = UUID.randomUUID().toString()
        val snapshot = KoogToolCallSnapshot(
            id = id,
            name = name,
            title = "Executing $name",
            status = "running",
            arguments = arguments,
            output = emptyList()
        )
        listener(KoogStreamEvent.ToolCallUpdate(snapshot, final = false))

        if (!permissionHandler.shouldExecute(name, arguments)) {
            listener(
                KoogStreamEvent.ToolCallUpdate(
                    snapshot.copy(
                        status = "declined",
                        output = listOf("User declined to run $name.")
                    ),
                    final = true
                )
            )
            return
        }

        val result = try {
            invokeTool(name, arguments)
        } catch (ex: Exception) {
            logger.warn("Koog tool $name failed", ex)
            listener(
                KoogStreamEvent.ToolCallUpdate(
                    snapshot.copy(
                        status = "failed",
                        output = listOf(ex.message ?: ex.javaClass.simpleName)
                    ),
                    final = true
                )
            )
            return
        }

        listener(
            KoogStreamEvent.ToolCallUpdate(
                snapshot.copy(
                    status = "completed",
                    output = listOf(result)
                ),
                final = true
            )
        )
    }

    private fun invokeTool(name: String, arguments: Map<String, String>): String {
        val definition = toolDefinitions[name]
            ?: error("Unknown tool '$name'. Available: ${toolDefinitions.keys.joinToString()}")

        val callArgs = mutableMapOf<KParameter, Any?>()
        callArgs[definition.instanceParameter] = toolset
        definition.parameters.forEach { parameter ->
            val rawValue = arguments[parameter.name]
            if (rawValue == null) {
                if (!parameter.parameter.isOptional && !parameter.parameter.type.isMarkedNullable) {
                    error("Missing argument '${parameter.name}' for tool '$name'.")
                }
            } else {
                callArgs[parameter.parameter] = convertArgument(rawValue, parameter.parameter.type)
            }
        }

        val result = definition.function.callBy(callArgs)
        return result?.toString().orEmpty()
    }

    private fun convertArgument(value: String, targetType: KType): Any? {
        val classifier = targetType.classifier
        return when (classifier) {
            Int::class -> {
                value.toIntOrNull()
                    ?: value.toDoubleOrNull()?.toInt()
                    ?: error("Expected integer but got '$value'")
            }
            Boolean::class -> value.equals("true", ignoreCase = true)
            else -> value
        }
    }

    private fun buildPrompt(history: List<ChatMessage>, systemPrompt: String) =
        prompt(id = "cod3-agent-${UUID.randomUUID()}") {
            system(systemPrompt)
            history.forEach { message ->
                when (message.role) {
                    ChatRole.USER -> user(formatMessageWithAttachments(message.content, message.attachments))
                    ChatRole.ASSISTANT -> assistant(message.content)
                }
            }
        }

    private fun buildStreamingSystemPrompt(customPrompt: String): String {
        val base = if (customPrompt.isBlank()) DEFAULT_SYSTEM_PROMPT else customPrompt.trim()
        return buildString {
            appendLine(base)
            appendLine()
            appendLine(
                "You MUST respond as newline-delimited JSON events. " +
                    "Every line must be a single JSON object."
            )
            appendLine(
                """Valid event shapes:
{"type":"text","content":"<plain language update (escape newlines as \\n, keep chunks <= 300 chars)>"} 
{"type":"tool","name":"<tool name>","arguments":{"arg1":"value","arg2":"value"}}"""
            )
            appendLine()
            appendLine("Available tools:")
            toolDefinitions.values.forEach { def ->
                appendLine(" - ${def.name}(${def.parameters.joinToString { it.name }}): ${def.description}")
            }
            appendLine()
            appendLine("Never output Markdown or free text outside the JSON events.")
            appendLine("Only call tools listed above and provide all required arguments.")
            appendLine("Continue emitting text events after tool calls to explain the work.")
        }
    }

    private fun buildToolDefinitions(): Map<String, ToolDefinition> {
        return KoogIdeToolset::class.memberFunctions
            .mapNotNull { function ->
                val annotation = function.findAnnotation<KoogTool>() ?: return@mapNotNull null
                val instanceParameter = function.instanceParameter ?: return@mapNotNull null
                val description = function.findAnnotation<LLMDescription>()?.description ?: function.name
                val parameters = function.parameters
                    .filter { it != instanceParameter }
                    .map { parameter ->
                        val name = parameter.name ?: return@mapNotNull null
                        ToolParameter(
                            name = name,
                            description = parameter.findAnnotation<LLMDescription>()?.description,
                            parameter = parameter
                        )
                    }
                ToolDefinition(
                    name = annotation.customName.ifBlank { function.name },
                    description = description,
                    function = function,
                    instanceParameter = instanceParameter,
                    parameters = parameters
                )
            }
            .associateBy { it.name }
    }

    override fun dispose() {
        scope.cancel()
    }

    private inner class StreamingEventParser(
        private val onEvent: (StreamingPayload) -> Unit
    ) {
        private val buffer = StringBuilder()

        fun append(chunk: String) {
            buffer.append(chunk)
            drain()
        }

        fun finish() {
            drain(final = true)
        }

        private fun drain(final: Boolean = false) {
            while (true) {
                val newline = buffer.indexOf("\n")
                if (newline == -1) {
                    if (final && buffer.isNotEmpty()) {
                        val remaining = buffer.toString().trim()
                        buffer.clear()
                        if (remaining.isNotEmpty()) {
                            parseLine(remaining)
                        }
                    }
                    return
                }
                val line = buffer.substring(0, newline).trim()
                buffer.delete(0, newline + 1)
                if (line.isNotEmpty()) {
                    parseLine(line)
                }
            }
        }

        private fun parseLine(line: String) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return
            if (!trimmed.startsWith("{")) {
                onEvent(
                    StreamingPayload(
                        type = "text",
                        content = trimmed
                    )
                )
                return
            }
            try {
                val payload = gson.fromJson(sanitizeJsonLine(trimmed), StreamingPayload::class.java)
                if (payload.type.isNullOrBlank()) return
                val decoded = payload.copy(
                    content = payload.content?.replace("\\n", "\n")
                )
                onEvent(decoded)
            } catch (ex: JsonSyntaxException) {
                logger.warn("Failed to parse streaming line: $line", ex)
            }
        }
    }

    private fun sanitizeJsonLine(line: String): String {
        val sb = StringBuilder(line.length + 8)
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (ch == '\\') {
                val next = line.getOrNull(i + 1)
                if (next == null) {
                    sb.append("\\\\")
                } else if (next in listOf('"', '\\', '/', 'b', 'f', 'n', 'r', 't')) {
                    sb.append('\\').append(next)
                    i++
                } else if (next == 'u' && i + 5 < line.length) {
                    sb.append("\\u").append(line.substring(i + 2, i + 6))
                    i += 5
                } else {
                    sb.append("\\\\").append(next)
                    i++
                }
            } else {
                sb.append(ch)
            }
            i++
        }
        return sb.toString()
    }

    private data class ToolDefinition(
        val name: String,
        val description: String,
        val function: KFunction<*>,
        val instanceParameter: KParameter,
        val parameters: List<ToolParameter>
    )

    private data class ToolParameter(
        val name: String,
        val description: String?,
        val parameter: KParameter
    )

    private data class StreamingPayload(
        val type: String?,
        val content: String? = null,
        val name: String? = null,
        val arguments: Map<String, Any?>? = null
    )

    companion object {
        private const val DEFAULT_SYSTEM_PROMPT =
            "You are Cod3, an IDE-native software engineer focused on precise, minimal edits."
    }
}

internal fun Project.koogAgentService(): KoogAgentService = service()

fun interface ToolPermissionHandler {
    fun shouldExecute(toolName: String?, args: Map<String, String>?): Boolean
}

sealed interface KoogStreamEvent {
    data class ContentDelta(val text: String) : KoogStreamEvent
    data class ToolCallUpdate(val snapshot: KoogToolCallSnapshot, val final: Boolean) : KoogStreamEvent
    data class Completed(val response: String) : KoogStreamEvent
    data class Error(val throwable: Throwable) : KoogStreamEvent
}

data class KoogToolCallSnapshot(
    val id: String,
    val name: String?,
    val title: String?,
    val status: String?,
    val arguments: Map<String, String>,
    val output: List<String>
)
