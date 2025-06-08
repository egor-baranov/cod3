package com.github.egorbaranov.cod3.ui

import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.Font
import java.awt.Graphics2D
import java.awt.geom.Rectangle2D
import javax.swing.JComponent
import java.awt.Graphics
import java.awt.Rectangle
import javax.swing.RepaintManager


class ComponentInlayRenderer(private val component: JComponent) : EditorCustomElementRenderer {

    override fun calcWidthInPixels(inlay: Inlay<*>): Int = component.preferredSize.width

    override fun calcHeightInPixels(inlay: Inlay<*>): Int = component.preferredSize.height

    override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
        val g2d = g.create()
        try {
            g2d.translate(targetRegion.x, targetRegion.y)
            component.setBounds(0, 0, component.preferredSize.width, component.preferredSize.height)

            val repaintManager = RepaintManager.currentManager(component)
            val doubleBufferingEnabled = repaintManager.isDoubleBufferingEnabled
            repaintManager.isDoubleBufferingEnabled = false
            try {
                component.paint(g2d)
            } finally {
                repaintManager.isDoubleBufferingEnabled = doubleBufferingEnabled
            }
        } finally {
            g2d.dispose()
        }
    }
}



class HintRenderer(private val text: String) : EditorCustomElementRenderer {

    override fun calcWidthInPixels(inlay: Inlay<*>): Int {
        val fontMetrics = inlay.editor.contentComponent.getFontMetrics(getFont())
        return 0
    }

    override fun calcHeightInPixels(inlay: Inlay<*>): Int {
        val fontMetrics = inlay.editor.contentComponent.getFontMetrics(getFont())
        return JBUI.scale(80)
    }

    override fun paint(
        inlay: Inlay<*>,
        g: Graphics2D,
        targetRegion: Rectangle2D,
        textAttributes: TextAttributes
    ) {
    }

    private fun getFont(): Font = Font("Dialog", Font.PLAIN, JBUI.scale(12))
}
