package com.github.egorbaranov.cod3.toolWindow.chat

import com.github.egorbaranov.cod3.acp.AcpClientService
import com.github.egorbaranov.cod3.acp.AcpStreamEvent
import com.github.egorbaranov.cod3.completions.CompletionsRequestService
import com.github.egorbaranov.cod3.completions.factory.UserMessage
import com.github.egorbaranov.cod3.settings.PluginSettingsState
import com.github.egorbaranov.cod3.toolWindow.Content
import com.github.egorbaranov.cod3.toolWindow.SSEParser
import com.github.egorbaranov.cod3.toolWindow.ToolCall
import com.github.egorbaranov.cod3.ui.Icons
import com.github.egorbaranov.cod3.ui.components.ReferencePopupProvider
import com.github.egorbaranov.cod3.ui.components.ScrollableSpacedPanel
import com.github.egorbaranov.cod3.ui.components.createComboBox
import com.github.egorbaranov.cod3.ui.components.createModelComboBox
import com.github.egorbaranov.cod3.ui.createResizableEditor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.IconLabelButton
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import ee.carlrobert.llm.client.openai.completion.ErrorDetails
import ee.carlrobert.llm.client.openai.completion.OpenAIChatCompletionModel
import ee.carlrobert.llm.client.openai.completion.request.OpenAIChatCompletionMessage
import ee.carlrobert.llm.client.openai.completion.request.OpenAIChatCompletionRequest
import ee.carlrobert.llm.completion.CompletionEventListener
import okhttp3.sse.EventSource
import java.awt.*
import java.awt.geom.Area
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.util.concurrent.atomic.AtomicReference
import javax.swing.*

