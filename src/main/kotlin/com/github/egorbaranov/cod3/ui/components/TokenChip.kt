package com.github.egorbaranov.cod3.ui.components

import com.intellij.ui.JBColor
import java.awt.Dimension
import java.awt.FontMetrics
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.Color
import javax.swing.JLabel

/**
 * A Swing component that displays text inside a rounded rectangle,
 * similar to a “token” or “chip” UI element.
 *
 * Usage:
 *   val chip = TokenChipK("Hello")
 *   panel.add(chip)
 *
 * You can customize background/foreground colors, font, padding, and corner radius.
 */
class TokenChip @JvmOverloads constructor(
    text: String,
    backgroundColor: Color = JBColor.LIGHT_GRAY,
    foregroundColor: Color = JBColor.WHITE
) : JLabel(text) {

    private var hPadding: Int = DEFAULT_H_PADDING
    private var vPadding: Int = DEFAULT_V_PADDING
    private var arcWidth: Int = DEFAULT_ARC_WIDTH
    private var arcHeight: Int = DEFAULT_ARC_HEIGHT

    private var chipBackground: Color = backgroundColor
    private var chipForeground: Color = foregroundColor

    init {
        // We will paint custom background; JLabel's opaque should be false
        isOpaque = false
        super.setForeground(chipForeground)
        // Font can be customized externally via setFont(...)
    }

    /**
     * Customize horizontal and vertical padding (in pixels).
     * Remember to call revalidate()/repaint() afterwards if changing at runtime.
     */
    fun setPadding(horizontal: Int, vertical: Int) {
        hPadding = horizontal
        vPadding = vertical
        revalidate()
        repaint()
    }

    /**
     * Customize corner arc size.
     */
    fun setArcSize(arcW: Int, arcH: Int) {
        arcWidth = arcW
        arcHeight = arcH
        revalidate()
        repaint()
    }

    /**
     * Override background setter: updates chipBackground and repaints.
     */
    override fun setBackground(bg: Color?) {
        if (bg != null) {
            chipBackground = bg
            repaint()
        } else {
            super.setBackground(bg)
        }
    }

    /**
     * Override foreground setter: updates chipForeground and repaints.
     */
    override fun setForeground(fg: Color?) {
        if (fg != null) {
            chipForeground = fg
            super.setForeground(fg)
            repaint()
        } else {
            super.setForeground(fg)
        }
    }

    /**
     * Override text setter: revalidate/repaint when text changes.
     */
    override fun setText(text: String?) {
        super.setText(text)
        revalidate()
        repaint()
    }

    /**
     * Calculate preferred size based on current font metrics + padding.
     */
    override fun getPreferredSize(): Dimension {
        val fm: FontMetrics = getFontMetrics(font)
        val txt = text ?: ""
        val textWidth = fm.stringWidth(txt)
        val textHeight = fm.height
        val width = textWidth + hPadding * 2
        val height = textHeight + vPadding * 2
        return Dimension(width, height)
    }

    /**
     * Paint the rounded background and then the text.
     */
    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            // Enable anti-aliasing for smooth corners and text
            g2.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
            )

            val width = width
            val height = height

            // Draw background rounded rectangle
            g2.color = chipBackground
            g2.fillRoundRect(0, 0, width, height, arcWidth, arcHeight)

            // Draw text
            val txt = text
            if (!txt.isNullOrEmpty()) {
                g2.color = chipForeground
                g2.font = font
                val fm = g2.fontMetrics
                // Horizontal: start at hPadding
                val textX = hPadding
                // Vertical centering: (height - fm.height)/2 + fm.ascent
                val textY = (height - fm.height) / 2 + fm.ascent
                g2.drawString(txt, textX, textY)
            }
        } finally {
            g2.dispose()
        }
    }

    companion object {
        private const val DEFAULT_H_PADDING = 10
        private const val DEFAULT_V_PADDING = 4
        private const val DEFAULT_ARC_WIDTH = 12
        private const val DEFAULT_ARC_HEIGHT = 12
    }
}
