package com.github.egorbaranov.cod3.ui.components

import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.JBColor
import java.awt.*

class RoundedTokenRenderer(private val text: String) : EditorCustomElementRenderer {

    override fun calcWidthInPixels(inlay: Inlay<*>): Int {
        val font = inlay.editor.colorsScheme.getFont(EditorFontType.PLAIN)
        val fm = inlay.editor.contentComponent.getFontMetrics(font)
        return fm.stringWidth(text) + 20
    }

    override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            // Draw background rounded rectangle
            g2.color = JBColor.LIGHT_GRAY
            g2.fillRoundRect(targetRegion.x, targetRegion.y, targetRegion.width, targetRegion.height, 12, 12)

            // Draw the text
            g2.color = JBColor.BLACK
            val fm = g2.fontMetrics
            val textX = targetRegion.x + 10
            val textY = targetRegion.y + (targetRegion.height + fm.ascent) / 2 - 2
            g2.drawString(text, textX, textY)
        } finally {
            g2.dispose()
        }
    }
}
