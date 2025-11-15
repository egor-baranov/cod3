package com.github.egorbaranov.cod3.toolWindow

import com.github.egorbaranov.cod3.state.ChatState
import com.github.egorbaranov.cod3.toolWindow.chat.ChatMessage
import com.github.egorbaranov.cod3.toolWindow.chat.ChatAttachment
import com.github.egorbaranov.cod3.toolWindow.chat.ChatRole
import com.github.egorbaranov.cod3.toolWindow.chat.ChatTabController
import com.github.egorbaranov.cod3.ui.Icons
import com.github.egorbaranov.cod3.ui.components.ChatBubble
import com.intellij.icons.AllIcons
import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
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
import scaledBy
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class Cod3ToolWindowFactory : ToolWindowFactory {

    override fun shouldBeAvailable(project: Project): Boolean = true
    private val list = JBList(listOf("Files & Folders", "Code", "Docs", "Git", "Web", "Recent Changes"))
    private val logger = Logger.getInstance(Cod3ToolWindowFactory::class.java)

    private val chatState = ChatState.getInstance()
    var chatQuantity = 0
    val messages = mutableMapOf<Int, MutableList<ChatMessage>>()
    private val chatTitles = mutableMapOf<Int, String>()
    private val chatTabs = mutableMapOf<Int, ChatTabMeta>()
    private lateinit var toolWindowRef: ToolWindow
    private lateinit var projectRef: Project

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindowRef = toolWindow
        projectRef = project
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

        restoreChats(project, toolWindow)
    }

    private fun restoreChats(project: Project, toolWindow: ToolWindow) {
        val saved = chatState.chats
        if (saved.isEmpty()) {
            addChatTab(project, toolWindow)
            return
        }

        chatQuantity = saved.maxOfOrNull { it.id } ?: 0
        saved.forEach { chat ->
            val id = if (chat.id > 0) chat.id else ++chatQuantity
            val title = chat.title.ifBlank { "Chat $id" }
            chatTitles[id] = title
            val history = chat.messages.mapNotNull { msg ->
                runCatching {
                    val role = ChatRole.valueOf(msg.role)
                    ChatMessage(
                        role = role,
                        content = msg.text,
                        attachments = msg.attachments.map {
                            ChatAttachment(it.title, it.content, navigationPath = it.navigationPath)
                        }
                    )
                }.getOrNull()
            }.toMutableList()
            messages[id] = history
        }

        val firstId = chatTitles.keys.minOrNull()
        if (firstId != null) {
            createChatTab(firstId, chatTitles[firstId] ?: "Chat $firstId")
        } else {
            addChatTab(project, toolWindow)
        }
    }

    private fun addChatTab(project: Project, toolWindow: ToolWindow) {
        val chatId = ++chatQuantity
        val title = "Chat $chatId"
        chatTitles[chatId] = title
        messages.getOrPut(chatId) { mutableListOf() }
        createChatTab(chatId, title)
        persistChats()
    }

    private fun createChatTab(chatId: Int, title: String, select: Boolean = true) {
        if (chatTabs.containsKey(chatId)) {
            if (select) {
                toolWindowRef.contentManager.setSelectedContent(chatTabs[chatId]!!.content)
            }
            return
        }

        val controller = ChatTabController(projectRef, chatId, messages, logger) {
            persistChats()
        }
        val panel = SimpleToolWindowPanel(true, true).apply {
            setContent(controller.createPanel())
        }

        val content: Content = ContentFactory.getInstance()
            .createContent(panel, title, /* isLockable= */ false)
            .apply {
                isCloseable = true
                setShouldDisposeContent(true)
                setDisposer {
                    chatTabs.remove(chatId)
                    refreshHistoryContent()
                }
            }

        chatTabs[chatId] = ChatTabMeta(content)

        toolWindowRef.contentManager.addContent(content)
        if (select) {
            toolWindowRef.contentManager.setSelectedContent(content)
        }
        refreshHistoryContent()
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

        val scroll = JBScrollPane(messageContainer)

        chatTitles.entries
            .sortedBy { it.key }
            .forEach { (id, title) ->
                messageContainer.add(historyBubble(id, title))
                messageContainer.add(Box.createVerticalStrut(8))
            }

        refresh(messageContainer)
        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
            add(scroll, BorderLayout.CENTER)
        }
    }

    private fun historyBubble(chatId: Int, title: String): JComponent {
        val label = JLabel("<html>${title.replace("\n", "<br>")}</html>").apply {
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: java.awt.event.MouseEvent?) {
                    openChatTab(chatId)
                }
            })
        }

        class RenameAction : AnAction("Rename", "Rename chat", Icons.Edit) {
            override fun actionPerformed(e: AnActionEvent) {
                val newName = Messages.showInputDialog(
                    projectRef,
                    "Enter new name:",
                    "Rename Chat",
                    null,
                    title,
                    null
                )?.trim()
                if (!newName.isNullOrEmpty()) {
                    renameChat(chatId, newName)
                }
            }
        }

        class DeleteAction : AnAction("Delete", "Delete chat", Icons.Trash) {
            override fun actionPerformed(e: AnActionEvent) {
                val result = Messages.showYesNoDialog(
                    projectRef,
                    "Delete ${chatTitles[chatId] ?: title}?",
                    "Confirm Delete",
                    Messages.getYesButton(),
                    Messages.getNoButton(),
                    null
                )
                if (result == Messages.YES) {
                    deleteChat(chatId)
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
                    object : AnAction("Open") {
                        override fun actionPerformed(e: AnActionEvent) {
                            openChatTab(chatId)
                        }
                    },
                    object : AnAction("Copy Name") {
                        override fun actionPerformed(e: AnActionEvent) {
                            val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                            clipboard.setContents(java.awt.datatransfer.StringSelection(title), null)
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
            private val lightBg = JBColor(0xF4F6FF, 0x2A2C34)
            init {
                border = JBUI.Borders.empty(12, 20, 12, 8)
                background = lightBg
                add(label, BorderLayout.CENTER)
                add(actionToolbar.component.apply {
                    border = JBUI.Borders.empty()
                }, BorderLayout.EAST)
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

            override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)
        }
    }

    private fun openChatTab(chatId: Int) {
        val meta = chatTabs[chatId]
        if (meta != null) {
            toolWindowRef.contentManager.setSelectedContent(meta.content)
        } else {
            val title = chatTitles[chatId] ?: "Chat $chatId"
            createChatTab(chatId, title)
        }
    }

    private fun renameChat(chatId: Int, newName: String) {
        chatTitles[chatId] = newName
        chatTabs[chatId]?.content?.displayName = newName
        refreshHistoryContent()
        persistChats()
    }

    private fun deleteChat(chatId: Int) {
        chatTabs.remove(chatId)?.let {
            toolWindowRef.contentManager.removeContent(it.content, true)
        }
        messages.remove(chatId)
        chatTitles.remove(chatId)
        refreshHistoryContent()
        persistChats()
    }

    private fun refreshHistoryContent() {
        if (!::toolWindowRef.isInitialized) return
        val cm = toolWindowRef.contentManager
        val historyContent = cm.contents.firstOrNull { it.getUserData(HISTORY_TAB_KEY) == true } ?: return
        val panel = historyContent.component as? SimpleToolWindowPanel ?: return
        panel.setContent(createHistoryPanel(projectRef))
        panel.revalidate()
        panel.repaint()
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
        private val HISTORY_TAB_KEY = Key.create<Boolean>("com.github.egorbaranov.cod3.HISTORY_TAB")
    }

    private fun persistChats() {
        val persisted = chatTitles.keys.sorted().map { id ->
            val title = chatTitles[id] ?: "Chat $id"
            val items = messages[id].orEmpty().map {
                ChatState.Chat.Message(
                    role = it.role.name,
                    text = it.content,
                    attachments = it.attachments.map { att ->
                        ChatState.Chat.Attachment(att.title, att.content, att.navigationPath)
                    }
                )
            }.toMutableList()
            ChatState.Chat(id = id, title = title, messages = items)
        }
        chatState.chats = persisted.toMutableList()
    }

    private data class ChatTabMeta(val content: Content)
}
