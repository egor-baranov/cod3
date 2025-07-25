package com.github.egorbaranov.cod3.ui.components

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * Creates an editor component bound to an existing Document.
 * @param document the IntelliJ Document to bind to
 * @param language optional language for syntax highlighting (extension or name)
 * @param heightCallback callback to adjust container height when content changes
 */
fun createEditorComponent(
    document: Document,
    language: String? = null,
    path: String = "",
    heightCallback: (Int) -> Unit
): JComponent {
    val project = ProjectManager.getInstance().defaultProject

    // Determine file type based on language if provided
    val fileType = language?.let {
        FileTypeManager.getInstance().registeredFileTypes
            .firstOrNull { type ->
                val lang = it.lowercase().trim()
                type.name.lowercase() == lang || type.defaultExtension.lowercase() == lang
            }
    } ?: PlainTextFileType.INSTANCE

    // Create editor bound to the existing document
    val editor = EditorFactory.getInstance()
        .createEditor(document, project, fileType, true) as EditorEx
    editor.isViewer = true
    editor.settings.apply {
        isLineNumbersShown = true
        isFoldingOutlineShown = false
        isCaretRowShown = false
        isUseSoftWraps = true
        isRightMarginShown = false
        additionalLinesCount = 0
        additionalColumnsCount = 0
    }

    val editorComponent = editor.component.apply {
        alignmentX = Component.LEFT_ALIGNMENT
    }

    // Toolbar panel (can add buttons as needed)
    val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(16), 0)).apply {
        isOpaque = true
        background = JBColor.darkGray.darker().darker().darker()
        border = JBUI.Borders.empty(8, 0)
    }

    toolbar.add(JLabel(path))

    // Wrapper panel: toolbar on top, editor below
    val wrapper = JPanel(BorderLayout()).apply {
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
        add(toolbar, BorderLayout.NORTH)
        add(editorComponent, BorderLayout.CENTER)
    }

    // Calculate new height based on document content and soft wraps
    fun calcHeight(): Int {
        val logicalLines = document.lineCount.coerceAtLeast(1)
        val softWraps = editor.softWrapModel.registeredSoftWraps.size
        val visualLines = logicalLines + softWraps
        val fm = editorComponent.getFontMetrics(editorComponent.font)
        val lineHeight = fm.height
        val toolbarHeight = toolbar.preferredSize.height
        return toolbarHeight + (lineHeight * visualLines * 1.3).toInt()
    }

    // Update wrapper sizes and notify via callback
    fun updateHeight() {
        val newHeight = calcHeight()
        if (wrapper.preferredSize.height != newHeight) {
            val dim = Dimension(0, newHeight)
            wrapper.minimumSize = dim
            wrapper.preferredSize = dim
            wrapper.maximumSize = Dimension(Int.MAX_VALUE, newHeight)
            heightCallback(newHeight)
            wrapper.revalidate()
            wrapper.repaint()
        }
    }

    // Initial layout
    updateHeight()

    // Listen for resize to recalc height
    editorComponent.addComponentListener(object : ComponentAdapter() {
        override fun componentResized(e: ComponentEvent) {
            updateHeight()
        }
    })

    // Listen for document changes to recalc height after edits
    document.addDocumentListener(object : com.intellij.openapi.editor.event.DocumentListener {
        override fun documentChanged(event: com.intellij.openapi.editor.event.DocumentEvent) {
            SwingUtilities.invokeLater { updateHeight() }
        }
    })

    return wrapper
}
