package com.github.egorbaranov.cod3.toolWindow.chat

import com.github.egorbaranov.cod3.ui.components.ChatBubble
import com.github.egorbaranov.cod3.ui.components.RoundedTokenLabel
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.border.EmptyBorder

internal fun JPanel.addChatBubble(
    component: JPanel,
    background: Color = JBColor(0x3F4248, 0x33363D)
): JPanel {
    val bubble = createBubbleWrapper(component, background)
    add(bubble)
    refreshPanel(this)
    return bubble
}

internal fun JPanel.appendUserBubble(
    text: String,
    attachments: List<ChatAttachment> = emptyList(),
    project: Project? = null
): JComponent {
    val hasText = text.isNotBlank()
    val bubble = if (hasText) ChatBubble(text, JBColor.LIGHT_GRAY) else null

    if (attachments.isEmpty()) {
        bubble?.let {
            add(it)
            refreshPanel(this)
            return it
        }
        return this
    }

    val wrapper = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        border = JBUI.Borders.empty()
    }

    bubble?.let { wrapper.add(it) }
    if (bubble != null) {
        wrapper.add(Box.createVerticalStrut(JBUI.scale(6)))
    }
    wrapper.add(buildAttachmentChips(project, attachments))

    add(wrapper)
    refreshPanel(this)
    return bubble ?: wrapper
}

internal fun JPanel.appendAssistantBubble(text: String): ChatBubble {
    val bubble = ChatBubble(text, UIUtil.getPanelBackground(), assistant = true)
    add(bubble)
    refreshPanel(this)
    return bubble
}

internal fun refreshPanel(container: JPanel) {
    container.revalidate()
    container.repaint()
}

private fun buildAttachmentChips(project: Project?, attachments: List<ChatAttachment>): JComponent =
    JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(6))).apply {
        isOpaque = false
        border = JBUI.Borders.empty()
        attachments.forEach { attachment ->
            val clickAction = if (project != null && !attachment.navigationPath.isNullOrBlank()) {
                { openFile(project, attachment.navigationPath) }
            } else null
            val label = RoundedTokenLabel(
                attachment.title.ifBlank { "Snippet" },
                icon = attachment.icon,
                closable = false,
                onClick = clickAction
            )
            add(label)
        }
    }

private fun openFile(project: Project, path: String) {
    val file = LocalFileSystem.getInstance().findFileByPath(path) ?: return
    ApplicationManager.getApplication().invokeLater {
        if (project.isDisposed) return@invokeLater
        FileEditorManager.getInstance(project).openFile(file, true)
    }
}

internal fun scrollToBottom(scroll: JScrollPane) {
    val bar = scroll.verticalScrollBar
    if (bar.value >= bar.maximum - 20) {
        bar.value = bar.maximum
    }
}

private fun createBubbleWrapper(
    component: JComponent,
    background: Color
): JPanel =
    object : JPanel(BorderLayout()) {
        init {
            this.background = background
            border = JBUI.Borders.empty(6)
            add(component)
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                g2.color = background
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.fillRoundRect(0, 0, width, height, 24, 24)
            } finally {
                g2.dispose()
            }
            super.paintComponent(g)
        }
    }
