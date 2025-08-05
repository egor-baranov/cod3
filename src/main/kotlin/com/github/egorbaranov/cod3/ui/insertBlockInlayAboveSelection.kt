package com.github.egorbaranov.cod3.ui

import com.github.egorbaranov.cod3.ui.components.ReferencePopupProvider
import com.github.egorbaranov.cod3.ui.components.ScrollableSpacedPanel
import com.github.egorbaranov.cod3.ui.components.createComboBox
import com.github.egorbaranov.cod3.ui.components.createModelComboBox
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorEmbeddedComponentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.IconLabelButton
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Box
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane

fun insertBlockInlayAboveSelection(editor: Editor, project: Project) {
    val sel = editor.selectionModel
    if (!sel.hasSelection()) return

    // Dispose existing popup
    editor.getUserData(LAST_POPUP_KEY)?.let { oldPopup ->
        oldPopup.cancel()
        editor.putUserData(LAST_POPUP_KEY, null)

        // Remove previous highlight on new selection
        editor.getUserData(LAST_HIGHLIGHT_KEY)?.let { oldHighlighter ->
            editor.markupModel.removeHighlighter(oldHighlighter)
            editor.putUserData(LAST_HIGHLIGHT_KEY, null)
        }
    }

    // Cleanup previous inlay & highlight
    editor.getUserData(LAST_INLAY_KEY)?.let { oldInlay ->
        Disposer.dispose(oldInlay)
        editor.putUserData(LAST_INLAY_KEY, null)
    }
    editor.getUserData(LAST_HIGHLIGHT_KEY)?.let { oldHighlighter ->
        editor.markupModel.removeHighlighter(oldHighlighter)
        editor.putUserData(LAST_HIGHLIGHT_KEY, null)
    }

    val offset = sel.selectionStart
    val visualPos = editor.offsetToVisualPosition(offset)
    val editorComponent = editor.contentComponent
    val lineHeight = editor.lineHeight

    // Input field and panel
    val textField = createResizableEditor(project, 40, 0) {
        println("send message: $it")
    }.apply { font = font.deriveFont(11f) }
    val panel = object : JPanel(BorderLayout()) {
        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = background
            g2.fillRoundRect(0, 0, width, height, 16, 16)
            super.paintComponent(g2)
            g2.dispose()
        }
    }.apply {
        border = JBUI.Borders.empty(4)
        minimumSize = Dimension(500, 106)
        preferredSize = Dimension(500, 106)
        background = JBColor.PanelBackground
    }

    // Top: context & actions
    val scrollPanel = ScrollableSpacedPanel(4).apply {
        isOpaque = false
        alignmentY = Component.CENTER_ALIGNMENT
    }
    val referenceProvider = ReferencePopupProvider(textField, scrollPanel)

    panel.add(JPanel(BorderLayout()).apply {
        border = JBUI.Borders.emptyBottom(4)
        isOpaque = false

        add(IconLabelButton(Icons.Mention) {
            referenceProvider.checkPopup(true)
        }.apply {
            minimumSize = Dimension(24, 24)
            preferredSize = Dimension(24, 24)
            cursor = Cursor(Cursor.HAND_CURSOR)
        }, BorderLayout.WEST)

        add(JBScrollPane(scrollPanel).apply {
            border = JBUI.Borders.empty(2, 0)
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_NEVER
            preferredSize = Dimension(100, 24)
        }, BorderLayout.CENTER)

        add(ScrollableSpacedPanel(1).apply {
            isOpaque = false
            alignmentY = Component.CENTER_ALIGNMENT

            fun actionLabel(text: String, color: Color): JPanel {
                val label = JLabel(text).apply {
                    cursor = Cursor(Cursor.HAND_CURSOR)
                    font = font.deriveFont(10f)
                    foreground = JBColor.GRAY
                }
                return object : JPanel(BorderLayout()) {
                    init {
                        isOpaque = false
                        border = JBUI.Borders.empty(2, 6)
                        add(label, BorderLayout.CENTER)
                    }

                    override fun paintComponent(g: Graphics) {
                        (g as Graphics2D).apply {
                            setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                            g.color = color
                            fillRoundRect(0, 0, width, height, 12, 12)
                        }
                    }
                }
            }

            add(actionLabel("Reject ⇧⌘⌫", JBColor.background()).also {
                it.addMouseListener(
                    object : MouseAdapter() {
                        override fun mouseClicked(e: MouseEvent?) {
                            editor.getUserData(LAST_INLAY_KEY)?.let { Disposer.dispose(it) }
                            editor.putUserData(LAST_INLAY_KEY, null)
                            editor.getUserData(LAST_HIGHLIGHT_KEY)?.let {
                                editor.markupModel.removeHighlighter(it)
                            }
                            editor.putUserData(LAST_HIGHLIGHT_KEY, null)
                        }
                    }
                )
            })
            add(Box.createHorizontalStrut(4))
            add(actionLabel("Accept ⌘⏎", JBColor.border()))
            add(Box.createHorizontalStrut(4))

            add(IconLabelButton(AllIcons.Actions.Close) {
                editor.getUserData(LAST_INLAY_KEY)?.let { Disposer.dispose(it) }
                editor.putUserData(LAST_INLAY_KEY, null)
                editor.getUserData(LAST_HIGHLIGHT_KEY)?.let {
                    editor.markupModel.removeHighlighter(it)
                }
                editor.putUserData(LAST_HIGHLIGHT_KEY, null)

            }.apply {
                minimumSize = Dimension(24, 24)
                preferredSize = Dimension(24, 24)
                maximumSize = Dimension(24, 24)
                cursor = Cursor(Cursor.HAND_CURSOR)
            })
        }, BorderLayout.EAST)
    }, BorderLayout.NORTH)

    panel.add(textField, BorderLayout.CENTER)

    textField.document.addDocumentListener(
        object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                val softWraps = textField.editor?.softWrapModel?.getSoftWrapsForRange(0, textField.text.length)?.size ?: 0
                panel.minimumSize = Dimension(
                    500,
                    maxOf(100 + (textField.editor?.lineHeight ?: 6) * (textField.document.lineCount + softWraps - 1), 110)
                )
                panel.preferredSize = Dimension(
                    500,
                    maxOf(100 + (textField.editor?.lineHeight ?: 6) * (textField.document.lineCount + softWraps - 1), 110)
                )
            }
        }
    )

    // Bottom: mode & send
    panel.add(JPanel(BorderLayout()).apply {
        border = JBUI.Borders.emptyTop(4)
        add(createModelComboBox(), BorderLayout.WEST)
        add(JPanel(BorderLayout()).apply {
            add(createComboBox(listOf("Edit selection", "Edit full file", "Quick question", "Send to chat")))
            add(IconLabelButton(IconUtil.scale(Icons.Send, null, 0.9f), {}).apply {
                minimumSize = Dimension(24, 24)
                preferredSize = Dimension(24, 24)
                cursor = Cursor(Cursor.HAND_CURSOR)
            })
        }, BorderLayout.EAST)
    }, BorderLayout.SOUTH)

    // Position updater
    val listener = VisibleAreaListener { _: VisibleAreaEvent ->
        val base = editor.visualPositionToXY(visualPos)
        panel.location = RelativePoint(editorComponent, Point(0, base.y - lineHeight - panel.height)).screenPoint
    }
    editor.scrollingModel.addVisibleAreaListener(listener)

    // Highlight selection
    val highlighter = highlightSelectedLinesWithHintColor(editor)
    editor.putUserData(LAST_HIGHLIGHT_KEY, highlighter)

    // Embed component
    val inlay = EditorEmbeddedComponentManager.getInstance().addComponent(
        editor as EditorEx, panel,
        EditorEmbeddedComponentManager.Properties(
            EditorEmbeddedComponentManager.ResizePolicy.none(), null, true, true, 0, offset
        )
    )
    inlay?.let {
        editor.putUserData(LAST_INLAY_KEY, it)
        Disposer.register(it as Disposable) { editor.scrollingModel.removeVisibleAreaListener(listener) }
    }
}

// keys for cleanup
val LAST_POPUP_KEY = Key.create<JBPopup>("com.github.egorbaranov.cod3.lastPopup")
private val LAST_INLAY_KEY = Key.create<com.intellij.openapi.editor.Inlay<*>>("com.github.egorbaranov.cod3.lastInlay")
private val LAST_HIGHLIGHT_KEY =
    Key.create<com.intellij.openapi.editor.markup.RangeHighlighter>("com.github.egorbaranov.cod3.lastHighlight")
