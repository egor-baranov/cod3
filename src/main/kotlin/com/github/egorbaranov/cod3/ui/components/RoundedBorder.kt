package com.github.egorbaranov.cod3.ui.components

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.*
import javax.swing.*
import javax.swing.border.Border

// Custom rounded border
class RoundedBorder(private val radius: Int) : Border {
    override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
        val g2 = g as Graphics2D
        g2.color = JBColor.GRAY.darker()
        g2.stroke = BasicStroke(2f)
        g2.drawRoundRect(x, y, width - 1, height - 1, radius, radius)
    }

    override fun getBorderInsets(c: Component): Insets = JBUI.insets(radius, radius, radius, radius)

    override fun isBorderOpaque(): Boolean = false
}