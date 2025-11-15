package com.github.egorbaranov.cod3.ui.components

import com.github.egorbaranov.cod3.ui.Icons
import com.github.egorbaranov.cod3.util.wrapTextStyles
import com.intellij.icons.AllIcons
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.IconLabelButton
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import scaledBy
import java.awt.*
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.datatransfer.StringSelection
import javax.swing.*
import javax.swing.event.HyperlinkListener
import javax.swing.text.DefaultCaret

class ChatBubble(
    initialText: String,
    bg: Color,
    assistant: Boolean = false
) : JPanel(BorderLayout()) {

    private val createdEditors = mutableListOf<Editor>()
    private val isAssistant: Boolean = assistant
    private var currentText: String = initialText
    private val contentPanel: JPanel

    data class Block(
        val text: String,
        val type: BlockType,
        val language: String? = null
    ) {
        enum class BlockType {
            TEXT, CODE
        }
    }

    init {
        isOpaque = false
        background = bg

        // Initialize contentPanel
        contentPanel = JPanel(VerticalLayout(JBUI.scale(8))).apply {
            border = JBUI.Borders.empty(8, if (isAssistant) 4 else 12)
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
        }

        // Populate initial content and adjust height
        SwingUtilities.invokeLater {
            rebuildContentAndAdjustHeight()
        }
        add(contentPanel, BorderLayout.CENTER)

        if (isAssistant && false) {
            add(createAssistantToolbar(), BorderLayout.SOUTH)
        }
    }

    /**
     * Updates the bubble's text content, rebuilding components accordingly.
     */
    fun updateText(newText: String, forceReplace: Boolean = false) {
        if (!forceReplace) {
            if (newText == currentText || newText.length < currentText.length) return
        } else if (newText == currentText) {
            return
        }
        currentText = newText
        SwingUtilities.invokeLater {
            rebuildContentAndAdjustHeight()
        }
        revalidate()
        repaint()
    }

    /**
     * Rebuilds contentPanel based on currentText and adjusts height.
     */
    private fun rebuildContentAndAdjustHeight(fullRefresh: Boolean = false) {
        val components = contentPanel.components

        val blocks = parseMarkdown(currentText)

        if (fullRefresh) {
            contentPanel.removeAll()
            disposeEditors()
            blocks.forEachIndexed { index, block ->
                contentPanel.add(blockToComponent(block))
                if (index < blocks.lastIndex) {
                    contentPanel.add(Box.createVerticalStrut(JBUI.scale(8)))
                }
            }
            contentPanel.revalidate()
            contentPanel.repaint()
            return
        }

        println("blocks: ${blocks.map { "${it.type}, ${it.text}" }}")
        if (blocks.size > components.size) {
            blocks.takeLast(blocks.size - components.size)
                .map { blockToComponent(it) }
                .forEachIndexed { idx, component ->
                    if (idx < (blocks.size - components.size) - 1) {
                        contentPanel.add(Box.createVerticalStrut(JBUI.scale(8)))
                    }
                    contentPanel.add(component)
                }
        } else {
            val lastBlock = blocks.last()
            if (lastBlock.type == Block.BlockType.TEXT) {
                (components.last() as JTextPane).text = wrapTextStyles(lastBlock.text)
            } else if (createdEditors.isNotEmpty()) {
                WriteCommandAction.runWriteCommandAction(createdEditors.last().project) {
                    createdEditors.last().document.setText(lastBlock.text)
                }
            } else {
                rebuildContentAndAdjustHeight(fullRefresh = true)
                return
            }
        }
    }

    /**
     * Creates the assistant toolbar (like/dislike/etc) for assistant bubbles
     */
    private fun createAssistantToolbar(): JComponent {
        return JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply {
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
        }
    }

    private fun updateHeight(contentHeight: Int) {
        // contentHeight includes all child component heights and spacing
        val paddedHeight = contentHeight + JBUI.scale(16) // 8px top + 8px bottom padding
        val currentPref = preferredSize
//        preferredSize = Dimension(currentPref.width, paddedHeight)
//        minimumSize = Dimension(currentPref.width, paddedHeight)
//        maximumSize = Dimension(Int.MAX_VALUE, paddedHeight)

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

    override fun addNotify() {
        super.addNotify()
        SwingUtilities.invokeLater { rebuildContentAndAdjustHeight(fullRefresh = true) }
    }

    override fun removeNotify() {
        disposeEditors()
        super.removeNotify()
    }

    private fun disposeEditors() {
        if (createdEditors.isEmpty()) return
        val factory = EditorFactory.getInstance()
        createdEditors.forEach { factory.releaseEditor(it) }
        createdEditors.clear()
    }

    private fun parseMarkdown(markdown: String): List<Block> {
        if (!markdown.contains("```")) {
            return listOf(Block(markdown, Block.BlockType.TEXT))
        }

        val blocks = mutableListOf<Block>()
        var cursor = 0
        val length = markdown.length

        while (cursor < length) {
            val fenceStart = markdown.indexOf("```", cursor)
            if (fenceStart == -1) {
                val tail = markdown.substring(cursor)
                if (tail.isNotEmpty()) {
                    blocks.add(Block(tail, Block.BlockType.TEXT))
                }
                break
            }

            if (fenceStart > cursor) {
                val textSegment = markdown.substring(cursor, fenceStart)
                if (textSegment.isNotEmpty()) {
                    blocks.add(Block(textSegment, Block.BlockType.TEXT))
                }
            }

            var language: String? = null
            var bodyStart = fenceStart + 3
            if (bodyStart >= length) break

            if (markdown[bodyStart] != '\n' && markdown[bodyStart] != '\r') {
                var langEnd = bodyStart
                while (langEnd < length && markdown[langEnd] != '\n' && markdown[langEnd] != '\r') {
                    langEnd++
                }
                if (langEnd > bodyStart) {
                    language = markdown.substring(bodyStart, langEnd).trim().takeIf { it.isNotEmpty() }
                    bodyStart = langEnd
                }
            }

            if (bodyStart < length && markdown[bodyStart] == '\r') bodyStart++
            if (bodyStart < length && markdown[bodyStart] == '\n') bodyStart++

            val fenceEnd = markdown.indexOf("```", bodyStart)
            if (fenceEnd == -1) {
                val remaining = markdown.substring(fenceStart)
                blocks.add(Block(remaining, Block.BlockType.TEXT))
                break
            }

            val codeBody = markdown.substring(bodyStart, fenceEnd)
            blocks.add(Block(codeBody, Block.BlockType.CODE, language))
            cursor = fenceEnd + 3
        }

        return blocks
    }

    private fun blockToComponent(block: Block): JComponent {
        return if (block.type == Block.BlockType.TEXT) {
            createLabelComponent(block.text)
        } else {
            createEditorComponent(block.text, block.language, {})
        }
    }

    private fun createLabelComponent(text: String): JTextPane {
        val textPane = JTextPane()
        textPane.putClientProperty(JTextPane.HONOR_DISPLAY_PROPERTIES, true)
//        textPane.addHyperlinkListener(listener)
        textPane.setContentType("text/html")
        textPane.isEditable = false
        textPane.text = wrapTextStyles(text)
        textPane.setOpaque(false)
        (textPane.caret as DefaultCaret).setUpdatePolicy(DefaultCaret.NEVER_UPDATE)
        return textPane
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

        val editorBackground = JBColor.LIGHT_GRAY
        val editorComponent = editor.component
        editorComponent.alignmentX = Component.LEFT_ALIGNMENT
        editorComponent.isOpaque = true
        editorComponent.background = editorBackground
        editor.contentComponent.isOpaque = true
        editor.contentComponent.background = editorBackground

        // Create toolbar panel with buttons
        val toolbar = RoundedPanel(editorBackground).apply {
            layout = FlowLayout(FlowLayout.RIGHT, JBUI.scale(12), 0)
            border = JBUI.Borders.empty(8, 12, 4, 12)

            add(IconLabelButton(Icons.Clipboard) {
                CopyPasteManager.getInstance().setContents(StringSelection(code))
            }.apply {
                cursor = Cursor(Cursor.HAND_CURSOR)
                minimumSize = preferredSize
                toolTipText = "Copy code"
            })
            add(IconLabelButton(AllIcons.Actions.More) { println("More clicked") }.apply {
                cursor = Cursor(Cursor.HAND_CURSOR)
                minimumSize = preferredSize
            })
        }

        // Wrapper panel to hold toolbar on top + editor below
        val wrapper = RoundedPanel(editorBackground).apply {
            layout = BorderLayout()
            alignmentX = Component.LEFT_ALIGNMENT
            border = JBUI.Borders.empty(4, 6, 8, 6)
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
            return toolbarHeight + (lineHeight * visualLines * 1.3).toInt()
        }

        fun updateHeightLocal() {
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

        updateHeightLocal()

        editorComponent.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                updateHeightLocal()
            }
        })

        document.addDocumentListener(object : com.intellij.openapi.editor.event.DocumentListener {
            override fun documentChanged(event: com.intellij.openapi.editor.event.DocumentEvent) {
                SwingUtilities.invokeLater {
                    updateHeightLocal()
                }
            }
        })

        return wrapper
    }

    private fun isDarkBackground(bg: Color): Boolean {
        val lum = 0.299 * bg.red + 0.587 * bg.green + 0.114 * bg.blue
        return lum < 128
    }
    private class RoundedPanel(
        private val fillColor: Color = UIUtil.getPanelBackground(),
        private val radius: Int = JBUI.scale(12)
    ) : JPanel() {

        init {
            isOpaque = false
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                g2.color = fillColor
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.fillRoundRect(0, 0, width, height, radius, radius)
            } finally {
                g2.dispose()
            }
            super.paintComponent(g)
        }
    }
}
