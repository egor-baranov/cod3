package com.github.egorbaranov.cod3.toolWindow.chat

import com.github.egorbaranov.cod3.acp.ToolCallSnapshot
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import java.awt.Color
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane

internal class ToolRenderer(
    private val messageContainer: JPanel,
    private val scroll: JScrollPane
) {

    private val cards = mutableMapOf<String, ToolCard>()

    fun render(snapshot: ToolCallSnapshot, isFinal: Boolean) {
        val card = cards[snapshot.id] ?: createToolCard().also { created ->
            val bubble = messageContainer.addChatBubble(created.panel, TOOL_BUBBLE_COLOR)
            created.bubble = bubble
            cards[snapshot.id] = created
        }

        updateCard(card, snapshot)
        scrollToBottom(scroll)

        if (isFinal) {
            cards.remove(snapshot.id)
        }
    }

    private fun createToolCard(): ToolCard {
        val titleLabel = JLabel().apply {
            font = font.deriveFont(font.style or java.awt.Font.BOLD)
        }
        val statusLabel = JLabel().apply { foreground = JBColor.GRAY }
        val kindLabel = JLabel().apply { foreground = JBColor.GRAY }

        val inputSection = SectionPanel("Input")
        val outputSection = SectionPanel("Output")

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

    private fun updateCard(card: ToolCard, snapshot: ToolCallSnapshot) {
        card.titleLabel.text = snapshot.title?.takeIf { it.isNotBlank() } ?: snapshot.name ?: snapshot.id

        val statusText = snapshot.status?.name?.lowercase()?.replace('_', ' ') ?: "unknown"
        card.statusLabel.text = "Status: $statusText"
        card.statusLabel.isVisible = true

        snapshot.kind?.let {
            card.kindLabel.text = "Kind: ${it.name.lowercase().replace('_', ' ')}"
            card.kindLabel.isVisible = true
        } ?: run { card.kindLabel.isVisible = false }

        card.inputSection.populate(snapshot.arguments.map { (key, value) -> "$key: $value" })
        card.outputSection.populate(snapshot.content.filter { it.isNotBlank() })

        card.panel.revalidate()
        card.panel.repaint()
        card.bubble?.isVisible = true
    }

    private data class ToolCard(
        val panel: JPanel,
        val titleLabel: JLabel,
        val statusLabel: JLabel,
        val kindLabel: JLabel,
        val inputSection: SectionPanel,
        val outputSection: SectionPanel,
        var bubble: JPanel? = null
    )

    private class SectionPanel(title: String) {
        val itemsPanel: JPanel = JBPanel<JBPanel<*>>(VerticalLayout(JBUI.scale(2))).apply {
            isOpaque = false
        }
        val container: JPanel = JBPanel<JBPanel<*>>(VerticalLayout(JBUI.scale(2))).apply {
            isOpaque = false
            add(JLabel(title).apply { font = font.deriveFont(font.style or java.awt.Font.BOLD) })
            add(itemsPanel)
            isVisible = false
        }

        fun populate(items: List<String>) {
            itemsPanel.removeAll()
            if (items.isEmpty()) {
                container.isVisible = false
            } else {
                items.forEach { itemsPanel.add(JLabel("\u2022 $it")) }
                container.isVisible = true
            }
        }
    }

    companion object {
        private val TOOL_BUBBLE_COLOR = JBColor(Color(0x50545B), Color(0x3B3F45))
    }
}
