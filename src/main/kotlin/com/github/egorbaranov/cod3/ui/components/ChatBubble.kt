package com.github.egorbaranov.cod3.ui.components

import com.github.egorbaranov.cod3.ui.Icons
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.IconLabelButton
import com.intellij.util.ui.JBUI
import scaledBy
import java.awt.*
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.*

class ChatBubble(
    text: String,
    bg: Color,
    assistant: Boolean = false
) : JPanel(BorderLayout()) {

    private val createdEditors = mutableListOf<Editor>()

    init {
        isOpaque = false
        background = bg

        val components = parseMarkdownContent(text) { newHeight ->
            updateHeight(newHeight)
        }

        val contentPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(8, if (assistant) 4 else 12)
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
        }

        components.forEachIndexed { idx, comp ->
            comp.alignmentX = Component.LEFT_ALIGNMENT
            contentPanel.add(comp)
            if (idx < components.size - 1) {
                contentPanel.add(Box.createVerticalStrut(JBUI.scale(8)))
            }
        }

        add(contentPanel, BorderLayout.CENTER)

        if (assistant) {
            add(
                JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply {
                    border = JBUI.Borders.empty(4, 0)
                    isOpaque = false

                    fun iconBtn(icon: Icon) = IconLabelButton(icon.scaledBy(1.1)) {}.apply {
                        minimumSize = Dimension(JBUI.scale(24), JBUI.scale(24))
                        preferredSize = Dimension(JBUI.scale(24), JBUI.scale(24))
                        cursor = Cursor(Cursor.HAND_CURSOR)
                    }

                    add(iconBtn(Icons.Like))
                    add(Box.createHorizontalStrut(JBUI.scale(4)))
                    add(iconBtn(Icons.Dislike))
                    add(Box.createHorizontalStrut(JBUI.scale(4)))
                    add(iconBtn(Icons.Clipboard))
                    add(Box.createHorizontalStrut(JBUI.scale(4)))
                    add(iconBtn(Icons.More))
                },
                BorderLayout.SOUTH
            )
        }

        maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
    }

    private fun updateHeight(newHeight: Int) {
        val currentPref = preferredSize
        val newPrefHeight = newHeight + JBUI.scale(16) // always use new height + padding

        preferredSize = Dimension(currentPref.width, newPrefHeight)
        minimumSize = Dimension(currentPref.width, newPrefHeight)
        maximumSize = Dimension(Int.MAX_VALUE, newPrefHeight)

        revalidate()
        repaint()
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.color = background
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.fillRoundRect(0, 0, width, height, JBUI.scale(16), JBUI.scale(16))
        } finally {
            g2.dispose()
        }
        super.paintComponent(g)
    }

    override fun removeNotify() {
        val factory = EditorFactory.getInstance()
        for (editor in createdEditors) {
            factory.releaseEditor(editor)
        }
        createdEditors.clear()
        super.removeNotify()
    }

    private fun parseMarkdownContent(markdown: String, heightCallback: (Int) -> Unit): List<JComponent> {
        val result = mutableListOf<JComponent>()
        val codeRegex = Regex("```(\\w+)?\\s*\\n([\\s\\S]*?)\\n?```", RegexOption.MULTILINE)
        var lastIndex = 0

        for (match in codeRegex.findAll(markdown)) {
            val langTag = match.groups[1]?.value?.trim()
            val codeBlock = match.groups[2]?.value?.trimEnd() ?: ""

            if (match.range.first > lastIndex) {
                val plainText = markdown.substring(lastIndex, match.range.first).trim()
                if (plainText.isNotEmpty()) {
                    result.add(createLabelComponent(plainText))
                }
            }

            result.add(createEditorComponent(codeBlock, langTag, heightCallback))
            lastIndex = match.range.last + 1
        }

        if (lastIndex < markdown.length) {
            val remainingText = markdown.substring(lastIndex).trim()
            if (remainingText.isNotEmpty()) {
                result.add(createLabelComponent(remainingText))
            }
        }

        return result
    }

    private fun createLabelComponent(text: String): JLabel {
        val escaped = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\n", "<br>")
        val html = "<html>$escaped</html>"

        return JLabel(html).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            foreground = if (isDarkBackground(background)) Color.WHITE else Color.BLACK

            val fm = getFontMetrics(font)
            val lines = text.count { it == '\n' } + 1
            val height = fm.height * lines + JBUI.scale(8)

            minimumSize = Dimension(0, height)
            preferredSize = Dimension(0, height)
            maximumSize = Dimension(Int.MAX_VALUE, height)
        }
    }

    private fun createEditorComponent(
        code: String,
        language: String?,
        heightCallback: (Int) -> Unit
    ): JComponent {
        val project = ProjectManager.getInstance().defaultProject

        val fileType = FileTypeManager.getInstance().registeredFileTypes.firstOrNull { type ->
            val lang = language?.lowercase()?.trim()
            type.name.lowercase() == lang || type.defaultExtension.lowercase() == lang
        } ?: PlainTextFileType.INSTANCE

        val document = EditorFactory.getInstance().createDocument(code)
        val editor = EditorFactory.getInstance().createEditor(document, project, fileType, true)
        createdEditors.add(editor)

        val editorEx = editor as EditorEx
        editorEx.isViewer = true
        editorEx.settings.apply {
            isLineNumbersShown = true
            isFoldingOutlineShown = false
            isCaretRowShown = false
            isUseSoftWraps = true
            isRightMarginShown = false
            additionalLinesCount = 0
            additionalColumnsCount = 0
        }

        val editorComponent = editor.component
        editorComponent.alignmentX = Component.LEFT_ALIGNMENT

        // Create toolbar panel with buttons
        val toolbar = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(16), 0)).apply {
            isOpaque = true
            background = JBColor.darkGray.darker().darker().darker()
            border = JBUI.Borders.empty(8, 0)

            add(IconLabelButton(AllIcons.Actions.Execute.scaledBy(0.8)) { println("Dislike clicked") }.apply {
                text = "Apply"
                cursor = Cursor(Cursor.HAND_CURSOR)
                minimumSize = preferredSize
            })
            // Example buttons, customize icons and actions as you like
            add(IconLabelButton(AllIcons.Toolbar.AddSlot.scaledBy(0.8)) { println("Like clicked") }.apply {
                text = "Insert"
                cursor = Cursor(Cursor.HAND_CURSOR)
                minimumSize = preferredSize
            })

            add(IconLabelButton(Icons.Clipboard) { println("Clipboard clicked") }.apply {
                cursor = Cursor(Cursor.HAND_CURSOR)
                minimumSize = preferredSize
            })
            add(IconLabelButton(AllIcons.Actions.More) { println("Clipboard clicked") }.apply {
                cursor = Cursor(Cursor.HAND_CURSOR)
                minimumSize = preferredSize
            })
        }

        // Wrapper panel to hold toolbar on top + editor below
        val wrapper = JPanel(BorderLayout()).apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            add(toolbar, BorderLayout.NORTH)
            add(editorComponent, BorderLayout.CENTER)
        }

        fun calcHeight(): Int {
            val doc = editor.document
            val logicalLines = doc.lineCount.coerceAtLeast(1)

            val softWrapModel = editor.softWrapModel
            val softWrapsCount: Int = softWrapModel.registeredSoftWraps.size
            val visualLines = logicalLines + softWrapsCount

            val fm = editorComponent.getFontMetrics(editorComponent.font)
            val lineHeight = fm.height
            val toolbarHeight = toolbar.preferredSize.height

            // Total height = toolbar height + editor lines height + padding
            return toolbarHeight + (lineHeight * visualLines * 1.5).toInt() + JBUI.scale(32)
        }

        fun updateHeight() {
            val newHeight = calcHeight()
            if (wrapper.preferredSize.height != newHeight) {
                wrapper.minimumSize = Dimension(0, newHeight)
                wrapper.preferredSize = Dimension(0, newHeight)
                wrapper.maximumSize = Dimension(Int.MAX_VALUE, newHeight)

                heightCallback(newHeight)

                wrapper.revalidate()
                wrapper.repaint()
            }
        }

        updateHeight()

        editorComponent.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                updateHeight()
            }
        })

        document.addDocumentListener(object : com.intellij.openapi.editor.event.DocumentListener {
            override fun documentChanged(event: com.intellij.openapi.editor.event.DocumentEvent) {
                SwingUtilities.invokeLater {
                    updateHeight()
                }
            }
        })

        return wrapper
    }

    private fun isDarkBackground(bg: Color): Boolean {
        val lum = 0.299 * bg.red + 0.587 * bg.green + 0.114 * bg.blue
        return lum < 128
    }
}
