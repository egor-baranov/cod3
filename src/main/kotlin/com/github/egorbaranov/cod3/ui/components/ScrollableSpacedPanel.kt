package com.github.egorbaranov.cod3.ui.components

import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.Scrollable
import javax.swing.SwingConstants
import javax.swing.JViewport

/**
 * A horizontal panel that spaces components by [gap] px, centers them vertically,
 * and when placed in a JScrollPane/JBScrollPane, will scroll horizontally instead of stretching.
 */
class ScrollableSpacedPanel(
    private val gap: Int = 2
) : JPanel(), Scrollable {

    init {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        isOpaque = false
    }

    override fun add(comp: Component?): Component? {
        if (comp == null) return null

        // If this is a JComponent, you can optionally set alignmentY; 
        // but overriding getAlignmentY in the child is better.
        if (comp is JComponent) {
            comp.alignmentY = Component.CENTER_ALIGNMENT
        }
        if (componentCount > 0) {
            super.add(Box.createRigidArea(Dimension(gap, 0)))
        }
        return super.add(comp)
    }

    // --- Scrollable implementation ---

    /**
     * Return preferred viewport size: let the scrollpane use our preferredSize initially.
     */
    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize

    /**
     * When in a viewport, we do NOT track viewport width: returning false
     * means if our preferred width > viewport width, horizontal scrollbar appears.
     */
    override fun getScrollableTracksViewportWidth(): Boolean = false

    /**
     * We DO track viewport height: the panel’s height will match viewport height,
     * so no vertical scrollbar unless content taller than viewport.
     */
    override fun getScrollableTracksViewportHeight(): Boolean = true

    /**
     * Unit increment for scrolling: e.g., 10px or based on gap or component size.
     * You can tweak as desired; here we use a small fixed increment.
     */
    override fun getScrollableUnitIncrement(
        visibleRect: java.awt.Rectangle?, orientation: Int, direction: Int
    ): Int {
        // For horizontal scroll: scroll by gap or a small fixed step
        return if (orientation == SwingConstants.HORIZONTAL) {
            gap.coerceAtLeast(10)
        } else {
            // vertical scroll unlikely used, but return small increment
            10
        }
    }

    /**
     * Block increment (page scroll). Scroll by viewport width minus some margin.
     */
    override fun getScrollableBlockIncrement(
        visibleRect: java.awt.Rectangle?, orientation: Int, direction: Int
    ): Int {
        return if (orientation == SwingConstants.HORIZONTAL) {
            // Scroll by roughly one viewport width:
            visibleRect?.width?.let { it - gap }?.coerceAtLeast(gap) ?: gap
        } else {
            visibleRect?.height?.coerceAtLeast(gap) ?: gap
        }
    }
}
