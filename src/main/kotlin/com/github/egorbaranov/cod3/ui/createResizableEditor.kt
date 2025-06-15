package com.github.egorbaranov.cod3.ui

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorTextField
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.JComponent.WHEN_FOCUSED
import javax.swing.KeyStroke

fun createResizableEditor(
    project: Project,
    minHeight: Int = JBUI.scale(48),
    maxHeight: Int = JBUI.scale(300),
    padding: Int = JBUI.scale(4),
    lineSpacing: Int = JBUI.scale(4)
): EditorTextField {
    val editorField = object : EditorTextField("", project, FileTypes.PLAIN_TEXT) {

        init {
            this.isOpaque = true
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = JBColor.gray.darker().darker().darker().darker()
            g2.fillRoundRect(0, 0, width, height, JBUI.scale(16), JBUI.scale(16))
            g2.dispose()
        }

        override fun paintBorder(g: Graphics?) {
            if (g == null) return
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = JBColor.gray.darker().darker().darker().darker()
                g2.drawRoundRect(
                    0,
                    0,
                    width - 1,
                    height - 1,
                    JBUI.scale(16),
                    JBUI.scale(16)
                )
            } finally {
                g2.dispose()
            }
        }

        override fun updateBorder(editor: EditorEx) {
            editor.setBorder(
                JBUI.Borders.empty(padding)
            )
        }

        override fun createEditor(): EditorEx {
            val editorEx = super.createEditor()
            editorEx.settings.isUseSoftWraps = true
            editorEx.backgroundColor = JBColor.gray.darker().darker().darker().darker()
            return editorEx
        }

        override fun getInsets() = JBUI.insets(8)
    }.apply {
        setOneLineMode(false)
        setPlaceholder("Ask anything...")
        // Initial preferred/minimum size; width is flexible in layout
        preferredSize = Dimension(preferredSize.width, minHeight)
        minimumSize = Dimension(minimumSize.width, minHeight)
    }

    // Helper to recalc height based on visual lines:
    fun recalcHeight(editor: EditorEx) {
        val doc = editor.document
        val logicalLines = doc.lineCount

        // Count soft wraps: cast to SoftWrapModelEx to access registeredSoftWraps
        val softWrapModel = editor.softWrapModel
        val softWrapsCount: Int = softWrapModel.registeredSoftWraps.size
        val visualLines = logicalLines + softWrapsCount

        // Compute line height
        val contentComp = editor.contentComponent
        val fm = contentComp.getFontMetrics(contentComp.font)
        val lineHeight = fm.height

        // Add 1 to visualLines for padding, multiply by lineHeight + lineSpacing
        val targetHeight = ((visualLines + 1) * (lineHeight + lineSpacing))
            .coerceAtLeast(minHeight)
            .coerceAtMost(maxHeight)

        // Update preferredSize and revalidate
        val currentWidth = editorField.preferredSize.width
        editorField.preferredSize = Dimension(currentWidth, targetHeight)
        editorField.revalidate()
        (editorField.parent as? JComponent)?.revalidate()
    }

    // Document listener: when content changes, recalc height
    editorField.document.addDocumentListener(object : DocumentListener {
        override fun beforeDocumentChange(event: DocumentEvent) {}
        override fun documentChanged(e: DocumentEvent) {
            val editor = editorField.editor as? EditorEx ?: return
            recalcHeight(editor)
        }
    })

    // Component listener: when width changes, recalc height (wrapping may change)
    editorField.addComponentListener(object : java.awt.event.ComponentAdapter() {
        override fun componentResized(e: java.awt.event.ComponentEvent) {
            val editor = editorField.editor as? EditorEx ?: return
            recalcHeight(editor)
        }
    })

    // If desired: also listen on the inner editor content component:
    editorField.addNotify() // ensure editor is created
    editorField.editor?.contentComponent?.addComponentListener(object : java.awt.event.ComponentAdapter() {
        override fun componentResized(e: java.awt.event.ComponentEvent) {
            val editor = editorField.editor as? EditorEx ?: return
            recalcHeight(editor)
        }
    })

    // Preserve existing ENTER behavior: allow Shift+Enter no-op or custom
    editorField.registerKeyboardAction(
        { _: ActionEvent -> /* noop for Shift+Enter */ },
        KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK),
        WHEN_FOCUSED
    )

    return editorField
}
