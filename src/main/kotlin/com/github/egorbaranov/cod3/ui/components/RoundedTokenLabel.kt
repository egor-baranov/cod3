package com.github.egorbaranov.cod3.ui.components

import com.intellij.ui.JBColor
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JPanel

class RoundedTokenLabel(
    private val text: String,
    private val icon: Icon? = null,
    private val closable: Boolean = true,
    var onClose: () -> Unit = {},
    private val onClick: (() -> Unit)? = null
) : JPanel(FlowLayout(FlowLayout.LEFT)) {

    private val padding = 8
    private val trailingPadding = 8
    private val iconTextGap = 4
    private val closeLeftPadding = 6   // ↑ was 4
    private val closeRightPadding = 10  // ↑ was 6
    private val arcSize = 12
    private val closeSize = 6
    private val closeHitPadding = 3    // ↑ was 2
    private val font = Font("Default", Font.PLAIN, 10)

    private var closeButtonBounds: Rectangle? = null

    init {
        isOpaque = false

        val clickable = onClick != null
        if (closable || clickable) {
            addMouseListener(object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    if (closable && closeButtonBounds?.contains(e.point) == true) {
                        onClose()
                    } else if (clickable) {
                        onClick?.invoke()
                    }
                }
            })

            addMouseMotionListener(object : MouseAdapter() {
                override fun mouseMoved(e: MouseEvent) {
                    cursor = when {
                        closable && closeButtonBounds?.contains(e.point) == true ->
                            Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

                        clickable -> Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        else -> Cursor.getDefaultCursor()
                    }
                }
            })
        }
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = JBColor.LIGHT_GRAY
            g2.fillRoundRect(0, 0, width, height, arcSize, arcSize)
        } finally {
            g2.dispose()
        }

        val g2t = g.create() as Graphics2D
        try {
            g2t.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2t.font = font
            val fm = g2t.fontMetrics
            val centerY = height / 2
            var x = padding

            icon?.let {
                val iconY = centerY - it.iconHeight / 2
                it.paintIcon(this, g2t, x, iconY)
                x += it.iconWidth + iconTextGap
            }

            g2t.color = JBColor.BLACK
            val textY = centerY + (fm.ascent - fm.descent) / 2
            g2t.drawString(text, x, textY)

            val textWidth = fm.stringWidth(text)
            x += textWidth + if (closable) closeLeftPadding else trailingPadding

            if (closable) {
                val closeX = x
                val closeY = centerY - closeSize / 2
                g2t.color = JBColor.DARK_GRAY
                g2t.stroke = BasicStroke(1.2f)
                g2t.drawLine(closeX, closeY, closeX + closeSize, closeY + closeSize)
                g2t.drawLine(closeX + closeSize, closeY, closeX, closeY + closeSize)

                // Hit box is a bit larger for easier clicking
                closeButtonBounds = Rectangle(
                    closeX - closeHitPadding,
                    closeY - closeHitPadding,
                    closeSize + closeHitPadding * 2,
                    closeSize + closeHitPadding * 2
                )
            } else {
                closeButtonBounds = null
            }
        } finally {
            g2t.dispose()
        }
    }

    override fun getPreferredSize(): Dimension {
        val fm = getFontMetrics(font)
        val textWidth = fm.stringWidth(text)
        val textHeight = fm.height

        val iconWidth = icon?.iconWidth ?: 0
        val iconHeight = icon?.iconHeight ?: 0

        val closeWidth = if (closable) closeLeftPadding + closeSize + closeRightPadding else trailingPadding
        val totalWidth = padding +
                (if (icon != null) iconWidth + iconTextGap else 0) +
                textWidth + closeWidth

        val totalHeight = padding + maxOf(textHeight, iconHeight, if (closable) closeSize else textHeight)

        return Dimension(totalWidth, totalHeight)
    }

    override fun getMinimumSize(): Dimension = preferredSize
    override fun getMaximumSize(): Dimension = preferredSize
}
