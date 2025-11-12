package com.github.egorbaranov.cod3.toolWindow

import com.github.egorbaranov.cod3.toolWindow.chat.ChatTabController
import com.github.egorbaranov.cod3.ui.Icons
import com.github.egorbaranov.cod3.ui.components.ChatBubble
import com.intellij.icons.AllIcons
import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import ee.carlrobert.llm.client.openai.completion.request.OpenAIChatCompletionMessage
import scaledBy
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel

class Cod3ToolWindowFactory : ToolWindowFactory {

    override fun shouldBeAvailable(project: Project): Boolean = true
    private val list = JBList(listOf("Files & Folders", "Code", "Docs", "Git", "Web", "Recent Changes"))
    private val logger = Logger.getInstance(Cod3ToolWindowFactory::class.java)

    var chatQuantity = 1
    val messages = mutableMapOf<Int, MutableList<OpenAIChatCompletionMessage>>()

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

        val controller = ChatTabController(project, chatQuantity, messages, logger)
        val panel = SimpleToolWindowPanel(true, true).apply {
            setContent(controller.createPanel())
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

    private fun historyBubble(text: String): JComponent {
        val label = JLabel("<html>${text.replace("\n", "<br>")}</html>")

        class RenameAction : AnAction("Rename", "Rename chat", Icons.Edit) {
            override fun actionPerformed(e: AnActionEvent) {
                JOptionPane.showMessageDialog(null, "Rename clicked for: $text")
            }
        }

        class DeleteAction : AnAction("Delete", "Delete chat", Icons.Trash) {
            override fun actionPerformed(e: AnActionEvent) {
                val result = JOptionPane.showConfirmDialog(
                    null,
                    "Delete this item?",
                    "Confirm Delete",
                    JOptionPane.YES_NO_OPTION
                )
                if (result == JOptionPane.YES_OPTION) {
                    // TODO implement deletion wiring
                }
            }
        }

        class MoreActionGroup : ActionGroup("More", true) {

            init {
                templatePresentation.icon = AllIcons.Actions.More
                templatePresentation.putClientProperty(ActionButton.HIDE_DROPDOWN_ICON, java.lang.Boolean.TRUE)
            }

            override fun getChildren(e: AnActionEvent?): Array<AnAction> {
                return arrayOf(
                    object : AnAction("Details") {
                        override fun actionPerformed(e: AnActionEvent) {
                            JOptionPane.showMessageDialog(null, "Details for: $text")
                        }
                    },
                    object : AnAction("Copy") {
                        override fun actionPerformed(e: AnActionEvent) {
                            // Placeholder for copy support
                        }
                    }
                )
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

        return object : JPanel(BorderLayout()) {
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
