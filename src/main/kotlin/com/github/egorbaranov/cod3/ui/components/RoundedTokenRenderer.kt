package com.github.egorbaranov.cod3.ui.components

import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.JBColor
import java.awt.*
import javax.swing.Icon

class RoundedTokenRenderer(
    private val text: String,
    private val icon: Icon? = null  // optional icon on the left
) : EditorCustomElementRenderer {

    // Padding around text/icon
    private val horizontalPadding = 6
    private val iconTextGap = 4
    private val verticalPadding = 2
    private val arcWidth = 12
    private val arcHeight = 12

    override fun calcWidthInPixels(inlay: Inlay<*>): Int {
        val scheme = inlay.editor.colorsScheme
        val font: Font = scheme.getFont(EditorFontType.PLAIN)
        // Use FontMetrics from contentComponent
        val fm = inlay.editor.contentComponent.getFontMetrics(font)

        val textWidth = fm.stringWidth(text)
        val iconWidth = icon?.iconWidth ?: 0

        // Total width: left + icon + gap + text + right
        return horizontalPadding * 2 + iconWidth + (if (icon != null) iconTextGap else 0) + textWidth
    }

    override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            // Draw background rounded rectangle
            g2.color = JBColor.LIGHT_GRAY
            g2.fillRoundRect(
                targetRegion.x, targetRegion.y,
                targetRegion.width, targetRegion.height,
                arcWidth, arcHeight
            )

            // Set font before measuring/drawing
            val font: Font = inlay.editor.colorsScheme.getFont(EditorFontType.PLAIN)
            g2.font = font
            val fm = inlay.editor.contentComponent.getFontMetrics(font)

            // Determine text color from textAttributes, fallback to black
            val textColor = textAttributes.foregroundColor ?: JBColor.BLACK
            g2.color = textColor

            // Compute content area
            val contentXStart = targetRegion.x + horizontalPadding
            val contentRight = targetRegion.x + targetRegion.width - horizontalPadding
            val centerY = targetRegion.y + targetRegion.height / 2

            var drawX = contentXStart
            // Draw icon if present
            if (icon != null) {
                val iconY = centerY - icon.iconHeight / 2
                icon.paintIcon(inlay.editor.contentComponent, g2, drawX, iconY)
                drawX += icon.iconWidth + iconTextGap
            }

            // Available width for text
            val remainingWidthForText = contentRight - drawX

            // Truncate text with ellipsis if needed
            var displayText = text
            val fullTextWidth = fm.stringWidth(displayText)
            if (fullTextWidth > remainingWidthForText) {
                val ellipsis = "…"
                var low = 0
                var high = displayText.length
                while (low < high) {
                    val mid = (low + high + 1) / 2
                    val substr = displayText.substring(0, mid) + ellipsis
                    if (fm.stringWidth(substr) <= remainingWidthForText) {
                        low = mid
                    } else {
                        high = mid - 1
                    }
                }
                displayText = if (low > 0) {
                    text.substring(0, low) + ellipsis
                } else {
                    // If even one char doesn't fit, try only ellipsis
                    if (fm.stringWidth(ellipsis) <= remainingWidthForText) ellipsis else ""
                }
            }

            // Compute baseline Y so text is vertically centered
            val textAscent = fm.ascent
            val textDescent = fm.descent
            val textY = centerY + (textAscent - textDescent) / 2

            if (displayText.isNotEmpty()) {
                g2.drawString(displayText, drawX, textY)
            }

        } finally {
            g2.dispose()
        }
    }
}
