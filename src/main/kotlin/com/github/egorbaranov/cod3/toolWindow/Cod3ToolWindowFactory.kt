package com.github.egorbaranov.cod3.toolWindow

import com.github.egorbaranov.cod3.completions.CompletionsRequestService
import com.github.egorbaranov.cod3.completions.factory.OpenAIRequestFactory
import com.github.egorbaranov.cod3.completions.factory.UserMessage
import com.github.egorbaranov.cod3.ui.Icons
import com.github.egorbaranov.cod3.ui.components.*
import com.github.egorbaranov.cod3.ui.createResizableEditor
import com.intellij.icons.AllIcons
import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.Key
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
import ee.carlrobert.llm.client.openai.completion.ErrorDetails
import ee.carlrobert.llm.client.openai.completion.OpenAIChatCompletionModel
import ee.carlrobert.llm.completion.CompletionEventListener
import okhttp3.sse.EventSource
import scaledBy
import java.awt.*
import java.awt.geom.Area
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import javax.swing.*

class Cod3ToolWindowFactory : ToolWindowFactory {

    override fun shouldBeAvailable(project: Project): Boolean = true
    private var lookupPopup: JBPopup? = null
    private val list = JBList(listOf("Files & Folders", "Code", "Docs", "Git", "Web", "Recent Changes"))

    var chatIndex = 1

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
        // Create fresh per-tab components:
        val editorTextField: EditorTextField = createResizableEditor(project, minHeight = 48)
        val contextReferencePanel = ScrollableSpacedPanel(4).apply {
            alignmentY = Component.CENTER_ALIGNMENT
        }

        val referencePopupProvider = ReferencePopupProvider(editorTextField, contextReferencePanel)
        // Build the chat panel, passing these fresh components:
        val panel = SimpleToolWindowPanel(true, true).apply {
            setContent(createChatPanel(project, referencePopupProvider))
        }

        val content: Content = ContentFactory.getInstance()
            .createContent(panel, "Chat ${chatIndex++}", /* isLockable= */ false)
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
            verticalScrollBar.unitIncrement = JBUI.scale(16)
        }

        for (i in 1..chatIndex) {
            messageContainer.add(historyBubble("Chat $i"))
            messageContainer.add(Box.createVerticalStrut(8))
        }

        refresh(messageContainer)
        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
            add(scroll, BorderLayout.CENTER)
        }
    }


    private fun createChatPanel(project: Project, referencePopupProvider: ReferencePopupProvider): JComponent {
        val messageContainer = JBPanel<JBPanel<*>>(VerticalLayout(JBUI.scale(8))).apply {
            border = JBUI.Borders.empty(4)

        }

        val scroll = JBScrollPane(messageContainer).apply {
            verticalScrollBar.unitIncrement = JBUI.scale(16)
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        }

        val sendButton = IconLabelButton(Icons.Send, {
            sendMessage(referencePopupProvider, messageContainer, scroll)
        }).apply {
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
                    cell(createComboBox(listOf("Agent", "Ask", "Manual", "Background")))
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
        referencePopupProvider: ReferencePopupProvider,
        messageContainer: JPanel,
        scroll: JBScrollPane
    ) {
        val text = referencePopupProvider.editorTextField.text.trim()
        if (text.isNotEmpty()) {
            referencePopupProvider.editorTextField.text = ""
            appendUserBubble(messageContainer, text)

            val completionRequest = OpenAIRequestFactory.createBasicCompletionRequest(
                model = OpenAIChatCompletionModel.GPT_4_O.code,
                messages = listOf(
                    UserMessage(text)
                ),
                isStream = true
            )

            var cachedBubble: ChatBubble? = null
            var text = ""
            CompletionsRequestService().getChatCompletionAsync(
                completionRequest,
                object : CompletionEventListener<String> {

                    override fun onCancelled(messageBuilder: StringBuilder?) {
                        println("on cancelled")
                    }

                    override fun onComplete(messageBuilder: StringBuilder?) {
                        println("on completed")
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
                        }
                    }

                    override fun onError(error: ErrorDetails?, ex: Throwable?) {
                        println("on error: $error, $ex")
                    }

                    override fun onEvent(data: String) {
                        println("got data: $data")
//                        SwingUtilities.invokeLater {
//                            scroll.verticalScrollBar.value = scroll.verticalScrollBar.maximum
//                        }
                    }
                }
            )
        }
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
