package com.github.egorbaranov.cod3.toolWindow

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.egorbaranov.cod3.acp.AcpClientService
import com.github.egorbaranov.cod3.acp.AcpStreamEvent
import com.github.egorbaranov.cod3.acp.PlanEntryView
import com.github.egorbaranov.cod3.acp.ToolCallSnapshot
import com.agentclientprotocol.model.PlanEntryPriority
import com.github.egorbaranov.cod3.completions.CompletionsRequestService
import com.github.egorbaranov.cod3.completions.factory.AssistantMessage
import com.github.egorbaranov.cod3.completions.factory.OpenAIRequestFactory
import com.github.egorbaranov.cod3.completions.factory.ToolMessage
import com.github.egorbaranov.cod3.completions.factory.UserMessage
import com.github.egorbaranov.cod3.settings.PluginSettingsState
import com.github.egorbaranov.cod3.ui.Icons
import com.github.egorbaranov.cod3.ui.components.*
import com.github.egorbaranov.cod3.ui.createResizableEditor
import com.intellij.icons.AllIcons
import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.Key
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findDocument
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.ui.EditorTextField
import com.intellij.ui.JBColor
import com.intellij.ui.components.IconLabelButton
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.jetbrains.rd.swing.mouseClicked
import ee.carlrobert.llm.client.openai.completion.ErrorDetails
import ee.carlrobert.llm.client.openai.completion.OpenAIChatCompletionModel
import ee.carlrobert.llm.client.openai.completion.request.OpenAIChatCompletionStandardMessage
import ee.carlrobert.llm.completion.CompletionEventListener
import okhttp3.sse.EventSource
import scaledBy
import java.awt.*
import java.awt.Component.LEFT_ALIGNMENT
import java.awt.geom.Area
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.io.File
import java.nio.file.Paths
import java.util.regex.PatternSyntaxException
import javax.swing.*
import kotlin.math.max
import java.util.concurrent.atomic.AtomicReference

class Cod3ToolWindowFactory : ToolWindowFactory {

    override fun shouldBeAvailable(project: Project): Boolean = true
    private var lookupPopup: JBPopup? = null
    private val list = JBList(listOf("Files & Folders", "Code", "Docs", "Git", "Web", "Recent Changes"))
    private val logger = Logger.getInstance(Cod3ToolWindowFactory::class.java)

    var chatQuantity = 1
    val messages = mutableMapOf<Int, MutableList<OpenAIChatCompletionStandardMessage>>()

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.title = "Cod3"

        // Set up title-bar actions: "Add", "History", "Settings"
        val titleGroup = DefaultActionGroup().apply {
            add(object : AnAction("Add", "Add a new chat tab", AllIcons.General.Add.scaledBy(0.8)) {
                override fun actionPerformed(e: AnActionEvent) {
                    addChatTab(project, toolWindow)
                }
            })

            add(object : AnAction("History", "Show history", Icons.ChatList.scaledBy(1.15)) {
                override fun actionPerformed(e: AnActionEvent) {
                    addHistoryTab(project, toolWindow)
                }
            })

            add(object : AnAction("Settings", "Open settings", Icons.Settings.scaledBy(1.15)) {
                override fun actionPerformed(e: AnActionEvent) {
                    ShowSettingsUtilImpl.showSettingsDialog(project, "cod3.settings", "")
                }
            })
        }
        (toolWindow as? ToolWindowEx)
            ?.setTitleActions(*titleGroup.getChildren(null))

