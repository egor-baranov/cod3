package com.github.egorbaranov.cod3.toolWindow.chat

import com.github.egorbaranov.cod3.ui.components.ChatBubble
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane

internal fun JPanel.addChatBubble(component: JPanel): JPanel {
    val bubble = object : JPanel(BorderLayout()) {
        init {
            background = JBColor.LIGHT_GRAY
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
    add(bubble)
    refreshPanel(this)
    return bubble
}

internal fun JPanel.appendUserBubble(text: String): JComponent {
    val bubble = ChatBubble(text, JBColor.LIGHT_GRAY)
    add(bubble)
    refreshPanel(this)
    return bubble
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

internal fun scrollToBottom(scroll: JScrollPane) {
    val bar = scroll.verticalScrollBar
    if (bar.value >= bar.maximum - 20) {
        bar.value = bar.maximum
    }
}
