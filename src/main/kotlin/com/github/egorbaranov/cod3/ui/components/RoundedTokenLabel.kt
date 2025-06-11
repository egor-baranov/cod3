package com.github.egorbaranov.cod3.ui.components

import com.intellij.ui.JBColor
import java.awt.*
import javax.swing.JComponent

class RoundedTokenLabel(private val text: String) : JComponent() {

    private val padding = 8
    private val arcSize = 12
    private val font = Font("Default", Font.PLAIN, 10)

    init {
        val fontMetrics = getFontMetrics(font)
        preferredSize = Dimension(
            fontMetrics.stringWidth(text) + padding * 2,
            fontMetrics.height + padding
        )
        isOpaque = false
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            // Background
            g2.color = JBColor.LIGHT_GRAY
            g2.fillRoundRect(0, 0, width, height, arcSize, arcSize)

            // Text
            g2.color = JBColor.BLACK
            g2.font = font
            val fm = g2.fontMetrics
            val textX = padding
            val textY = (height + fm.ascent) / 2 - 2
            g2.drawString(text, textX, textY)
        } finally {
            g2.dispose()
        }
    }

    override fun getPreferredSize(): Dimension {
        return Dimension(
            getFontMetrics(font).stringWidth(text) + padding * 2,
            getFontMetrics(font).height + padding
        )
    }

    override fun getMinimumSize(): Dimension = preferredSize

    override fun getMaximumSize(): Dimension = preferredSize
}