        // Add an initial tab:
        addChatTab(project, toolWindow)
    }

    private fun addChatTab(project: Project, toolWindow: ToolWindow) {
        chatQuantity++

        val panel = SimpleToolWindowPanel(true, true).apply {
            setContent(createChatPanel(project, chatQuantity))
        }

        val content: Content = ContentFactory.getInstance()
            .createContent(panel, "Chat $chatQuantity", /* isLockable= */ false)
            .apply {
                isCloseable = true
                setShouldDisposeContent(true)
            }

        toolWindow.contentManager.addContent(content)
        toolWindow.contentManager.setSelectedContent(content)
    }

    private fun addHistoryTab(project: Project, toolWindow: ToolWindow) {
        val cm = toolWindow.contentManager
        val existing = cm.contents.firstOrNull { it.getUserData(HISTORY_TAB_KEY) == true }
        if (existing != null) {
            cm.setSelectedContent(existing)
            return
        }

        val panel = SimpleToolWindowPanel(true, true).apply {
            setContent(createHistoryPanel(project))
        }
        val content: Content = ContentFactory.getInstance()
            .createContent(panel, "History", /* isLockable= */ false)
            .apply {
                isCloseable = true
                setShouldDisposeContent(true)
            }
        content.putUserData(HISTORY_TAB_KEY, true)
        toolWindow.contentManager.addContent(content)
        toolWindow.contentManager.setSelectedContent(content)
    }

    private fun createHistoryPanel(project: Project): JPanel {
        val messageContainer = JBPanel<JBPanel<*>>().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(4)
        }

        val scroll = JBScrollPane(messageContainer).apply {
//            verticalScrollBar.unitIncrement = JBUI.scale(16)
        }

        for (i in 1..chatQuantity) {
            messageContainer.add(historyBubble("Chat $i"))
            messageContainer.add(Box.createVerticalStrut(8))
        }

        refresh(messageContainer)
        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
            add(scroll, BorderLayout.CENTER)
        }
    }

    private fun sendMessageViaAcp(
        project: Project,
        _chatIndex: Int,
        userMessage: String,
        messageContainer: JPanel,
        scroll: JBScrollPane
    ) {
        val acpService = project.service<AcpClientService>()
        val accumulatedText = StringBuilder()
        val textBubbleRef = AtomicReference<ChatBubble?>()
        val planCardRef = AtomicReference<PlanCard?>()
        val toolCards = mutableMapOf<String, ToolCard>()

        fun appendAssistantContent(chunk: String) =
            appendStreamingAssistantNote(messageContainer, scroll, textBubbleRef, accumulatedText, chunk)

        acpService.sendPrompt(userMessage) { event ->
            when (event) {
                is AcpStreamEvent.AgentContentText -> appendAssistantContent(event.text)
                is AcpStreamEvent.PlanUpdate -> updatePlanBubble(
                    messageContainer = messageContainer,
                    scroll = scroll,
                    planCardRef = planCardRef,
                    entries = event.entries
                )
                is AcpStreamEvent.ToolCallUpdate -> handleAcpToolUpdate(
                    project = project,
                    messageContainer = messageContainer,
                    scroll = scroll,
                    toolCards = toolCards,
                    snapshot = event.toolCall,
                    isFinal = event.final
                )

                is AcpStreamEvent.Completed -> {
                    appendAssistantContent("")
                }

                is AcpStreamEvent.Error -> {
                    val errorMessage = event.throwable.message ?: event.throwable.javaClass.simpleName
                    appendAssistantContent("\n\nError: $errorMessage")
                }
            }
        }
    }

    private fun handleAcpToolUpdate(
        project: Project,
        messageContainer: JPanel,
        scroll: JBScrollPane,
        toolCards: MutableMap<String, ToolCard>,
        snapshot: ToolCallSnapshot,
        isFinal: Boolean
    ) {
        SwingUtilities.invokeLater {
            val card = toolCards[snapshot.id] ?: run {
                val created = createToolCard()
                val bubble = messageContainer.addCustomBubble(created.panel)
                created.bubble = bubble
                toolCards[snapshot.id] = created
                created
            }
            updateToolCard(card, snapshot)
            scrollToBottom(scroll)
            if (isFinal) {
                toolCards.remove(snapshot.id)
            }
        }

        if (!isFinal) return

        val toolName = snapshot.name ?: return
        val toolCall = ToolCall(
            name = toolName,
            arguments = snapshot.arguments.takeIf { it.isNotEmpty() }
        )

        val result = try {
            processToolCall(project, toolCall, messageContainer)
        } catch (e: Exception) {
            logger.warn("Tool call '${toolCall.name}' failed", e)
            "Error executing tool ${toolCall.name}: ${e.message}"
        }

        val message = result ?: "Tool ${toolCall.name} executed."

        SwingUtilities.invokeLater {
            appendUserBubble(messageContainer, message)
            scrollToBottom(scroll)
        }
    }

    private fun updatePlanBubble(
        messageContainer: JPanel,
        scroll: JBScrollPane,
        planCardRef: AtomicReference<PlanCard?>,
        entries: List<PlanEntryView>
    ) {
        SwingUtilities.invokeLater {
            val existing = planCardRef.get()
            if (entries.isEmpty()) {
                existing?.let { card ->
                    card.panel.isVisible = false
                    card.bubble?.isVisible = false
                }
                refresh(messageContainer)
                return@invokeLater
            }

            val card = existing ?: run {
                val created = createPlanCard()
                val bubble = messageContainer.addCustomBubble(created.panel)
                created.bubble = bubble
                planCardRef.set(created)
                created
            }

            updatePlanCard(card, entries)
            card.panel.isVisible = true
            card.bubble?.isVisible = true
            refresh(messageContainer)
            scrollToBottom(scroll)
        }
    }

    private fun createPlanCard(): PlanCard {
        val headerLabel = JLabel("Plan").apply {
            font = font.deriveFont(Font.BOLD.toFloat())
        }
        val stepsPanel = JBPanel<JBPanel<*>>(VerticalLayout(JBUI.scale(6))).apply {
            isOpaque = false
        }
        val panel = JBPanel<JBPanel<*>>(VerticalLayout(JBUI.scale(8))).apply {
            isOpaque = false
            border = JBUI.Borders.empty(12)
            add(headerLabel)
            add(stepsPanel)
        }
        return PlanCard(panel, stepsPanel)
    }

    private fun updatePlanCard(card: PlanCard, entries: List<PlanEntryView>) {
        while (card.stepRows.size > entries.size) {
            val row = card.stepRows.removeAt(card.stepRows.size - 1)
            card.stepsPanel.remove(row.container)
        }
        while (card.stepRows.size < entries.size) {
            val row = createPlanStepRow()
            card.stepRows.add(row)
            card.stepsPanel.add(row.container)
        }

        entries.forEachIndexed { index, entry ->
            val row = card.stepRows[index]
            row.contentLabel.text = "${entry.order}. ${entry.content}"
            val status = entry.status.name.lowercase().replace('_', ' ')
            val priority = entry.priority.name.lowercase().replace('_', ' ')
            row.metaLabel.text = if (entry.priority == PlanEntryPriority.MEDIUM) {
                "Status: $status"
            } else {
                "Status: $status · Priority: $priority"
            }
        }

        card.panel.revalidate()
        card.panel.repaint()
    }

    private fun createPlanStepRow(): PlanStepRow {
        val contentLabel = JLabel()
        val metaLabel = JLabel().apply {
            foreground = JBColor.GRAY
            font = font.deriveFont(Font.ITALIC, font.size2D - 1f)
        }
        val container = JBPanel<JBPanel<*>>(VerticalLayout(JBUI.scale(2))).apply {
            isOpaque = false
            add(contentLabel)
            add(metaLabel)
        }
        return PlanStepRow(container, contentLabel, metaLabel)
    }

    private fun createToolCard(): ToolCard {
        val titleLabel = JLabel().apply {
            font = font.deriveFont(Font.BOLD.toFloat())
        }
        val statusLabel = JLabel().apply {
            foreground = JBColor.GRAY
        }
        val kindLabel = JLabel().apply {
            foreground = JBColor.GRAY
        }

        val inputSection = createSectionPanel("Input")
        val outputSection = createSectionPanel("Output")

        val panel = JBPanel<JBPanel<*>>(VerticalLayout(JBUI.scale(6))).apply {
            isOpaque = false
            border = JBUI.Borders.empty(12)
            add(titleLabel)
            add(statusLabel)
            add(kindLabel)
            add(inputSection.container)
            add(outputSection.container)
        }

        return ToolCard(panel, titleLabel, statusLabel, kindLabel, inputSection, outputSection)
    }

    private fun updateToolCard(card: ToolCard, snapshot: ToolCallSnapshot) {
        card.titleLabel.text = snapshot.title?.takeIf { it.isNotBlank() } ?: snapshot.name ?: snapshot.id

        val statusText = snapshot.status?.name?.lowercase()?.replace('_', ' ') ?: "unknown"
        card.statusLabel.text = "Status: $statusText"
        card.statusLabel.isVisible = true

        snapshot.kind?.let {
            card.kindLabel.text = "Kind: ${it.name.lowercase().replace('_', ' ')}"
            card.kindLabel.isVisible = true
        } ?: run {
            card.kindLabel.isVisible = false
        }

        val inputs = snapshot.arguments.map { (key, value) -> "$key: $value" }
        populateSection(card.inputSection, inputs)
        populateSection(card.outputSection, snapshot.content.filter { it.isNotBlank() })

        card.panel.revalidate()
        card.panel.repaint()
        card.bubble?.isVisible = true
    }

    private fun createSectionPanel(title: String): SectionPanel {
        val titleLabel = JLabel(title).apply {
            font = font.deriveFont(Font.BOLD.toFloat())
        }
        val itemsPanel = JBPanel<JBPanel<*>>(VerticalLayout(JBUI.scale(2))).apply {
            isOpaque = false
        }
        val container = JBPanel<JBPanel<*>>(VerticalLayout(JBUI.scale(2))).apply {
            isOpaque = false
            add(titleLabel)
            add(itemsPanel)
            isVisible = false
        }
        return SectionPanel(container, itemsPanel)
    }

    private fun populateSection(section: SectionPanel, items: List<String>) {
        section.itemsPanel.removeAll()
        if (items.isEmpty()) {
            section.container.isVisible = false
        } else {
            items.forEach {
                section.itemsPanel.add(JLabel("\u2022 $it"))
            }
            section.container.isVisible = true
        }
        section.itemsPanel.revalidate()
        section.itemsPanel.repaint()
    }

    private fun appendStreamingAssistantNote(
        messageContainer: JPanel,
        scroll: JBScrollPane,
        bubbleRef: AtomicReference<ChatBubble?>,
        accumulatedText: StringBuilder,
        note: String
    ) {
        if (note.isEmpty()) return
        accumulatedText.append(note)
        val updatedText = accumulatedText.toString()
        SwingUtilities.invokeLater {
            val existing = bubbleRef.get()
            if (existing != null) {
                existing.updateText(updatedText, forceReplace = true)
            } else {
                bubbleRef.set(appendAssistantBubble(messageContainer, updatedText))
            }
            scrollToBottom(scroll)
        }
    }

    private class PlanCard(val panel: JPanel, val stepsPanel: JPanel) {
        val stepRows: MutableList<PlanStepRow> = mutableListOf()
        var bubble: JPanel? = null
    }

    private class PlanStepRow(
        val container: JPanel,
        val contentLabel: JLabel,
        val metaLabel: JLabel
    )

    private class ToolCard(
        val panel: JPanel,
        val titleLabel: JLabel,
        val statusLabel: JLabel,
        val kindLabel: JLabel,
        val inputSection: SectionPanel,
        val outputSection: SectionPanel
    ) {
        var bubble: JPanel? = null
    }

    private class SectionPanel(
        val container: JPanel,
        val itemsPanel: JPanel
    )

    private fun scrollToBottom(scroll: JBScrollPane) {
        with(scroll.verticalScrollBar) {
            if (value >= maximum - 20) {
                value = maximum
            }
        }
    }


    private fun createChatPanel(
        project: Project,
        chatIndex: Int
    ): JComponent {
        val messageContainer = JBPanel<JBPanel<*>>(VerticalLayout(JBUI.scale(8))).apply {
            border = JBUI.Borders.empty(4)

        }

        val scroll = JBScrollPane(messageContainer).apply {
//            verticalScrollBar.unitIncrement = JBUI.scale(16)
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        }

        lateinit var sendButton: IconLabelButton
        lateinit var referencePopupProvider: ReferencePopupProvider

        // Create fresh per-tab components:
        val editorTextField: EditorTextField = createResizableEditor(project, minHeight = 48) {
            println("send message: ")
            sendMessage(
                project,
                chatIndex,
                referencePopupProvider.editorTextField.text.trim(),
                referencePopupProvider,
                messageContainer,
                scroll
            )
        }

        val contextReferencePanel = ScrollableSpacedPanel(4).apply {
            alignmentY = Component.CENTER_ALIGNMENT
        }

        referencePopupProvider = ReferencePopupProvider(editorTextField, contextReferencePanel)
        // Build the chat panel, passing these fresh components:

        sendButton = IconLabelButton(Icons.Send) {
            sendMessage(
                project,
                chatIndex,
                referencePopupProvider.editorTextField.text.trim(),
                referencePopupProvider,
                messageContainer,
                scroll
            )
        }.apply {
            minimumSize = Dimension(24, 24)
            preferredSize = Dimension(24, 24)
            cursor = Cursor(Cursor.HAND_CURSOR)
        }

        val inputBar = object : JPanel(BorderLayout()) {
            init {
                isOpaque = false
            }

            override fun paintBorder(g: Graphics) {
                val g2 = g.create() as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = JBUI.CurrentTheme.Focus.defaultButtonColor()
                if (referencePopupProvider.editorTextField.isFocusOwner) {
                    g2.stroke = BasicStroke(1.5F)
                } else {
                    g2.stroke = BasicStroke(0F)
                }
                g2.drawRoundRect(0, 0, width - 1, height - 1, 24, 24)
                g2.dispose()
            }

            override fun paintComponent(g: Graphics) {
                val g2 = g.create() as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

                val area = Area(Rectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat()))
                val roundedRect = RoundRectangle2D.Float(
                    0f,
                    0f,
                    width.toFloat(),
                    height.toFloat(),
                    24f,
                    24f
                )
                area.intersect(Area(roundedRect))

                g2.clip = area
                g2.color = JBColor.gray.darker().darker().darker().darker()
                g2.fill(area)

                super.paintComponent(g2)
                g2.dispose()
            }
        }.apply {
            border = JBUI.Borders.empty(4)

            val addContextButton = IconLabelButton(Icons.Mention, {
                referencePopupProvider.checkPopup(true)
            }).apply {
                minimumSize = Dimension(24, 24)
                preferredSize = Dimension(24, 24)
                cursor = Cursor(Cursor.HAND_CURSOR)
            }

            val scrollPane = JBScrollPane(referencePopupProvider.contextReferencePanel).apply {
                isOpaque = false
                border = JBUI.Borders.empty(2, 0)
                horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
                verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_NEVER
            }

            scrollPane.viewport.isOpaque = false
            scrollPane.viewport.background = UIUtil.TRANSPARENT_COLOR

            val header = panel {
                row {
                    cell(addContextButton).align(AlignX.LEFT).gap(RightGap.SMALL)
                    cell(scrollPane).align(Align.FILL)
                }
            }.andTransparent().withBorder(JBUI.Borders.empty(0, 4))

            add(header, BorderLayout.NORTH)
            add(referencePopupProvider.editorTextField, BorderLayout.CENTER)
            val comboBoxAction = createModelComboBox()

            val footer = panel {
                row {
                    cell(comboBoxAction)
                    cell(createComboBox(listOf("Agent", "Manual")))
                    cell(checkBox("Auto-apply").component)
                    cell(sendButton).align(AlignX.RIGHT)
                }
            }.andTransparent().withBorder(JBUI.Borders.empty(0, 4))

            add(footer, BorderLayout.SOUTH)
        }

        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
            add(scroll, BorderLayout.CENTER)
            add(inputBar, BorderLayout.SOUTH)
        }
    }

    private fun sendMessage(
        project: Project,
        chatIndex: Int,
        text: String?,
        referencePopupProvider: ReferencePopupProvider,
        messageContainer: JPanel,
        scroll: JBScrollPane
    ) {
        if (text == null || text.isNotEmpty()) {
            ApplicationManager.getApplication().invokeLater {
                WriteCommandAction.runWriteCommandAction(project) {
                    referencePopupProvider.editorTextField.text = ""
                }
            }

            if (text != null) {
                appendUserBubble(messageContainer, text)
                with(scroll.verticalScrollBar) {
                    if (value >= maximum - 100) {
                        println("scroll to bottom: value=$value, maximum=$maximum")
                        value = maximum
                    }
                }
            }

            val settingsState = PluginSettingsState.getInstance()
            if (settingsState.useAgentClientProtocol) {
                if (text != null) {
                    sendMessageViaAcp(
                        project,
                        chatIndex,
                        text,
                        messageContainer,
                        scroll
                    )
                }
                return
            }

            if (messages[chatIndex] == null) {
                messages.put(chatIndex, mutableListOf())
            }

            if (text != null) {
                messages[chatIndex]?.add(UserMessage(text))
            }

            println("messages are: $messages")

            println("send request with messages: ${messages[chatIndex]}")

            val completionRequest = OpenAIRequestFactory.createBasicCompletionRequest(
                model = OpenAIChatCompletionModel.GPT_4_1.code,
                messages = messages[chatIndex].orEmpty(),
                isStream = true,
                overridenPath = ""
            )

            var cachedBubble: ChatBubble? = null
            var text = ""
            val tools: MutableList<ToolCall> = mutableListOf()

            println("sending completion request: ${ObjectMapper().writeValueAsString(completionRequest)}")
            CompletionsRequestService().getChatCompletionAsync(
                completionRequest,
                object : CompletionEventListener<String> {

                    override fun onCancelled(messageBuilder: StringBuilder?) {
                        println("on cancelled")
                    }

                    override fun onComplete(messageBuilder: StringBuilder?) {
                        println("on completed")
                        messages[chatIndex]?.add(AssistantMessage(text))

                        for (tool in tools) {
                            cachedBubble = null
                            text = ""

                            val messageText = try {
                                processToolCall(project, tool, messageContainer) ?: continue
                            } catch (e: Exception) {
                                "Error executing tool: ${e.message}"
                            }

                            messages[chatIndex]?.add(ToolMessage(messageText))
                            appendUserBubble(
                                messageContainer,
                                messageText
                            )
                        }

                        if (tools.isNotEmpty()) {
                            sendMessage(
                                project,
                                chatIndex,
                                null,
                                referencePopupProvider,
                                messageContainer,
                                scroll
                            )
                        }
                    }

                    override fun onOpen() {
                        println("On open")
                    }

                    override fun onMessage(message: String?, rawMessage: String?, eventSource: EventSource?) {
                        println("on message: $message")
                        if (message != null && message.isNotEmpty()) {
                            text += message
                            if (cachedBubble != null) {
                                cachedBubble?.updateText(text)
                            } else {
                                cachedBubble = appendAssistantBubble(messageContainer, message)
                            }

                            with(scroll.verticalScrollBar) {
                                if (value >= maximum - 20) {
                                    value = maximum
                                }
                            }
                        }
                    }

                    override fun onError(error: ErrorDetails?, ex: Throwable?) {
                        println("on error: $error, $ex")
                    }

                    override fun onEvent(data: String) {
                        println("got event: $data")
                        try {
                            val (content, toolCalls) = SSEParser.parse(data)

                            when (content) {
                                is Content.PlanContent -> {
                                    println("💡 Received plan: ${content.plan.taskTitle}")
                                    println("Steps: ${content.plan.steps.joinToString()}")

                                    text += content.plan.taskTitle + "\n" + content.plan.steps.joinToString() + "\n"
                                    if (cachedBubble != null) {
                                        cachedBubble?.updateText(text)
                                    } else {
                                        cachedBubble = appendAssistantBubble(messageContainer, text)
                                    }
                                }

                                is Content.TextContent -> {
                                    text += content.text
                                    if (cachedBubble != null) {
                                        cachedBubble?.updateText(text)
                                    } else {
                                        cachedBubble = appendAssistantBubble(messageContainer, text)
                                    }

                                    println("✏️  Assistant says: ${content.text}")
                                }

                                else -> {
                                    println("⚠️  No content in this event")
                                }
                            }

                            toolCalls?.let {
                                println("🔧 Tool calls:")
                                val toolCall = it.firstOrNull() ?: return@let

                                if (tools.isEmpty()) {
                                    tools.add(
                                        ToolCall(
                                            name = toolCall.name.takeIf { it != "null" },
                                            arguments = toolCall.arguments
                                        )
                                    )
                                } else {
                                    tools[tools.size - 1] = tools[tools.size - 1].let {
                                        ToolCall(
                                            name = it.name.orEmpty(),
                                            arguments = it.arguments.orEmpty() + toolCall.arguments.orEmpty()
                                        )
                                    }
                                }

                                it.forEach { tc ->
                                    println(" - ${tc.name}(${tc.arguments})")
                                }
                            } ?: run {
                                for (tool in tools) {
                                    text += "\n\nTool call: " + tool.name.orEmpty() + "(" + tool.arguments.orEmpty() + ")\n\n"
                                    appendUserBubble(
                                        messageContainer,
                                        tool.name.orEmpty() + "(" + tool.arguments.orEmpty() + ")"
                                    )
                                    cachedBubble = null
                                    text = ""

                                    val toolResult = try {
                                        processToolCall(project, tool, messageContainer) ?: return
                                    } catch (e: Exception) {
                                        "Error executing tool: ${e.message}"
                                    }

                                    sendMessage(
                                        project,
                                        chatIndex,
                                        toolResult,
                                        referencePopupProvider,
                                        messageContainer,
                                        scroll
                                    )
                                }

                                tools.clear()
                            }

                        } catch (e: Exception) {
                            println("❌ Failed to parse SSE JSON for data $data: ${e.stackTraceToString()}")
                        }
                    }
                }
            )
        }
    }

    private fun processToolCall(project: Project, toolCall: ToolCall, container: JPanel): String? {
        val name = toolCall.name ?: return null
        val args = toolCall.arguments ?: emptyMap()
        println("processing a tool call: $toolCall")

        return when (name) {
            "run_command" -> {
                val command = args["command"] ?: error("missing command")
                println("✅ Run $command")
                "Successfully run command=$command"
            }

            "write_file" -> {
                val pathString = args["path"] ?: error("missing path")
                val content = args["content"] ?: error("missing content")

                // Normalize to system‐independent path
                val path = Paths.get(project.basePath!!)
                    .resolve(pathString.trimStart('/', '\\'))
                    .toAbsolutePath()
                val parentPath = path.parent.toAbsolutePath().toString()
                val fileName = path.fileName.toString()

                // Ensure the parent directory exists on disk
                println("parent path: $parentPath, path: $path")
                File(parentPath).apply { if (!exists()) mkdirs() }

                lateinit var createdVFile: VirtualFile

                WriteCommandAction.runWriteCommandAction(project) {
                    // Locate (or refresh) the parent directory in the VFS
                    val parentVDir = LocalFileSystem.getInstance()
                        .refreshAndFindFileByPath(parentPath)
                        ?: error("Could not find or create VFS directory: $parentPath")

                    // find or create the file
                    createdVFile = parentVDir.findChild(fileName)
                        ?: parentVDir.createChildData(this, fileName)
                    // write the text (this also handles line‐endings, encoding, etc.)
                    VfsUtil.saveText(createdVFile, content)
                }

                ApplicationManager.getApplication().invokeLater {
                    FileEditorManager.getInstance(project)
                        .openFile(createdVFile, true)
                }

                SwingUtilities.invokeLater {
                    container.addCustomBubble(JPanel(VerticalLayout(8)).also {
                        container.add(
                            createEditorComponent(
                                createdVFile.findDocument()!!,
                                createdVFile.extension,
                                path = pathString
                            ) {}
                        )
                        refresh(container)
                    })
                }

                println("✅ Wrote $pathString")
                "Successfully wrote file in path=$pathString"
            }


            "edit_file" -> {
                val pathString = args["path"] ?: error("missing path")
                val newContent = args["edits"] ?: error("missing edits")

                val filePath = Paths.get(project.basePath!!)
                    .resolve(pathString.trimStart('/', '\\'))
                    .toAbsolutePath()
                val file = File(filePath.toString())
                if (!file.exists()) error("file not found: $pathString")

                // Locate the VFS file before editing
                val vFile = LocalFileSystem.getInstance()
                    .refreshAndFindFileByPath(filePath.toString())
                    ?: error("VFS file not found: $filePath")

                // Save original to temp file to use in diff
                val tempOriginal = File.createTempFile("orig", null)
                tempOriginal.writeBytes(vFile.contentsToByteArray())
                val origVFile = LocalFileSystem.getInstance()
                    .refreshAndFindFileByPath(tempOriginal.absolutePath)
                    ?: error("temp VFS file not found")

                // Overwrite the file content in a write action
                WriteCommandAction.runWriteCommandAction(project) {
                    VfsUtil.saveText(vFile, newContent)
                }

                // Refresh the VFS to pick up the changes
                LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath.toString())

                // Embed diff between orig and edited
                SwingUtilities.invokeLater {
                    container.addCustomBubble(JPanel(VerticalLayout(8)).also {
                        container.add(
                            createInlineDiffComponent(
                                project,
                                origVFile,
                                vFile
                            )
                        )
                        refresh(container)
                    })
                }

                println("✅ Edited file at path=$pathString")
                "Successfully edited $pathString"
            }


            "find" -> {
                val rawPattern = args["pattern"]
                    ?: error("missing pattern")

                // Helper: convert shell-style glob to regex
                fun globToRegex(glob: String): String {
                    val sb = StringBuilder("^")
                    var i = 0
                    while (i < glob.length) {
                        when (val c = glob[i]) {
                            '*' -> sb.append(".*")
                            '?' -> sb.append(".")
                            '[' -> {
                                // copy character class up to closing ]
                                val j = glob.indexOf(']', i + 1).takeIf { it > i } ?: i
                                sb.append(glob.substring(i, j + 1))
                                i = j
                            }

                            '\\' -> {
                                // escape next char literally
                                if (i + 1 < glob.length) {
                                    sb.append("\\\\").append(glob[i + 1])
                                    i++
                                } else sb.append("\\\\")
                            }

                            else -> {
                                // escape regex metachars
                                if ("\\.[]{}()+-^$|".contains(c)) sb.append("\\")
                                sb.append(c)
                            }
                        }
                        i++
                    }
                    sb.append("$")
                    return sb.toString()
                }

                // Decide whether we're in raw‑regex mode or glob mode
                val (patternBody, isRegex) = if (rawPattern.startsWith("r:")) {
                    rawPattern.drop(2) to true
                } else {
                    rawPattern to false
                }

                // Build final regex string
                val regexString = if (isRegex) {
                    patternBody
                } else {
                    globToRegex(patternBody)
                }

                // Compile
                val regex = try {
                    regexString.toRegex(RegexOption.IGNORE_CASE)
                } catch (ex: PatternSyntaxException) {
                    error("Invalid pattern syntax: ${ex.message}")
                }

                // Walk *all* files, relativize paths, and filter by our regex
                val basePath = project.basePath
                    ?: error("missing Project basePath")
                val baseDir = Paths.get(basePath)
                val matches = File(basePath).walk()
                    .filter { it.isFile }
                    .map { file ->
                        baseDir.relativize(file.toPath())
                            .toString()
                            .replace("\\", "/")
                    }
                    .filter { relPath ->
                        regex.containsMatchIn(relPath)
                    }
                    .toList()

                // Output
                println("🔍 find results for ${if (isRegex) "regex" else "glob"} /$rawPattern/ in '$basePath':")
                matches.forEach { println(" • $it") }

                container.addCustomBubble(JPanel(VerticalLayout(8)).also {
                    matches.forEach { path ->
                        container.add(createBubble(path, JBColor.LIGHT_GRAY))
                    }
                    refresh(container)
                })

                "Successfully found ${matches.size} result(s) for ${if (isRegex) "regex" else "glob"} /$rawPattern/:\n" +
                        matches.joinToString("\n") { " - /$it" }
            }

            "codebase_search" -> {
                val query = args["query"] ?: error("missing query")
                val topK = args["top_k"]?.toIntOrNull() ?: 5
                // Stub: in real life you'd call your semantic‐search index here
                println("💡 (stub) searching codebase for '$query' (top $topK)")
                "Successfully searched codebase for '$query' (top $topK) and found results: "
            }

            "list_directory" -> {
                val dirArg = args["directory"].orEmpty()

                val projectBasePath = project.basePath ?: error("Project base path not available")
                val dir = File(projectBasePath, dirArg).canonicalFile

                if (!dir.isDirectory) error("$dir is not a directory")

                val children = dir.listFiles() ?: emptyArray()
                println("📁 listing '${dir.relativeTo(File(projectBasePath))}':")
                children.forEach {
                    val type = if (it.isDirectory) "dir" else "file"
                    println(" - [$type] ${it.name} (${it.length()} bytes)")
                }

                "Successfully listed directory '${dir.relativeTo(File(projectBasePath))}' with contents:\n${
                    children.map {
                        it.relativeTo(
                            File(
                                projectBasePath
                            )
                        )
                    }.joinToString("\n")
                }"
            }


            "grep_search" -> {
                val pattern = args["pattern"] ?: error("missing pattern")
                val path = args["path"] ?: error("missing path")
                val recursive = args["recursive"]?.toBoolean() ?: true
                val regex = pattern.toRegex()
                val direction = if (recursive) FileWalkDirection.TOP_DOWN else FileWalkDirection.BOTTOM_UP

                File(path).walk(direction).forEach { file ->
                    if (file.isFile) {
                        file.readLines().forEachIndexed { idx, line ->
                            if (regex.containsMatchIn(line)) {
                                println("🧪 ${file.path}:${idx + 1}: $line")
                            }
                        }
                    }
                }

                "Successfully provided grep search results for '$pattern' in '$path' (recursive=$recursive):"
            }

            "view_code_item" -> {
                val item = args["item_name"] ?: error("missing item_name")
                val path = args["path"] ?: error("missing path")
                val v = File(path).useLines { lines ->
                    val snippet = lines
                        .dropWhile { !it.contains("fun $item") && !it.contains("class $item") }
                        .takeWhile { !it.startsWith("}") }
                        .joinToString("\n")
                    println("$item in $path:\n$snippet")
                    return@useLines snippet
                }

                "Successfully viewed code item $item in $path:\n$v"
            }

            "view_file" -> {
                val path = args["path"] ?: error("missing path")
                val filePath = Paths.get(project.basePath!!)
                    .resolve(path.trimStart('/', '\\'))
                    .toAbsolutePath()
                val file = File(filePath.toString())
                if (!file.exists()) error("file not found: $path")
                println("📄 contents of $path:\n${file.readText()}")
                "Successfully viewed contents of $path:\n${file.readText()}"
            }

            else -> {
                println("⚠️ Unknown tool: $name")
                null
            }
        }
    }

    private fun JPanel.addCustomBubble(component: JPanel): JPanel {
        val container = this
        val bubble = object : JPanel(BorderLayout()) {
            init {
                background = JBColor.LIGHT_GRAY
                this.add(component)
            }

            override fun paintComponent(g: Graphics) {
                val g2 = g.create() as Graphics2D
                try {
                    g2.color = background
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    g2.fillRoundRect(0, 0, width, height, JBUI.scale(16), JBUI.scale(16))
                } finally {
                    g2.dispose()
                }
                super.paintComponent(g)
            }
        }
        container.add(bubble)
        refresh(container)
        return bubble
    }

    private fun appendUserBubble(container: JPanel, text: String) {
        val bubble = createBubble(text, JBColor.LIGHT_GRAY)
        container.add(bubble)
        refresh(container)
    }

    private fun appendAssistantBubble(container: JPanel, text: String): ChatBubble {
        val bubble = createBubble(text, UIUtil.getPanelBackground(), assistant = true)
        container.add(bubble)
        refresh(container)
        return bubble as ChatBubble
    }

    private fun historyBubble(text: String): JComponent {
        val label = JLabel("<html>${text.replace("\n", "<br>")}</html>")

        class RenameAction : AnAction("Rename", "Rename chat", Icons.Edit) {
            override fun actionPerformed(e: AnActionEvent) {
                // TODO: implement rename logic
                JOptionPane.showMessageDialog(null, "Rename clicked for: $text")
            }
        }

        class DeleteAction : AnAction("Delete", "Delete chat", Icons.Trash) {
            override fun actionPerformed(e: AnActionEvent) {
                // TODO: implement delete logic
                val result = JOptionPane.showConfirmDialog(
                    null,
                    "Delete this item?",
                    "Confirm Delete",
                    JOptionPane.YES_NO_OPTION
                )
                if (result == JOptionPane.YES_OPTION) {
                    // perform deletion
                }
            }
        }

        class MoreActionGroup : ActionGroup("More", true) {

            init {
                templatePresentation.icon = AllIcons.Actions.More
                templatePresentation.putClientProperty(ActionButton.HIDE_DROPDOWN_ICON, java.lang.Boolean.TRUE)
            }

            override fun getChildren(e: AnActionEvent?): Array<AnAction> {
                // Populate additional actions here
                return arrayOf(object : AnAction("Details") {
                    override fun actionPerformed(e: AnActionEvent) {
                        // TODO: show details
                        JOptionPane.showMessageDialog(null, "Details for: $text")
                    }
                }, object : AnAction("Copy") {
                    override fun actionPerformed(e: AnActionEvent) {
                        // TODO: copy action
//                        Toolkit.getDefaultToolkit().systemClipboard.setContents(
//                            StringSelection(text), null)
                    }
                })
            }
        }

        val actionGroup = DefaultActionGroup().apply {
            add(RenameAction())
            add(DeleteAction())
            add(MoreActionGroup())
        }

        val actionToolbar = ActionManager.getInstance()
            .createActionToolbar("HistoryBubbleToolbar", actionGroup, true)
            .apply {
                targetComponent = null
                component.isOpaque = false
                isReservePlaceAutoPopupIcon = false
            }

        val contentPanel = object : JPanel(BorderLayout()) {
            init {
                add(label, BorderLayout.CENTER)
                add(actionToolbar.component.apply {
                    border = JBUI.Borders.empty()
                }, BorderLayout.EAST)

                isOpaque = true
                background = JBColor.LIGHT_GRAY
                border = JBUI.Borders.empty(12, 20, 12, 8)

                cursor = Cursor(Cursor.HAND_CURSOR)
            }

            override fun paintComponent(g: Graphics) {
                val g2 = g.create() as Graphics2D
                try {
                    g2.setRenderingHint(
                        RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON
                    )
                    g2.color = JBColor.LIGHT_GRAY
                    g2.fillRoundRect(0, 0, width, height, 24, 24)
                } finally {
                    g2.dispose()
                }
            }

            override fun getMaximumSize(): Dimension {
                return Dimension(Int.MAX_VALUE, preferredSize.height)
            }
        }
        return contentPanel
    }

    private fun createBubble(text: String, bg: Color, assistant: Boolean = false): JComponent {
        return ChatBubble(text, bg, assistant)
    }


    private fun refresh(container: JPanel) {
        container.revalidate()
        container.repaint()
    }

    private enum class Alignment { LEFT, RIGHT }

    companion object {
        private val HISTORY_TAB_KEY = Key.create<Boolean>("com.example.Cod3.HISTORY_TAB")
    }
}
