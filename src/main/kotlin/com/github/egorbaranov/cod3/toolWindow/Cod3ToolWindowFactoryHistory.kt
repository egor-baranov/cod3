package com.github.egorbaranov.cod3.toolWindow

import com.github.egorbaranov.cod3.ui.Icons
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.*

internal fun Cod3ToolWindowFactory.createHistoryPanel(project: Project): JPanel {
    val messageContainer = JBPanel<JBPanel<*>>().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.empty(4)
    }

    val scroll = JScrollPane(messageContainer)

    for (i in 1..chatQuantity) {
        messageContainer.add(historyBubble("Chat $i"))
        messageContainer.add(Box.createVerticalStrut(8))
    }

    refresh(messageContainer)
    return JPanel(java.awt.BorderLayout()).apply {
        border = JBUI.Borders.empty(8)
        add(scroll, java.awt.BorderLayout.CENTER)
    }
}

internal fun refresh(panel: JPanel) {
    panel.revalidate()
    panel.repaint()
}

internal fun Cod3ToolWindowFactory.historyBubble(text: String): JComponent {
    val label = JLabel("<html>${text.replace("\n", "<br>")}</html>")

    val actionGroup = DefaultActionGroup().apply {
        add(object : AnAction("Rename", "Rename chat", Icons.Edit) {
            override fun actionPerformed(e: AnActionEvent) {
                JOptionPane.showMessageDialog(null, "Rename clicked for: $text")
            }
        })
        add(object : AnAction("Delete", "Delete chat", Icons.Trash) {
            override fun actionPerformed(e: AnActionEvent) {
                val result = JOptionPane.showConfirmDialog(
                    null,
                    "Delete this item?",
                    "Confirm Delete",
                    JOptionPane.YES_NO_OPTION
                )
                if (result == JOptionPane.YES_OPTION) {
                    // TODO implement deletion
                }
            }
        })
        add(object : ActionGroup("More", true) {
            init {
                templatePresentation.icon = AllIcons.Actions.More
                templatePresentation.putClientProperty(ActionButton.HIDE_DROPDOWN_ICON, java.lang.Boolean.TRUE)
            }

            override fun getChildren(e: AnActionEvent?): Array<AnAction> = arrayOf(
                object : AnAction("Details") {
                    override fun actionPerformed(e: AnActionEvent) {
                        JOptionPane.showMessageDialog(null, "Details for: $text")
                    }
                },
                object : AnAction("Copy") {
                    override fun actionPerformed(e: AnActionEvent) {
                        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
                    }
                }
            )
        })
    }

    val actionToolbar: ActionToolbar = ActionManager.getInstance()
        .createActionToolbar("HistoryBubbleToolbar", actionGroup, true)
        .apply {
            targetComponent = null
            component.isOpaque = false
            isReservePlaceAutoPopupIcon = false
        }

    return object : JPanel(java.awt.BorderLayout()) {
        init {
            add(label, java.awt.BorderLayout.CENTER)
            add(actionToolbar.component.apply { border = JBUI.Borders.empty() }, java.awt.BorderLayout.EAST)
            isOpaque = true
            background = JBColor.LIGHT_GRAY
            border = JBUI.Borders.empty(12, 20, 12, 8)
            cursor = Cursor(Cursor.HAND_CURSOR)
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = JBColor.LIGHT_GRAY
                g2.fillRoundRect(0, 0, width, height, 24, 24)
            } finally {
                g2.dispose()
            }
        }

        override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)
    }
}
