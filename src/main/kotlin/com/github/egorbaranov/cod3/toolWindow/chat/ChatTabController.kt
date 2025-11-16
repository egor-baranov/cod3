package com.github.egorbaranov.cod3.toolWindow.chat

import com.github.egorbaranov.cod3.acp.AcpClientService
import com.github.egorbaranov.cod3.acp.AcpStreamEvent
import com.github.egorbaranov.cod3.koog.*
import com.github.egorbaranov.cod3.settings.PluginSettingsState
import com.github.egorbaranov.cod3.toolWindow.ToolCall
import com.github.egorbaranov.cod3.ui.Icons
import com.github.egorbaranov.cod3.ui.components.ReferencePopupProvider
import com.github.egorbaranov.cod3.ui.components.ScrollableSpacedPanel
import com.github.egorbaranov.cod3.ui.components.createModelComboBox
import com.github.egorbaranov.cod3.ui.createResizableEditor
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.IconLabelButton
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.awt.geom.Area
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference
import javax.swing.*
import kotlinx.coroutines.Job

internal class ChatTabController(
    private val project: Project,
    private val chatIndex: Int,
    private val messages: MutableMap<Int, MutableList<ChatMessage>>,
    private val logger: Logger,
    private val onHistoryChanged: () -> Unit = {}
) {

    private val toolProcessor = ToolCallProcessor()
    private var agentMode: AgentMode = AgentMode.AGENT_CONFIRM
    private lateinit var messageContainer: JPanel
    private lateinit var scroll: JScrollPane
    private lateinit var planRenderer: PlanRenderer
    private lateinit var toolRenderer: ToolRenderer
    private lateinit var referencePopupProvider: ReferencePopupProvider
    private lateinit var sendButton: IconLabelButton
    private lateinit var stopButton: IconLabelButton
    private lateinit var actionButtonPanel: JPanel
    private lateinit var actionButtonLayout: CardLayout
    private var activeJob: Job? = null
    private var thinkingBubble: com.github.egorbaranov.cod3.ui.components.ChatBubble? = null
    private var thinkingTimer: Timer? = null
    private var thinkingDots = 0
    private fun currentConversation(): MutableList<ChatMessage> =
        messages.getOrPut(chatIndex) { mutableListOf() }

    private fun recordUserTurn(content: String, attachments: List<ChatAttachment>) {
        currentConversation().add(ChatMessage(ChatRole.USER, content, attachments))
        onHistoryChanged()
    }

    private fun recordAssistantTurn(content: String) {
        val normalized = content.trim()
        if (normalized.isEmpty()) return
        currentConversation().add(ChatMessage(ChatRole.ASSISTANT, normalized))
        onHistoryChanged()
    }

    fun createPanel(): JComponent {
        messageContainer = JBPanel<JBPanel<*>>(VerticalLayout(JBUI.scale(8))).apply {
            border = JBUI.Borders.empty(4)
        }

        scroll = JBScrollPane(messageContainer).apply {
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        }

        planRenderer = PlanRenderer(messageContainer, scroll)
        toolRenderer = ToolRenderer(messageContainer, scroll)

        lateinit var popupProvider: ReferencePopupProvider
        val editorTextField = createResizableEditor(project, minHeight = 48) {
            sendMessage(popupProvider.editorTextField.text.trim())
        }

        val contextPanel = ScrollableSpacedPanel(4).apply {
            alignmentY = Component.CENTER_ALIGNMENT
        }
        popupProvider = ReferencePopupProvider(editorTextField, contextPanel)
        referencePopupProvider = popupProvider

        sendButton = IconLabelButton(Icons.Send) { sendMessage(popupProvider.editorTextField.text.trim()) }.apply {
            minimumSize = Dimension(24, 24)
            preferredSize = Dimension(24, 24)
            cursor = Cursor(Cursor.HAND_CURSOR)
        }
        stopButton = IconLabelButton(AllIcons.Actions.Suspend) { interruptGeneration() }.apply {
            minimumSize = Dimension(24, 24)
            preferredSize = Dimension(24, 24)
            cursor = Cursor(Cursor.HAND_CURSOR)
        }
        actionButtonLayout = CardLayout()
        actionButtonPanel = JPanel(actionButtonLayout).apply {
            isOpaque = false
            add(sendButton, "send")
            add(stopButton, "stop")
            actionButtonLayout.show(this, "send")
        }

        val inputBar = createInputBar(popupProvider, actionButtonPanel)
        renderExistingConversation()

        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
            add(scroll, BorderLayout.CENTER)
            add(inputBar, BorderLayout.SOUTH)
        }
    }

    private fun renderExistingConversation() {
        messages[chatIndex].orEmpty().forEach { message ->
            when (message.role) {
                ChatRole.USER -> messageContainer.appendUserBubble(message.content, message.attachments, project)
                ChatRole.ASSISTANT -> messageContainer.appendAssistantBubble(message.content)
            }
        }
        scrollToBottom(scroll)
    }

    private fun sendMessage(text: String?) {
        val trimmed = text?.trim().orEmpty()
        if (activeJob?.isActive == true) return
        val hasAttachments = referencePopupProvider.hasAttachments()
        if (trimmed.isEmpty() && !hasAttachments) return

        val attachments = referencePopupProvider.detachAttachments()
            .map { ChatAttachment(it.title, it.content, it.icon, it.navigationPath) }

        ApplicationManager.getApplication().invokeLater {
            WriteCommandAction.runWriteCommandAction(project) {
                referencePopupProvider.editorTextField.text = ""
            }
        }

        if (trimmed.isNotEmpty() || attachments.isNotEmpty()) {
            messageContainer.appendUserBubble(trimmed, attachments, project)
            scrollToBottom(scroll)
        }

        val settings = PluginSettingsState.getInstance()
        when {
            settings.useKoogAgents -> {
                if (agentMode == AgentMode.CHAT) {
                    sendMessageViaKoogChat(trimmed, attachments)
                } else {
                    sendMessageViaKoogAgent(trimmed, attachments)
                }
                return
            }

            settings.useAgentClientProtocol -> {
                sendMessageViaAcp(trimmed, attachments)
                return
            }

            else -> sendMessageViaKoogChat(trimmed, attachments)
        }
    }

    private fun sendMessageViaAcp(userMessage: String, attachments: List<ChatAttachment>) {
        recordUserTurn(userMessage, attachments)
        val accumulatedText = StringBuilder()
        val textBubbleRef = AtomicReference<com.github.egorbaranov.cod3.ui.components.ChatBubble?>()

        fun finalizeAssistantBubble() {
            if (accumulatedText.isNotEmpty()) {
                accumulatedText.setLength(0)
            }
            textBubbleRef.set(null)
        }

        val service = project.service<AcpClientService>()

        val job = service.sendPrompt(formatMessageWithAttachments(userMessage, attachments)) { event ->
            when (event) {
                is AcpStreamEvent.AgentContentText -> appendStreamingAssistant(
                    accumulatedText,
                    textBubbleRef,
                    event.text
                )

                is AcpStreamEvent.PlanUpdate -> {
                    finalizeAssistantBubble()
                    planRenderer.render(event.entries)
                }

                is AcpStreamEvent.ToolCallUpdate -> {
                    finalizeAssistantBubble()
                    handleAcpToolUpdate(event)
                }

                is AcpStreamEvent.Completed -> {
                    recordAssistantTurn(accumulatedText.toString())
                }
                is AcpStreamEvent.Error -> {
                    val errorMessage = event.throwable.message?.takeIf { !it.contains("StandaloneCoroutine was cancelled") }
                        ?: "Conversation paused"
                    val prefix = if (errorMessage == "Conversation paused") "" else "Error: "
                    appendStreamingAssistant(accumulatedText, textBubbleRef, "\n\n$prefix$errorMessage")
                }
            }
        }
        beginStreaming(job)
    }

    private fun appendStreamingAssistant(
        accumulator: StringBuilder,
        bubbleRef: AtomicReference<com.github.egorbaranov.cod3.ui.components.ChatBubble?>,
        chunk: String
    ) {
        if (chunk.isEmpty()) return
        stopThinkingIndicator()
        accumulator.append(chunk)
        val updated = accumulator.toString()
        SwingUtilities.invokeLater {
            val existing = bubbleRef.get()
            if (existing != null) {
                existing.updateText(updated, forceReplace = true)
            } else {
                bubbleRef.set(messageContainer.appendAssistantBubble(updated))
            }
            scrollToBottom(scroll)
        }
    }

    private fun handleAcpToolUpdate(event: AcpStreamEvent.ToolCallUpdate) {
        toolRenderer.render(event.toolCall.toViewModel(), event.final)
        if (!event.final) return

        val toolName = event.toolCall.name ?: return
        if (!shouldExecuteTool(toolName, event.toolCall.arguments)) {
            return
        }
        val toolCall = ToolCall(toolName, event.toolCall.arguments.takeIf { it.isNotEmpty() })
        val result = try {
            toolProcessor.process(project, toolCall, messageContainer)
        } catch (e: Exception) {
            logger.warn("Tool call '$toolName' failed", e)
            "Error executing tool $toolName: ${e.message}"
        }
        val message = result ?: "Tool $toolName executed."
        messageContainer.appendUserBubble(message, project = project)
        scrollToBottom(scroll)
    }

    private fun sendMessageViaKoogChat(text: String, attachments: List<ChatAttachment>) {
        val conversation = currentConversation()
        recordUserTurn(text, attachments)

        val accumulatedText = StringBuilder()
        val bubbleRef = AtomicReference<com.github.egorbaranov.cod3.ui.components.ChatBubble?>()

        val job = project.koogChatService().stream(conversation.toList()) { event ->
            when (event) {
                is KoogChatStreamEvent.ContentDelta -> appendStreamingAssistant(accumulatedText, bubbleRef, event.text)
                is KoogChatStreamEvent.Completed -> {
                    recordAssistantTurn(event.response)
                }

                is KoogChatStreamEvent.Error -> {
                    val errorMessage = event.throwable.message?.takeIf { !it.contains("StandaloneCoroutine was cancelled") }
                        ?: "Conversation paused"
                    val prefix = if (errorMessage == "Conversation paused") "" else "Error: "
                    appendStreamingAssistant(accumulatedText, bubbleRef, "\n\n$prefix$errorMessage")
                }
            }
        }
        beginStreaming(job)
    }

    private fun createInputBar(
        referencePopupProvider: ReferencePopupProvider,
        actionButton: JComponent
    ): JPanel = object : JPanel(BorderLayout()) {
        init {
            isOpaque = false
        }

        override fun paintBorder(g: Graphics) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = JBUI.CurrentTheme.Focus.defaultButtonColor()
            g2.stroke = if (referencePopupProvider.editorTextField.isFocusOwner) BasicStroke(1.5F) else BasicStroke(0F)
            g2.drawRoundRect(0, 0, width - 1, height - 1, 24, 24)
            g2.dispose()
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            val area = Area(Rectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat()))
            val roundedRect = RoundRectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat(), 24f, 24f)
            area.intersect(Area(roundedRect))
            g2.clip = area
            g2.color = JBColor.gray.darker().darker().darker().darker()
            g2.fill(area)
            super.paintComponent(g2)
            g2.dispose()
        }
    }.apply {
        border = JBUI.Borders.empty(4)

        val addContextButton = IconLabelButton(Icons.Mention) {
            referencePopupProvider.checkPopup(true)
        }.apply {
            minimumSize = Dimension(24, 24)
            preferredSize = Dimension(24, 24)
            cursor = Cursor(Cursor.HAND_CURSOR)
        }

        val scrollPane = JBScrollPane(referencePopupProvider.contextReferencePanel).apply {
            isOpaque = false
            border = JBUI.Borders.empty(2, 0)
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_NEVER
            viewport.isOpaque = false
            viewport.background = UIUtil.TRANSPARENT_COLOR
        }

        val header = panel {
            row {
                cell(addContextButton).align(AlignX.LEFT).gap(RightGap.SMALL)
                cell(scrollPane).align(Align.FILL)
            }
        }.andTransparent().withBorder(JBUI.Borders.empty(0, 4))

        add(header, BorderLayout.NORTH)
        add(referencePopupProvider.editorTextField, BorderLayout.CENTER)

        val footer = panel {
            row {
                cell(createModelComboBox())
                cell(createAgentModeDropdown())
                cell(actionButton).align(AlignX.RIGHT)
            }
        }.andTransparent().withBorder(JBUI.Borders.empty(0, 4))

        add(footer, BorderLayout.SOUTH)
    }

    private fun createAgentModeDropdown(): JComponent {
        val action = object : ComboBoxAction() {
            override fun update(e: AnActionEvent) {
                e.presentation.text = agentMode.displayName
            }

            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

            override fun createPopupActionGroup(button: JComponent?): DefaultActionGroup {
                val comboPresentation = (button as? ComboBoxButton)?.presentation ?: templatePresentation
                return DefaultActionGroup().apply {
                    AgentMode.entries.forEach { mode ->
                        add(object : AnAction(mode.displayName) {
                            override fun actionPerformed(e: AnActionEvent) {
                                agentMode = mode
                                comboPresentation.text = mode.displayName
                            }

                            override fun update(e: AnActionEvent) {
                                e.presentation.isEnabled = agentMode != mode
                            }

                            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
                        })
                    }
                }
            }

            override fun createCustomComponent(presentation: com.intellij.openapi.actionSystem.Presentation, place: String): JComponent {
                val button = createComboBoxButton(presentation)
                button.foreground = EditorColorsManager.getInstance().globalScheme.defaultForeground
                button.border = null
                button.putClientProperty("JButton.backgroundColor", Color(0, 0, 0, 0))
                return button
            }
        }

        val presentation = action.templatePresentation
        presentation.text = agentMode.displayName

        return action.createCustomComponent(presentation, ActionPlaces.UNKNOWN)
    }

    private fun shouldExecuteTool(toolName: String?, args: Map<String, String>?): Boolean {
        val readableName = toolName ?: "tool"
        return when (agentMode) {
            AgentMode.CHAT -> {
                appendToolInfo("Skipped $readableName because Chat mode disables tool calls.")
                false
            }

            AgentMode.AGENT_CONFIRM -> {
                val prompt = buildApprovalPrompt(readableName, args)
                val app = ApplicationManager.getApplication()
                val inlineFlow = !app.isDispatchThread
                val approved = if (inlineFlow) {
                    requestInlineToolApproval(readableName, args)
                } else {
                    showApprovalDialog(prompt)
                }
                if (!approved && !inlineFlow) {
                    appendToolInfo("Declined $readableName.")
                }
                approved
            }

            AgentMode.AGENT_AUTO -> true
        }
    }

    private fun appendToolInfo(text: String) {
        SwingUtilities.invokeLater {
            messageContainer.appendAssistantBubble(text)
            scrollToBottom(scroll)
        }
    }

    private fun showApprovalDialog(prompt: String): Boolean {
        val app = ApplicationManager.getApplication()
        val dialogAction = {
            Messages.showYesNoDialog(
                project,
                prompt,
                "Approve Tool Execution",
                Messages.getYesButton(),
                Messages.getNoButton(),
                null
            )
        }

        val result = if (app.isDispatchThread) {
            dialogAction()
        } else {
            var decision = Messages.NO
            app.invokeAndWait({
                decision = dialogAction()
            }, ModalityState.any())
            decision
        }
        return result == Messages.YES
    }

    private fun buildApprovalPrompt(toolName: String, args: Map<String, String>?): String {
        val argDetails = args?.entries
            ?.joinToString(separator = "\n") { "${it.key}: ${it.value}" }
            ?.takeIf { it.isNotBlank() }
        return buildString {
            appendLine("Agent requests permission to run $toolName.")
            argDetails?.let {
                appendLine()
                appendLine("Arguments:")
                appendLine(it)
            }
            appendLine()
            append("Allow execution?")
        }
    }

    private fun requestInlineToolApproval(toolName: String, args: Map<String, String>?): Boolean {
        val future = CompletableFuture<Boolean>()

        ApplicationManager.getApplication().invokeLater {
            val panel = object : JPanel() {
                init {
                    layout = BoxLayout(this, BoxLayout.Y_AXIS)
                    isOpaque = false
                    border = JBUI.Borders.empty(6)
                    alignmentX = Component.LEFT_ALIGNMENT
                }
            }

            val escapedName = StringUtil.escapeXmlEntities(toolName)
            val statusLabel = JBLabel("<html>Agent requests permission to run <b>$escapedName</b>.</html>").apply {
                foreground = UIUtil.getLabelForeground()
                alignmentX = Component.LEFT_ALIGNMENT
            }
            panel.add(statusLabel)

            args?.takeIf { it.isNotEmpty() }?.let { arguments ->
                val argHtml = formatArgumentsHtml(arguments)
                val argLabel = JBLabel("<html>$argHtml</html>").apply {
                    border = JBUI.Borders.emptyTop(4)
                    foreground = UIUtil.getLabelInfoForeground()
                    alignmentX = Component.LEFT_ALIGNMENT
                }
                panel.add(argLabel)
            }

            val buttonPanel = JPanel(BorderLayout()).apply {
                isOpaque = false
            }
            val approveButton = JButton("Allow").apply {
                isOpaque = false
                putClientProperty("JButton.buttonType", "accent")
            }
            val declineButton = JButton("Decline").apply {
                isOpaque = false
                putClientProperty("JButton.backgroundColor", background)
            }
            val buttonsRow = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply {
                isOpaque = false
                add(approveButton)
                add(Box.createHorizontalStrut(6))
                add(declineButton)
            }
            buttonPanel.add(buttonsRow, BorderLayout.SOUTH)
            buttonPanel.alignmentX = Component.LEFT_ALIGNMENT
            panel.add(buttonPanel)

            val bubble = object : JPanel(BorderLayout()) {
                init {
                    isOpaque = false
                    border = JBUI.Borders.empty(6,6,0,0)
                    add(panel, BorderLayout.CENTER)
                    alignmentX = Component.LEFT_ALIGNMENT
                }

                override fun paintComponent(g: Graphics) {
                    val g2 = g.create() as Graphics2D
                    try {
                        g2.color = JBColor(0xEBF0FF, 0x353944)
                        g2.setRenderingHint(
                            RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON
                        )
                        g2.fillRoundRect(0, 0, width, height, 24, 24)
                    } finally {
                        g2.dispose()
                    }
                    super.paintComponent(g)
                }
            }

            messageContainer.add(bubble)
            refreshPanel(messageContainer)
            scrollToBottom(scroll)

            fun complete(approved: Boolean) {
                if (future.isDone) return
                approveButton.isEnabled = false
                declineButton.isEnabled = false
                buttonPanel.isVisible = false
                val statusText = if (approved) {
                    "Approved $toolName."
                } else {
                    "Declined $toolName."
                }
                statusLabel.text = statusText
                statusLabel.foreground = if (approved) JBColor(0x4CAF50, 0x6FBF73) else JBColor(0xF44336, 0xF6685E)
                future.complete(approved)
            }

            approveButton.addActionListener { complete(true) }
            declineButton.addActionListener { complete(false) }
        }

        return try {
            future.get()
        } catch (ie: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        } catch (ex: Exception) {
            logger.warn("Tool approval bubble failed for $toolName", ex)
            false
        }
    }

    private fun formatArgumentsHtml(args: Map<String, String>): String {
        val rows = args.entries.joinToString(separator = "<br/>") { entry ->
            val key = StringUtil.escapeXmlEntities(entry.key)
            val value = StringUtil.escapeXmlEntities(entry.value)
            "<code>$key</code>: $value"
        }
        return "<b>Arguments</b><br/>$rows"
    }

    private fun sendMessageViaKoogAgent(userMessage: String, attachments: List<ChatAttachment>) {
        val accumulatedText = StringBuilder()
        val bubbleRef = AtomicReference<com.github.egorbaranov.cod3.ui.components.ChatBubble?>()

        val permissionHandler = ToolPermissionHandler { name, arguments ->
            shouldExecuteTool(name, arguments)
        }

        val payload = formatMessageWithAttachments(userMessage, attachments)
        val conversation = currentConversation()
        recordUserTurn(userMessage, attachments)

        val job = project.koogAgentService().run(chatIndex, payload, conversation, permissionHandler) { event ->
            when (event) {
                is KoogStreamEvent.ContentDelta -> appendStreamingAssistant(accumulatedText, bubbleRef, event.text)
                is KoogStreamEvent.ToolCallUpdate -> {
                    toolRenderer.render(event.snapshot.toViewModel(), event.final)
                    if (event.final) {
                        val outputText = event.snapshot.output.joinToString("\n").trim()
                        if (outputText.isNotEmpty()) {
                            appendStreamingAssistant(
                                accumulatedText,
                                bubbleRef,
                                "\n$outputText\n"
                            )
                        }
                    }
                }

                is KoogStreamEvent.Completed -> {
                    if (accumulatedText.isEmpty()) {
                        appendStreamingAssistant(accumulatedText, bubbleRef, event.response)
                    }
                    recordAssistantTurn(event.response)
                }

                is KoogStreamEvent.Error -> {
                    val errorMessage = event.throwable.message?.takeIf { !it.contains("StandaloneCoroutine was cancelled") }
                        ?: "Conversation paused"
                    val prefix = if (errorMessage == "Conversation paused") "" else "Error: "
                    appendStreamingAssistant(accumulatedText, bubbleRef, "\n\n$prefix$errorMessage")
                }
            }
        }
        beginStreaming(job)
    }

    private fun beginStreaming(job: Job) {
        activeJob?.cancel()
        activeJob = job
        showStopButton()
        startThinkingIndicator()
        job.invokeOnCompletion {
            SwingUtilities.invokeLater {
                if (activeJob == job) {
                    activeJob = null
                    stopThinkingIndicator()
                    showSendButton()
                }
            }
        }
    }

    private fun showSendButton() {
        SwingUtilities.invokeLater {
            actionButtonLayout.show(actionButtonPanel, "send")
        }
    }

    private fun showStopButton() {
        SwingUtilities.invokeLater {
            actionButtonLayout.show(actionButtonPanel, "stop")
        }
    }

    private fun interruptGeneration() {
        activeJob?.cancel()
    }

    private fun startThinkingIndicator() {
        SwingUtilities.invokeLater {
            if (thinkingBubble != null) return@invokeLater
            thinkingDots = 1
            val bubble = messageContainer.appendAssistantBubble("Thinking.")
            thinkingBubble = bubble
            thinkingTimer?.stop()
            thinkingTimer = Timer(500) {
                thinkingDots = (thinkingDots % 3) + 1
                SwingUtilities.invokeLater {
                    thinkingBubble?.updateText("Thinking" + ".".repeat(thinkingDots), forceReplace = true)
                }
            }.apply { start() }
            scrollToBottom(scroll)
        }
    }

    private fun stopThinkingIndicator() {
        SwingUtilities.invokeLater {
            thinkingTimer?.stop()
            thinkingTimer = null
            thinkingDots = 0
            thinkingBubble?.let {
                messageContainer.remove(it)
                refreshPanel(messageContainer)
            }
            thinkingBubble = null
        }
    }
}

private enum class AgentMode(val displayName: String) {
    CHAT("Chat"),
    AGENT_CONFIRM("Agent"),
    AGENT_AUTO("Agent (full access)")
}
