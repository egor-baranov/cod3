package com.github.egorbaranov.cod3.koog

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.feature.handler.agent.AgentCompletedContext
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingCompletedContext
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingFrameReceivedContext
import ai.koog.agents.core.feature.handler.tool.ToolCallCompletedContext
import ai.koog.agents.core.feature.handler.tool.ToolCallFailedContext
import ai.koog.agents.core.feature.handler.tool.ToolCallStartingContext
import ai.koog.agents.core.feature.handler.tool.ToolValidationFailedContext
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.tools
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.agents.mcp.defaultStdioTransport
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.streaming.StreamFrame
import com.github.egorbaranov.cod3.settings.PluginSettingsState
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.util.execution.ParametersListUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@Service(Service.Level.PROJECT)
class KoogAgentService(
    private val project: Project
): Disposable {

    private val logger = Logger.getInstance(KoogAgentService::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessions = ConcurrentHashMap<Int, KoogAgentSession>()
    private val sessionMutex = Mutex()

    fun run(chatId: Int, prompt: String, listener: (KoogStreamEvent) -> Unit): Job {
        val trimmed = prompt.trim()
        require(trimmed.isNotEmpty()) { "Koog prompt must not be blank." }

        return scope.launch {
            val session = prepareFreshSession(chatId)
            session.run(trimmed, listener)
        }
    }

    private suspend fun prepareFreshSession(chatId: Int): KoogAgentSession = sessionMutex.withLock {
        sessions.remove(chatId)?.close()
        val session = createSession()
        sessions[chatId] = session
        session
    }

    private suspend fun createSession(): KoogAgentSession = withContext(Dispatchers.IO) {
        val settings = PluginSettingsState.getInstance()
        val apiKey = settings.openAIApiKey.trim()
        if (apiKey.isEmpty()) {
            throw IllegalStateException("OpenAI API key is not configured in Cod3 settings.")
        }

        val executor = simpleOpenAIExecutor(apiKey)
        val eventBridge = KoogEventBridge()
        val toolRegistry = ToolRegistry {
            tools(KoogIdeToolset(project))
        }.also {
            logger.warn("Koog IDE tools registered: ${it.tools.map { tool -> tool.name }}")
        }

        val mcpHandle = createMcpHandle(settings)
        val registry = mcpHandle?.let { toolRegistry + it.registry } ?: toolRegistry
        logger.warn("Koog tool registry active set: ${registry.tools.map { tool -> tool.name }}")

        val requiredTools = listOf(
            "write_file",
            "edit_file",
            "find",
            "list_directory",
            "grep_search",
            "view_file",
            "run_command"
        )
        fun normalize(name: String) = name.replace("_", "").lowercase()
        val availableNormalized = registry.tools.associateBy { normalize(it.name) }
        val missing = requiredTools.filter { required ->
            availableNormalized[normalize(required)] == null
        }
        require(missing.isEmpty()) { "Missing Koog IDE tools: $missing; available=${registry.tools.map { it.name }}" }

        val systemPrompt = settings.koogSystemPrompt.ifBlank {
            DEFAULT_SYSTEM_PROMPT
        }

        val agent = AIAgent(
            promptExecutor = executor,
            llmModel = KoogModelCatalog.resolve(settings.koogModelId),
            toolRegistry = registry,
            systemPrompt = systemPrompt
        ) {
            install(EventHandler) {
                onLLMStreamingFrameReceived { ctx -> eventBridge.handleStreamFrame(ctx) }
                onLLMStreamingFailed { ctx -> eventBridge.emit(KoogStreamEvent.Error(ctx.error)) }
                onLLMStreamingCompleted { _: LLMStreamingCompletedContext -> }

                onToolCallStarting { eventBridge.handleToolStarting(it) }
                onToolCallCompleted { eventBridge.handleToolCompleted(it) }
                onToolCallFailed { eventBridge.handleToolFailed(it) }
                onToolValidationFailed { eventBridge.handleToolValidationFailed(it) }

                onAgentCompleted { eventBridge.handleAgentCompleted(it) }
                onAgentExecutionFailed { eventBridge.emit(KoogStreamEvent.Error(it.throwable)) }
            }
        }

        KoogAgentSession(agent, eventBridge, mcpHandle)
    }

    private suspend fun createMcpHandle(settings: PluginSettingsState): McpHandle? {
        val command = settings.koogMcpCommand.trim()
        if (command.isEmpty()) return null

        val parts = ParametersListUtil.parse(command)
        if (parts.isEmpty()) return null

        val processBuilder = ProcessBuilder(parts).apply {
            val workingDir = settings.koogMcpWorkingDirectory.takeIf { it.isNotBlank() }
                ?: project.basePath
            workingDir?.let { directory(File(it)) }
            redirectErrorStream(false)
        }

        val process = try {
            processBuilder.start()
        } catch (io: IOException) {
            logger.warn("Failed to start MCP server process", io)
            throw IllegalStateException("Cannot start MCP server: ${io.message}", io)
        }

        val transport = McpToolRegistryProvider.defaultStdioTransport(process)
        val registry = try {
            McpToolRegistryProvider.fromTransport(
                transport = transport,
                name = settings.koogMcpClientName.takeIf { it.isNotBlank() } ?: "cod3-koog-mcp"
            )
        } catch (ex: Exception) {
            process.destroyForcibly()
            logger.warn("Failed to initialise MCP registry", ex)
            throw ex
        }

        return McpHandle(process, registry)
    }

    companion object {
        private const val DEFAULT_SYSTEM_PROMPT =
            "You are Cod3, an IDE-native software engineer. " +
                "Prefer editing files directly, keep changes minimal, and explain your intent clearly."
    }

    override fun dispose() {
        scope.coroutineContext.cancel()
        sessions.values.forEach { it.close() }
        sessions.clear()
    }
}

private class KoogAgentSession(
    private val agent: AIAgent<String, String>,
    private val eventBridge: KoogEventBridge,
    private val mcpHandle: McpHandle?
) {
    private val mutex = Mutex()

    suspend fun run(prompt: String, listener: (KoogStreamEvent) -> Unit) {
        mutex.withLock {
            eventBridge.useListener(listener) {
                try {
                    val result = agent.run(prompt)
                    eventBridge.emitCompletion(result)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (ex: Exception) {
                    eventBridge.emit(KoogStreamEvent.Error(ex))
                }
            }
        }
    }

    fun close() {
        runCatching { runBlocking { agent.close() } }
        mcpHandle?.close()
    }
}

private class KoogEventBridge {
    private val listenerRef = AtomicReference<(KoogStreamEvent) -> Unit>()
    private val completed = AtomicBoolean(false)
    private val toolStore = KoogToolStore()

    suspend fun useListener(listener: (KoogStreamEvent) -> Unit, block: suspend () -> Unit) {
        listenerRef.set(listener)
        completed.set(false)
        try {
            block()
        } finally {
            listenerRef.set(null)
        }
    }

    fun emit(event: KoogStreamEvent) {
        listenerRef.get()?.invoke(event)
    }

    fun emitCompletion(result: String) {
        if (completed.compareAndSet(false, true)) {
            emit(KoogStreamEvent.Completed(result))
        }
    }

    fun handleStreamFrame(ctx: LLMStreamingFrameReceivedContext) {
        when (val frame = ctx.streamFrame) {
            is StreamFrame.Append -> emit(KoogStreamEvent.ContentDelta(frame.text))
            is StreamFrame.End -> Unit
            is StreamFrame.ToolCall -> {
                toolStore.handleStreamingToolCall(frame)?.let {
                    emit(KoogStreamEvent.ToolCallUpdate(it, final = false))
                }
            }
        }
    }

    fun handleToolStarting(ctx: ToolCallStartingContext) {
        val view = toolStore.onStart(ctx)
        emit(KoogStreamEvent.ToolCallUpdate(view, final = false))
    }

    fun handleToolCompleted(ctx: ToolCallCompletedContext) {
        val view = toolStore.onCompleted(ctx, success = true)
        emit(KoogStreamEvent.ToolCallUpdate(view, final = true))
    }

    fun handleToolFailed(ctx: ToolCallFailedContext) {
        val view = toolStore.onFailed(ctx)
        emit(KoogStreamEvent.ToolCallUpdate(view, final = true))
    }

    fun handleToolValidationFailed(ctx: ToolValidationFailedContext) {
        val view = toolStore.onValidationFailed(ctx)
        emit(KoogStreamEvent.ToolCallUpdate(view, final = true))
    }

    fun handleAgentCompleted(ctx: AgentCompletedContext) {
        emitCompletion(ctx.result?.toString().orEmpty())
    }
}

private class KoogToolStore {
    private val snapshots = ConcurrentHashMap<String, KoogToolCallSnapshot>()

    fun handleStreamingToolCall(frame: StreamFrame.ToolCall): KoogToolCallSnapshot? {
        val id = frame.id ?: return null
        val snapshot = snapshots[id]
        return snapshot?.copy(
            arguments = snapshot.arguments + mapOf("stream" to frame.content),
            status = "streaming"
        )?.also { snapshots[id] = it }
    }

    fun onStart(ctx: ToolCallStartingContext): KoogToolCallSnapshot {
        val id = ctx.toolCallId ?: UUID.randomUUID().toString()
        val snapshot = KoogToolCallSnapshot(
            id = id,
            name = ctx.tool.name,
            title = ctx.tool.description,
            status = "running",
            arguments = encodeArgs(ctx.toolArgs),
            output = emptyList()
        )
        snapshots[id] = snapshot
        return snapshot
    }

    fun onCompleted(ctx: ToolCallCompletedContext, success: Boolean): KoogToolCallSnapshot {
        val id = ctx.toolCallId ?: UUID.randomUUID().toString()
        val prev = snapshots[id]
        val snapshot = (prev ?: onStart(ToolCallStartingContext(ctx.runId, id, ctx.tool, ctx.toolArgs))).copy(
            status = if (success) "completed" else "failed",
            output = encodeResult(ctx.result)
        )
        snapshots[id] = snapshot
        return snapshot
    }

    fun onFailed(ctx: ToolCallFailedContext): KoogToolCallSnapshot {
        val id = ctx.toolCallId ?: UUID.randomUUID().toString()
        val prev = snapshots[id]
        val snapshot = (prev ?: onStart(ToolCallStartingContext(ctx.runId, id, ctx.tool, ctx.toolArgs))).copy(
            status = "failed",
            output = listOf(ctx.throwable.message ?: ctx.throwable.javaClass.simpleName)
        )
        snapshots[id] = snapshot
        return snapshot
    }

    fun onValidationFailed(ctx: ToolValidationFailedContext): KoogToolCallSnapshot {
        val id = ctx.toolCallId ?: UUID.randomUUID().toString()
        val prev = snapshots[id]
        val snapshot = (prev ?: onStart(ToolCallStartingContext(ctx.runId, id, ctx.tool, ctx.toolArgs))).copy(
            status = "invalid",
            output = listOf(ctx.error)
        )
        snapshots[id] = snapshot
        return snapshot
    }

    private fun encodeArgs(args: Any?): Map<String, String> =
        when (args) {
            null -> emptyMap()
            is Map<*, *> -> args.mapNotNull { (key, value) ->
                (key as? String)?.let { it to (value?.toString() ?: "") }
            }.toMap()
            else -> mapOf("value" to args.toString())
        }

    private fun encodeResult(result: Any?): List<String> {
        if (result == null) return emptyList()
        return listOf(result.toString())
    }
}

private data class McpHandle(
    val process: Process,
    val registry: ToolRegistry
) {
    fun close() {
        runCatching { process.destroy() }
    }
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

internal fun Project.koogAgentService(): KoogAgentService = service()
