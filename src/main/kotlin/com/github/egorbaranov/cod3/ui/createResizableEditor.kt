package com.github.egorbaranov.cod3.ui

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorTextField
import com.intellij.util.ui.JBUI
import java.awt.Color
import javax.swing.JComponent
import javax.swing.KeyStroke
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent

fun createResizableEditor(project: Project, minHeight: Int = 48, padding: Int = 4): EditorTextField {
    val editorField = object : EditorTextField("", project, FileTypes.PLAIN_TEXT) {

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = service<EditorColorsManager>().globalScheme.defaultBackground
            g2.fillRoundRect(0, 0, width, height, 16, 16)
            g2.dispose()
            super.paintComponent(g)
        }

        override fun paintBorder(g: Graphics?) {
            val g2 = g?.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = service<EditorColorsManager>().globalScheme.defaultBackground
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
            editorEx.backgroundColor = service<EditorColorsManager>().globalScheme.defaultBackground
            return editorEx
        }

        override fun getInsets() = JBUI.insets(8)

    }.apply {
        setOneLineMode(false)
        isOpaque = false
        foreground = Color.red
        setPlaceholder("Ask anything...")
        preferredSize = Dimension(preferredSize.width, minHeight)
        minimumSize = Dimension(minimumSize.width, minHeight)
    }

    editorField.document.addDocumentListener(object : DocumentListener {
        override fun documentChanged(e: DocumentEvent) {
            val editor = editorField.editor ?: return
            val doc = editor.document
            val lineCount = doc.lineCount
            val contentComp = editor.contentComponent
            val fm = contentComp.getFontMetrics(contentComp.font)
            val lineHeight = fm.height

            val newHeight = (maxOf((lineCount + 1) * (lineHeight + 6), minHeight)).coerceAtMost(300)
            editorField.preferredSize = Dimension(editorField.preferredSize.width, newHeight)
            editorField.revalidate()
            (editorField.parent as? JComponent)?.revalidate()
        }

        // unused
        override fun beforeDocumentChange(event: DocumentEvent) {}
    })

    editorField.registerKeyboardAction(
        { _: ActionEvent -> /* noop */ },
        KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK),
        JComponent.WHEN_FOCUSED
    )

    return editorField
}
