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
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.IconLabelButton
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import scaledBy
import java.awt.*
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
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
    private fun rebuildContentAndAdjustHeight() {
        val components = contentPanel.components

        val blocks = parseMarkdown(currentText)

        println("blocks: ${blocks.map { "${it.type}, ${it.text}" }}")
        if (blocks.size > components.size) {
            blocks.takeLast(blocks.size - components.size)
                .map { blockToComponent(blocks.last()) }
                .forEachIndexed { idx, it ->
                    if (idx < (blocks.size - components.size) - 1) {
                        contentPanel.add(Box.createVerticalStrut(JBUI.scale(8)))
                    }

                    contentPanel.add(it)
                }
        } else {
            val lastBlock = blocks.last()
            if (lastBlock.type == Block.BlockType.TEXT) {
                (components.last() as JTextPane).text = wrapTextStyles(lastBlock.text)
            } else {
                val lastEditor = createdEditors.last()
                println("set text to editor: $lastEditor: ${lastBlock.text}")

                WriteCommandAction.runWriteCommandAction(lastEditor.project) {
                    createdEditors.last().document.setText(lastBlock.text)
                }
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

    override fun removeNotify() {
        val factory = EditorFactory.getInstance()
        for (editor in createdEditors) {
            factory.releaseEditor(editor)
        }
        createdEditors.clear()
        super.removeNotify()
    }

    private fun parseMarkdown(markdown: String): List<Block> {
        var code = markdown.startsWith("```")
        val result = mutableListOf<Block>()

        println("blocks: ${markdown.split("```")}")

        for (block in markdown.split("```").filter { it.isNotEmpty() }) {
            result.add(
                Block(
                    block.trim().let {
                        if (code) it.lines().drop(1).joinToString("\n").removeSuffix("\n``")
                        else it
                    },
                    if (code) Block.BlockType.CODE else Block.BlockType.TEXT,
                    language = block.lines().firstOrNull()?.takeIf { code }).also { v ->
                        println("language: $v")
                }
            )
            code = !code
        }

        return result
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

        val editorComponent = editor.component
        editorComponent.alignmentX = Component.LEFT_ALIGNMENT

        // Create toolbar panel with buttons
        val toolbar = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(16), 0)).apply {
            isOpaque = true
            background = JBColor.darkGray.darker().darker().darker()
            border = JBUI.Borders.empty(8, 0)

            add(IconLabelButton(AllIcons.Actions.Execute.scaledBy(0.8)) { println("Apply clicked") }.apply {
                text = "Apply"
                cursor = Cursor(Cursor.HAND_CURSOR)
                minimumSize = preferredSize
            })
            add(IconLabelButton(AllIcons.Toolbar.AddSlot.scaledBy(0.8)) { println("Insert clicked") }.apply {
                text = "Insert"
                cursor = Cursor(Cursor.HAND_CURSOR)
                minimumSize = preferredSize
            })

            add(IconLabelButton(Icons.Clipboard) { println("Clipboard clicked") }.apply {
                cursor = Cursor(Cursor.HAND_CURSOR)
                minimumSize = preferredSize
            })
            add(IconLabelButton(AllIcons.Actions.More) { println("More clicked") }.apply {
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
}