internal class ChatTabController(
    private val project: Project,
    private val chatIndex: Int,
    private val messages: MutableMap<Int, MutableList<OpenAIChatCompletionMessage>>,
    private val logger: Logger
) {

    private val toolProcessor = ToolCallProcessor()
    private lateinit var messageContainer: JPanel
    private lateinit var scroll: JScrollPane
    private lateinit var planRenderer: PlanRenderer
    private lateinit var toolRenderer: ToolRenderer
    private lateinit var referencePopupProvider: ReferencePopupProvider

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

        val sendButton = IconLabelButton(Icons.Send) { sendMessage(popupProvider.editorTextField.text.trim()) }.apply {
            minimumSize = Dimension(24, 24)
            preferredSize = Dimension(24, 24)
            cursor = Cursor(Cursor.HAND_CURSOR)
        }

        val inputBar = createInputBar(popupProvider, sendButton)

        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
            add(scroll, BorderLayout.CENTER)
            add(inputBar, BorderLayout.SOUTH)
        }
    }

    private fun sendMessage(text: String?) {
        if (text == null || text.isNotEmpty()) {
            ApplicationManager.getApplication().invokeLater {
                WriteCommandAction.runWriteCommandAction(project) {
                    referencePopupProvider.editorTextField.text = ""
                }
            }

            text?.let {
                messageContainer.appendUserBubble(it)
                scrollToBottom(scroll)
            }

            val settings = PluginSettingsState.getInstance()
            if (settings.useAgentClientProtocol) {
                text?.let { sendMessageViaAcp(it) }
                return
            }

            sendMessageViaOpenAi(text ?: "")
        }
    }

    private fun sendMessageViaAcp(userMessage: String) {
        val accumulatedText = StringBuilder()
        val textBubbleRef = AtomicReference<com.github.egorbaranov.cod3.ui.components.ChatBubble?>()

        fun finalizeAssistantBubble() {
            if (accumulatedText.isNotEmpty()) {
                accumulatedText.setLength(0)
            }
            textBubbleRef.set(null)
        }
        val service = project.service<AcpClientService>()

        service.sendPrompt(userMessage) { event ->
            when (event) {
                is AcpStreamEvent.AgentContentText -> appendStreamingAssistant(accumulatedText, textBubbleRef, event.text)
                is AcpStreamEvent.PlanUpdate -> {
                    finalizeAssistantBubble()
                    planRenderer.render(event.entries)
                }
                is AcpStreamEvent.ToolCallUpdate -> {
                    finalizeAssistantBubble()
                    handleAcpToolUpdate(event)
                }
                is AcpStreamEvent.Completed -> appendStreamingAssistant(accumulatedText, textBubbleRef, "")
                is AcpStreamEvent.Error -> {
                    val errorMessage = event.throwable.message ?: event.throwable.javaClass.simpleName
                    appendStreamingAssistant(accumulatedText, textBubbleRef, "\n\nError: $errorMessage")
                }
            }
        }
    }

    private fun appendStreamingAssistant(
        accumulator: StringBuilder,
        bubbleRef: AtomicReference<com.github.egorbaranov.cod3.ui.components.ChatBubble?>,
        chunk: String
    ) {
        if (chunk.isEmpty()) return
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
        toolRenderer.render(event.toolCall, event.final)
        if (!event.final) return

        val toolName = event.toolCall.name ?: return
        val toolCall = ToolCall(toolName, event.toolCall.arguments.takeIf { it.isNotEmpty() })
        val result = try {
            toolProcessor.process(project, toolCall, messageContainer)
        } catch (e: Exception) {
            logger.warn("Tool call '$toolName' failed", e)
            "Error executing tool $toolName: ${e.message}"
        }
        val message = result ?: "Tool $toolName executed."
        messageContainer.appendUserBubble(message)
        scrollToBottom(scroll)
    }

    private fun sendMessageViaOpenAi(text: String) {
        val conversation = messages.getOrPut(chatIndex) { mutableListOf() }
        conversation.add(UserMessage(text))

        val completionRequest = OpenAIChatCompletionRequest
            .Builder(ArrayList(conversation))
            .setModel(OpenAIChatCompletionModel.GPT_4_1.code)
            .setStream(true)
            .build()

        var cachedBubble: com.github.egorbaranov.cod3.ui.components.ChatBubble? = null
        val accumulatedText = StringBuilder()
        val pendingTools: MutableList<ToolCall> = mutableListOf()

        CompletionsRequestService().getChatCompletionAsync(
            completionRequest,
            object : CompletionEventListener<String> {
                override fun onCancelled(messageBuilder: StringBuilder?) {}
                override fun onOpen() {}

                override fun onMessage(message: String?, rawMessage: String?, eventSource: EventSource?) {
                    if (message.isNullOrEmpty()) return
                    accumulatedText.append(message)
                    val combined = accumulatedText.toString()
                    if (cachedBubble != null) {
                        cachedBubble!!.updateText(combined)
                    } else {
                        cachedBubble = messageContainer.appendAssistantBubble(message)
                    }
                    scrollToBottom(scroll)
                }

                override fun onError(error: ErrorDetails?, ex: Throwable?) {
                    logger.warn("Completion error: $error", ex)
                }

                override fun onComplete(messageBuilder: StringBuilder?) {
                    conversation.add(com.github.egorbaranov.cod3.completions.factory.AssistantMessage(accumulatedText.toString()))
                    pendingTools.forEach { tool ->
                        val toolResult = try {
                            toolProcessor.process(project, tool, messageContainer) ?: return@forEach
                        } catch (e: Exception) {
                            "Error executing tool: ${e.message}"
                        }
                        conversation.add(com.github.egorbaranov.cod3.completions.factory.ToolMessage(toolResult))
                        messageContainer.appendUserBubble(toolResult)
                    }
                }

                override fun onEvent(data: String) {
                    try {
                        val (content, toolCalls) = SSEParser.parse(data)
                        when (content) {
                            is Content.PlanContent -> {
                                val textForPlan = buildString {
                                    append(content.plan.taskTitle)
                                    append("\n")
                                    append(content.plan.steps.joinToString())
                                    append("\n")
                                }
                                accumulatedText.append(textForPlan)
                                val combined = accumulatedText.toString()
                                if (cachedBubble != null) {
                                    cachedBubble!!.updateText(combined)
                                } else {
                                    cachedBubble = messageContainer.appendAssistantBubble(combined)
                                }
                            }

                            is Content.TextContent -> {
                                accumulatedText.append(content.text)
                                val combined = accumulatedText.toString()
                                if (cachedBubble != null) {
                                    cachedBubble!!.updateText(combined)
                                } else {
                                    cachedBubble = messageContainer.appendAssistantBubble(combined)
                                }
                            }

                            else -> Unit
                        }

                        toolCalls?.let {
                            val toolCall = it.firstOrNull() ?: return@let
                            if (pendingTools.isEmpty()) {
                                pendingTools.add(
                                    ToolCall(
                                        name = toolCall.name.takeIf { name -> name != "null" },
                                        arguments = toolCall.arguments
                                    )
                                )
                            } else {
                                val existing = pendingTools.last()
                                pendingTools[pendingTools.lastIndex] = ToolCall(
                                    name = existing.name,
                                    arguments = existing.arguments.orEmpty() + toolCall.arguments.orEmpty()
                                )
                            }
                        } ?: run {
                            pendingTools.forEach { tool ->
                                messageContainer.appendUserBubble(
                                    tool.name.orEmpty() + "(" + tool.arguments.orEmpty() + ")"
                                )
                                val result = try {
                                    toolProcessor.process(project, tool, messageContainer) ?: return@run
                                } catch (e: Exception) {
                                    "Error executing tool: ${e.message}"
                                }
                                sendMessage(result)
                            }
                            pendingTools.clear()
                        }
                    } catch (e: Exception) {
                        logger.warn("Failed to parse SSE JSON", e)
                    }
                }
            }
        )
    }

    private fun createInputBar(
        referencePopupProvider: ReferencePopupProvider,
        sendButton: IconLabelButton
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
                cell(createComboBox(listOf("Agent", "Manual")))
                cell(checkBox("Auto-apply").component)
                cell(sendButton).align(AlignX.RIGHT)
            }
        }.andTransparent().withBorder(JBUI.Borders.empty(0, 4))

        add(footer, BorderLayout.SOUTH)
    }
}
