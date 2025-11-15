package com.github.egorbaranov.cod3.toolWindow.chat

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.Box
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane

internal class ToolRenderer(
    private val messageContainer: JPanel,
    private val scroll: JScrollPane
) {

    private val cards = mutableMapOf<String, ToolCard>()

    fun render(viewModel: ToolCallViewModel, isFinal: Boolean) {
        val card = cards[viewModel.id] ?: createToolCard().also { created ->
            val bubble = createToolBubble(created.panel)
            messageContainer.add(bubble)
            refreshPanel(messageContainer)
            created.bubble = bubble
            cards[viewModel.id] = created
        }

        updateCard(card, viewModel)
        scrollToBottom(scroll)

        if (isFinal) {
            cards.remove(viewModel.id)
        }
    }

    private fun createToolCard(): ToolCard {
        val titleLabel = JLabel().apply {
            font = font.deriveFont(font.style or Font.BOLD)
        }
        val statusPanel = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0)).apply {
            isOpaque = false
        }
        val statusLabel = JLabel().apply {
            foreground = JBColor.GRAY
        }
        val detailsLabel = JLabel().apply {
            foreground = JBColor(0x94A3FF, 0x9BA9FF)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }
        statusPanel.add(statusLabel)
        statusPanel.add(Box.createHorizontalStrut(4))
        statusPanel.add(detailsLabel)
        val kindLabel = JLabel().apply { foreground = JBColor.GRAY }

        val inputSection = SectionPanel("Input")
        val outputSection = SectionPanel("Output")

        val panel = JBPanel<JBPanel<*>>(VerticalLayout(JBUI.scale(4))).apply {
            isOpaque = false
            border = JBUI.Borders.empty(4)
            add(titleLabel)
            add(statusPanel)
            add(kindLabel)
            add(inputSection.container)
            add(outputSection.container)
        }

        return ToolCard(panel, titleLabel, statusLabel, detailsLabel, kindLabel, inputSection, outputSection).apply {
            detailsLabel.apply {
                text = formatDetailsText(showDetails)
                addMouseListener(object : java.awt.event.MouseAdapter() {
                    override fun mouseClicked(e: java.awt.event.MouseEvent?) {
                        toggleDetails()
                    }
                })
            }
        }
    }

    private fun updateCard(card: ToolCard, snapshot: ToolCallViewModel) {
        card.titleLabel.text = snapshot.title?.takeIf { it.isNotBlank() } ?: snapshot.name ?: snapshot.id

        val statusText = snapshot.statusText ?: "unknown"
        card.statusLabel.text = "Status: $statusText"
        card.statusLabel.isVisible = true

        snapshot.kindText?.let {
            card.kindLabel.text = "Kind: ${it.lowercase()}"
            card.kindLabel.isVisible = true
        } ?: run { card.kindLabel.isVisible = false }

        card.detailsLabel.text = formatDetailsText(card.showDetails)
        card.inputSection.populate(snapshot.arguments.map { (key, value) -> "$key: $value" })
        card.outputSection.populate(snapshot.output.filter { it.isNotBlank() })
        card.inputSection.setVisible(card.showDetails)
        card.outputSection.setVisible(card.showDetails)

        card.panel.revalidate()
        card.panel.repaint()
        card.bubble?.isVisible = true
    }

    private data class ToolCard(
        val panel: JPanel,
        val titleLabel: JLabel,
        val statusLabel: JLabel,
        val detailsLabel: JLabel,
        val kindLabel: JLabel,
        val inputSection: SectionPanel,
        val outputSection: SectionPanel,
        var bubble: JPanel? = null,
        var showDetails: Boolean = false
    ) {
        fun toggleDetails() {
            showDetails = !showDetails
            detailsLabel.text = formatDetailsText(showDetails)
            inputSection.setVisible(showDetails)
            outputSection.setVisible(showDetails)
            panel.revalidate()
            panel.repaint()
        }
    }

    private class SectionPanel(private val title: String) {
        private val headerPanel = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0)).apply {
            isOpaque = false
        }
        val itemsPanel: JPanel = JBPanel<JBPanel<*>>(VerticalLayout(JBUI.scale(2))).apply {
            isOpaque = false
        }
        val container: JPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            isOpaque = false
            add(headerPanel, BorderLayout.NORTH)
            add(itemsPanel, BorderLayout.CENTER)
            isVisible = false
        }

        init {
            val titleLabel = JLabel(title).apply { font = font.deriveFont(font.style or Font.BOLD) }
            headerPanel.add(titleLabel)
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

        fun setVisible(visible: Boolean) {
            container.isVisible = visible && itemsPanel.componentCount > 0
        }
    }

    companion object {
        private fun formatDetailsText(showing: Boolean): String =
            if (showing) "<html><u>Hide details</u></html>" else "<html><u>Details</u></html>"
        private val TOOL_BUBBLE_COLOR = JBColor(Color(0xE7ECF9), Color(0x33363D))
        private fun createToolBubble(content: JPanel): JPanel {
            return object : JPanel(BorderLayout()) {
                init {
                    isOpaque = false
                    border = JBUI.Borders.empty(8, 12)
                    add(content, BorderLayout.CENTER)
                }

                override fun paintComponent(g: Graphics) {
                    val g2 = g.create() as Graphics2D
                    try {
                        g2.color = TOOL_BUBBLE_COLOR
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
        }
    }
}
