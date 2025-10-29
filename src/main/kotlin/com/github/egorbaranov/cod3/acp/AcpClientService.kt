package com.github.egorbaranov.cod3.acp

import com.agentclientprotocol.client.*
import com.agentclientprotocol.common.ClientSessionOperations
import com.agentclientprotocol.common.Event
import com.agentclientprotocol.common.SessionParameters
import com.agentclientprotocol.model.*
import com.agentclientprotocol.protocol.Protocol
import com.agentclientprotocol.transport.StdioTransport
import com.github.egorbaranov.cod3.settings.PluginSettingsState
import com.intellij.openapi.Disposable
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.execution.ParametersListUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.*
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.math.min

@Service(Service.Level.PROJECT)
class AcpClientService(
    private val project: Project,
) : Disposable {

    private val logger = Logger.getInstance(AcpClientService::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("Cod3Acp"))
    private val connectionMutex = Mutex()

    @Volatile
    private var connection: AcpConnection? = null

    fun sendPrompt(
        message: String,
        listener: (AcpStreamEvent) -> Unit
    ): Job {
        val trimmed = message.trim()
        require(trimmed.isNotEmpty()) { "ACP prompt message must not be blank." }

        return scope.launch {
            val activeToolCalls = mutableMapOf<String, ActiveToolCall>()
            val session = try {
                ensureSession()
            } catch (ex: Exception) {
                logger.warn("Failed to establish ACP session", ex)
                listener(AcpStreamEvent.Error(ex))
                return@launch
            }

            try {
                session.prompt(listOf(ContentBlock.Text(trimmed))).collect { event ->
                    when (event) {
                        is Event.SessionUpdateEvent -> handleSessionUpdate(event.update, activeToolCalls, listener)
                        is Event.PromptResponseEvent -> listener(AcpStreamEvent.Completed(event.response))
                    }
                }
            } catch (cancelled: CancellationException) {
                logger.debug("ACP prompt cancelled", cancelled)
                // Allow cancellation without surfacing an error bubble.
            } catch (ex: Exception) {
                logger.warn("ACP prompt failed", ex)
                listener(AcpStreamEvent.Error(ex))
            }
        }
    }

    private suspend fun ensureSession(): ClientSession {
        val settings = PluginSettingsState.getInstance()

        if (!settings.useAgentClientProtocol) {
            throw IllegalStateException("Agent Client Protocol support is disabled in plugin settings.")
        }

        val command = settings.acpAgentCommand.trim()
        if (command.isEmpty()) {
            throw IllegalStateException("ACP agent command is not configured. Set it in plugin settings.")
        }
        val commandParts = ParametersListUtil.parse(command)
        if (commandParts.isEmpty()) {
            throw IllegalStateException("ACP agent command could not be parsed.")
        }

        val workingDir = settings.acpAgentWorkingDirectory.trim().takeIf { it.isNotEmpty() }
        val sessionCwd = settings.acpSessionRoot.trim()
            .takeIf { it.isNotEmpty() }
            ?: project.basePath
            ?: System.getProperty("user.dir")

        return connectionMutex.withLock {
            val existing = connection
            if (existing != null && existing.matches(commandParts, workingDir, sessionCwd) && existing.isAlive()) {
                return@withLock existing.session
            }

            existing?.close()

            val newConnection = createConnection(commandParts, workingDir, sessionCwd)
            connection = newConnection
            newConnection.session
        }
    }

    private suspend fun createConnection(
        commandParts: List<String>,
        workingDirectory: String?,
        sessionCwd: String
    ): AcpConnection {
        val processBuilder = ProcessBuilder(commandParts).apply {
            if (workingDirectory != null) {
                directory(File(workingDirectory))
            } else {
                project.basePath?.let { directory(File(it)) }
            }
            redirectErrorStream(false)
        }

        val process = try {
            processBuilder.start()
        } catch (ex: IOException) {
            throw IllegalStateException("Failed to start ACP agent process: ${ex.message}", ex)
        }

        val stderrJob = scope.launch(Dispatchers.IO + CoroutineName("Cod3AcpStderr")) {
            process.errorStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (line.isNotBlank()) {
                        logger.warn("[ACP stderr] $line")
                    }
                }
            }
        }

        val transport = StdioTransport(
            parentScope = scope,
            ioDispatcher = Dispatchers.IO,
            input = process.inputStream.asSource().buffered(),
            output = process.outputStream.asSink().buffered(),
            name = "Cod3AcpTransport"
        )

        val protocol = Protocol(scope, transport)
        val client = Client(
            protocol = protocol,
            clientSupport = IdeClientSupport(project),
            handlerSideExtensions = listOf(FileSystemOperations)
        )

        protocol.start()

        try {
            client.initialize(
                ClientInfo(
                    capabilities = ClientCapabilities(
                        fs = FileSystemCapability(readTextFile = true, writeTextFile = true)
                    )
                )
            )
            val session = client.newSession(
                SessionParameters(
                    cwd = sessionCwd,
                    mcpServers = emptyList()
                )
            )

            return AcpConnection(
                command = commandParts,
                workingDirectory = workingDirectory,
                sessionRoot = sessionCwd,
                process = process,
                protocol = protocol,
                transport = transport,
                client = client,
                session = session,
                stderrJob = stderrJob
            )
        } catch (ex: Exception) {
            logger.warn("Failed to initialise ACP client/session", ex)
            stderrJob.cancel()
            transport.close()
            process.destroyForcibly()
            throw ex
        }
    }

    private fun handleSessionUpdate(
        update: SessionUpdate,
        activeToolCalls: MutableMap<String, ActiveToolCall>,
        listener: (AcpStreamEvent) -> Unit
    ) {
        when (update) {
            is SessionUpdate.AgentMessageChunk -> {
                contentBlockToText(update.content)?.takeIf { it.isNotBlank() }?.let {
                    listener(AcpStreamEvent.AgentContentText(it))
                }
            }

            is SessionUpdate.AgentThoughtChunk -> {
                contentBlockToText(update.content)?.takeIf { it.isNotBlank() }?.let {
                    listener(AcpStreamEvent.AgentContentText("*(thinking)* $it"))
                }
            }

            is SessionUpdate.PlanUpdate -> {
                val planText = buildPlanText(update)
                if (planText.isNotBlank()) {
                    listener(AcpStreamEvent.AgentContentText(planText))
                }
            }

            is SessionUpdate.ToolCall -> {
                handleToolCallEvent(
                    id = update.toolCallId.value,
                    status = update.status,
                    rawInput = update.rawInput,
                    rawOutput = update.rawOutput,
                    content = update.content,
                    activeToolCalls = activeToolCalls,
                    listener = listener
                )
            }

            is SessionUpdate.ToolCallUpdate -> {
                handleToolCallEvent(
                    id = update.toolCallId.value,
                    status = update.status,
                    rawInput = update.rawInput,
                    rawOutput = update.rawOutput,
                    content = update.content.orEmpty(),
                    activeToolCalls = activeToolCalls,
                    listener = listener
                )
            }

            else -> {
                // Ignore other updates for now.
            }
        }
    }

    private fun handleToolCallEvent(
        id: String,
        status: ToolCallStatus?,
        rawInput: JsonElement?,
        rawOutput: JsonElement?,
        content: List<ToolCallContent>,
        activeToolCalls: MutableMap<String, ActiveToolCall>,
        listener: (AcpStreamEvent) -> Unit
    ) {
        val state = activeToolCalls.getOrPut(id) { ActiveToolCall(id) }

        parseToolCallPayload(rawInput)?.let { payload ->
            if (payload.name != null) {
                state.name = payload.name
            }
            if (payload.arguments.isNotEmpty()) {
                state.arguments.putAll(payload.arguments)
            }
        }

        if (content.isNotEmpty()) {
            val summary = content.joinToString("\n") { toolCallContentToText(it) }.trim()
            if (summary.isNotEmpty()) {
                state.arguments.compute("content") { _, existing ->
                    listOfNotNull(existing, summary).joinToString("\n").trim()
                }
            }
        }

        parseToolCallPayload(rawOutput)?.let { payload ->
            if (payload.arguments.isNotEmpty()) {
                state.arguments.putAll(payload.arguments)
            }
        }

        state.status = status ?: state.status

        val final = state.status == ToolCallStatus.COMPLETED || state.status == ToolCallStatus.FAILED
        val snapshot = state.snapshot()
        listener(AcpStreamEvent.ToolCallUpdate(snapshot, final))

        if (final) {
            activeToolCalls.remove(id)
        }
    }

    private fun buildPlanText(update: SessionUpdate.PlanUpdate): String {
        if (update.entries.isEmpty()) return ""
        val builder = StringBuilder("Plan:\n")
        update.entries.forEachIndexed { idx, entry ->
            builder.append(idx + 1)
                .append(". ")
                .append(entry.content)
            builder.append(" [")
                .append(entry.status.name.lowercase())
                .append("]")
            builder.append('\n')
        }
        return builder.toString().trimEnd()
    }

    private fun contentBlockToText(content: ContentBlock): String? {
        return when (content) {
            is ContentBlock.Text -> content.text
            is ContentBlock.Image -> "[Image ${content.uri ?: content.mimeType}]"
            is ContentBlock.Audio -> "[Audio ${content.mimeType}]"
            is ContentBlock.ResourceLink -> "Resource ${content.name} -> ${content.uri}"
            is ContentBlock.Resource -> when (val resource = content.resource) {
                is EmbeddedResourceResource.TextResourceContents -> resource.text
                is EmbeddedResourceResource.BlobResourceContents -> "[Blob ${resource.mimeType ?: "application/octet-stream"}]"
            }
        }
    }

    private fun toolCallContentToText(content: ToolCallContent): String {
        return when (content) {
            is ToolCallContent.Content -> contentBlockToText(content.content) ?: ""
            is ToolCallContent.Diff -> buildString {
                append("Diff for ${content.path}")
                content.oldText?.let {
                    append("\n--- Original ---\n")
                    append(it.trimEnd())
                }
                append("\n--- New ---\n")
                append(content.newText.trimEnd())
            }

            is ToolCallContent.Terminal -> "Terminal output available (id=${content.terminalId})"
        }
    }

    private fun parseToolCallPayload(element: JsonElement?): ToolCallPayload? {
        if (element == null || element === JsonNull) return null

        if (element is JsonObject) {
            val name = element["name"]?.jsonPrimitive?.contentOrNull
            val argumentsElement = element["arguments"] ?: element["args"]
            val arguments = when (argumentsElement) {
                null -> element.filterKeys { it != "name" }.mapValues { (_, value) -> jsonElementToString(value) }
                else -> jsonElementToMap(argumentsElement)
            }
            return ToolCallPayload(name, arguments)
        }

        return ToolCallPayload(null, mapOf("value" to jsonElementToString(element)))
    }

    private fun jsonElementToMap(element: JsonElement): Map<String, String> =
        when (element) {
            is JsonObject -> element.mapValues { (_, value) -> jsonElementToString(value) }
            is JsonArray -> element.mapIndexed { index, value ->
                index.toString() to jsonElementToString(value)
            }.toMap()

            is JsonPrimitive -> mapOf("value" to jsonElementToString(element))
            else -> emptyMap()
        }

    private fun jsonElementToString(element: JsonElement): String = when (element) {
        is JsonPrimitive -> if (element.isString) element.content else element.toString()
        is JsonObject -> element.toString()
        is JsonArray -> element.joinToString(prefix = "[", postfix = "]") { jsonElementToString(it) }
        else -> element.toString()
    }

    override fun dispose() {
        val result = runCatching {
            kotlinx.coroutines.runBlocking {
                connectionMutex.withLock {
                    connection?.close()
                    connection = null
                }
            }
        }
        result.exceptionOrNull()?.let {
            logger.warn("Failed to dispose ACP connection cleanly", it)
        }
        scope.cancel()
    }

    private data class AcpConnection(
        val command: List<String>,
        val workingDirectory: String?,
        val sessionRoot: String,
        val process: Process,
        val protocol: Protocol,
        val transport: StdioTransport,
        val client: Client,
        val session: ClientSession,
        val stderrJob: Job
    ) {
        fun isAlive(): Boolean = process.isAlive

        fun matches(command: List<String>, workingDirectory: String?, sessionRoot: String): Boolean {
            return this.command == command &&
                    this.sessionRoot == sessionRoot &&
                    (this.workingDirectory ?: "") == (workingDirectory ?: "")
        }

        fun close() {
            stderrJob.cancel()
            transport.close()
            if (process.isAlive) {
                process.destroy()
            }
        }
    }

    private data class ToolCallPayload(
        val name: String?,
        val arguments: Map<String, String>
    )

    private class ActiveToolCall(val id: String) {
        var name: String? = null
        val arguments: MutableMap<String, String> = linkedMapOf()
        var status: ToolCallStatus? = null

        fun snapshot(): ToolCallSnapshot =
            ToolCallSnapshot(id, name, LinkedHashMap(arguments), status)
    }

    private class IdeClientSupport(
        private val project: Project
    ) : ClientSupport {
        override suspend fun createClientSession(
            session: ClientSession,
            _sessionResponseMeta: JsonElement?
        ): ClientSessionOperations {
            return IdeClientSessionOperations(project)
        }
    }

    private class IdeClientSessionOperations(
        private val project: Project
    ) : ClientSessionOperations, FileSystemOperations {

        private val logger = Logger.getInstance(IdeClientSessionOperations::class.java)

        override suspend fun requestPermissions(
            toolCall: SessionUpdate.ToolCallUpdate,
            permissions: List<PermissionOption>,
            _meta: JsonElement?
        ): RequestPermissionResponse {
            val selected = permissions.firstOrNull {
                it.kind == com.agentclientprotocol.model.PermissionOptionKind.ALLOW_ALWAYS ||
                        it.kind == com.agentclientprotocol.model.PermissionOptionKind.ALLOW_ONCE
            } ?: permissions.firstOrNull()
            return if (selected != null) {
                RequestPermissionResponse(
                    RequestPermissionOutcome.Selected(selected.optionId)
                )
            } else {
                RequestPermissionResponse(RequestPermissionOutcome.Cancelled)
            }
        }

        override suspend fun notify(notification: SessionUpdate, _meta: JsonElement?) {
            logger.info("ACP notification: $notification")
        }

        override suspend fun fsReadTextFile(
            path: String,
            line: UInt?,
            limit: UInt?,
            _meta: JsonElement?
        ): com.agentclientprotocol.model.ReadTextFileResponse {
            val resolved = resolveProjectPath(path)
            val text = withContext(Dispatchers.IO) {
                Files.readString(resolved)
            }
            if (line == null && limit == null) {
                return com.agentclientprotocol.model.ReadTextFileResponse(text)
            }

            val lines = text.split('\n')
            val startLine = line?.toIntOrNullPositive()?.minus(1)?.coerceAtLeast(0) ?: 0
            val count = limit?.toIntOrNullPositive()?.let { min(it, lines.size - startLine) } ?: (lines.size - startLine)
            val sliced = if (startLine >= lines.size) emptyList() else lines.subList(startLine, startLine + count)
            return com.agentclientprotocol.model.ReadTextFileResponse(sliced.joinToString("\n"))
        }

        override suspend fun fsWriteTextFile(
            path: String,
            content: String,
            _meta: JsonElement?
        ): com.agentclientprotocol.model.WriteTextFileResponse {
            val resolved = resolveProjectPath(path)
            val parent = resolved.parent ?: throw IllegalArgumentException("Cannot determine parent directory for $path")
            withContext(Dispatchers.IO) {
                Files.createDirectories(parent)
            }
            WriteCommandAction.runWriteCommandAction(project) {
                val targetDir = VfsUtil.createDirectories(FileUtil.toSystemIndependentName(parent.toString()))
                val targetFile = targetDir.findChild(resolved.fileName.toString())
                    ?: targetDir.createChildData(this, resolved.fileName.toString())
                VfsUtil.saveText(targetFile, content)
                targetFile.refresh(false, false)
            }
            return com.agentclientprotocol.model.WriteTextFileResponse()
        }

        private fun resolveProjectPath(path: String): Path {
            val basePath = project.basePath ?: throw IllegalStateException("Project base path is not available.")
            val base = Paths.get(basePath).normalize()
            val candidate = Paths.get(path)
            val resolved = if (candidate.isAbsolute) candidate.normalize() else base.resolve(path).normalize()
            if (!resolved.startsWith(base)) {
                throw IllegalArgumentException("Path '$path' escapes the project root.")
            }
            return resolved
        }

        private fun UInt.toIntOrNullPositive(): Int? {
            val value = toLong()
            return if (value > Int.MAX_VALUE) null else value.toInt()
        }
    }
}
