package com.github.egorbaranov.cod3.toolWindow.chat

import com.agentclientprotocol.model.PlanEntryPriority
import com.github.egorbaranov.cod3.acp.PlanEntryView
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import com.intellij.ui.components.panels.VerticalLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane

import java.awt.Color

internal class PlanRenderer(
    private val messageContainer: JPanel,
    private val scroll: JScrollPane
) {
    private var card: PlanCard? = null

    fun render(entries: List<PlanEntryView>) {
        if (entries.isEmpty()) {
            card?.let {
                it.panel.isVisible = false
                it.bubble?.isVisible = false
                refreshPanel(messageContainer)
            }
            return
        }

        val planCard = card ?: createPlanCard().also { created ->
            val bubble = messageContainer.addChatBubble(created.panel, PLAN_BUBBLE_COLOR)
            created.bubble = bubble
            card = created
        }

        updatePlanCard(planCard, entries)
        planCard.panel.isVisible = true
        planCard.bubble?.isVisible = true
        refreshPanel(messageContainer)
        scrollToBottom(scroll)
    }

    private fun createPlanCard(): PlanCard {
        val headerLabel = JLabel("Plan").apply {
            font = font.deriveFont(font.style or java.awt.Font.BOLD)
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
            font = font.deriveFont(java.awt.Font.ITALIC, font.size2D - 1f)
        }
        val container = JBPanel<JBPanel<*>>(VerticalLayout(JBUI.scale(2))).apply {
            isOpaque = false
            add(contentLabel)
            add(metaLabel)
        }
        return PlanStepRow(container, contentLabel, metaLabel)
    }

    private data class PlanCard(
        val panel: JPanel,
        val stepsPanel: JPanel,
        val stepRows: MutableList<PlanStepRow> = mutableListOf(),
        var bubble: JPanel? = null
    )

    private data class PlanStepRow(
        val container: JPanel,
        val contentLabel: JLabel,
        val metaLabel: JLabel
    )

    companion object {
        private val PLAN_BUBBLE_COLOR = JBColor(Color(0x4A4E55), Color(0x393D43))
    }
}
