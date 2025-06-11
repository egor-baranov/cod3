package com.github.egorbaranov.cod3.ui

import PlaceholderTextField
import RoundedIconButton
import com.github.egorbaranov.cod3.ui.components.createComboBox
import com.github.egorbaranov.cod3.ui.components.createModelComboBox
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.CheckBox
import com.intellij.ui.components.IconLabelButton
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.IconUtil
import com.intellij.util.ui.CheckBox
import com.intellij.util.ui.JBUI
import java.awt.Point
import javax.swing.JTextField

import java.awt.*
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import java.awt.event.*

fun insertBlockInlayAboveSelection(editor: Editor, project: Project) {
    val sel = editor.selectionModel
    if (!sel.hasSelection()) return

    // 1) Dispose of any existing popup
    editor.getUserData(LAST_POPUP_KEY)?.let { oldPopup ->
        oldPopup.cancel()
        editor.putUserData(LAST_POPUP_KEY, null)
    }

    val offset = sel.selectionStart
    val visualPos = editor.offsetToVisualPosition(offset)
    val editorComponent = editor.contentComponent
    val lineHeight = editor.lineHeight

    val textField = createResizableEditor(project, 40, 0).apply {
        font = font.deriveFont(11f)
        minimumSize = Dimension()
    }
    val panel = JPanel(BorderLayout()).also {
        it.border = JBUI.Borders.empty(4)
    }

    val popup = JBPopupFactory.getInstance()
        .createComponentPopupBuilder(panel, panel)
        .setRequestFocus(true)
        .setFocusable(true)
        .setResizable(true)
        .setMovable(false)
        .setCancelOnClickOutside(false)
        .setCancelOnWindowDeactivation(false)
        .setCancelOnOtherWindowOpen(false)
        .setShowShadow(false)
        .setMinSize(Dimension(500, 70))
        .createPopup()

    panel.add(JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).also {
        it.border = JBUI.Borders.empty(4, 4, 2, 4)

        val rejectLabel = JLabel("Reject ⇧⌘⌫").apply {
            cursor = Cursor(Cursor.HAND_CURSOR)
            font = font.deriveFont(10f)
            foreground = JBColor.GRAY
        }

        val acceptLabel = JLabel("Accept ⌘⏎").apply {
            cursor = Cursor(Cursor.HAND_CURSOR)
            font = font.deriveFont(10f)
            foreground = JBColor.GRAY
        }

        fun wrapInRoundedPanel(label: JLabel, color: Color): JPanel {
            return object : JPanel(BorderLayout()) {
                init {
                    isOpaque = false
                    border = JBUI.Borders.empty(2, 6)
                    add(label, BorderLayout.CENTER)
                }

                override fun getPreferredSize(): Dimension {
                    val preferred = super.getPreferredSize()
                    return Dimension(preferred.width + 4, preferred.height)
                }

                override fun paintComponent(g: Graphics) {
                    val g2 = g as Graphics2D
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    g2.color = color
                    g2.fillRoundRect(0, 0, width, height, 12, 12)
                }
            }
        }

// Add them to your container
        it.add(wrapInRoundedPanel(rejectLabel, JBColor.background()))
        it.add(Box.createHorizontalStrut(2))
        it.add(wrapInRoundedPanel(acceptLabel, JBColor.border()))
        it.add(Box.createHorizontalStrut(2))

        it.add(JLabel(AllIcons.Actions.Close).also {
            it.cursor = Cursor(Cursor.HAND_CURSOR)
            it.addMouseListener(
                object: MouseListener {
                    override fun mouseClicked(e: MouseEvent?) { popup.cancel() }

                    override fun mousePressed(e: MouseEvent?) { popup.cancel() }

                    override fun mouseReleased(e: MouseEvent?) {}

                    override fun mouseEntered(e: MouseEvent?) {}

                    override fun mouseExited(e: MouseEvent?) {}
                }
            )
        })
    }, BorderLayout.NORTH)
    panel.add(textField, BorderLayout.CENTER)

    val actionPanel = JPanel(BorderLayout()).also { p ->
        p.border = JBUI.Borders.empty(2)

        val comboBox = createModelComboBox().apply {
//            preferredSize = JBUI.size(160, 20)  // smaller combo box width & height
//            font = font.deriveFont(font.size2D * 0.85f)
        }

        val scaledIcon = IconUtil.scale(Icons.Send, null, 0.8f)

        val applyButton = IconLabelButton(scaledIcon, {}).apply {
            cursor = Cursor(Cursor.HAND_CURSOR)
            preferredSize = Dimension(24, 24)
        }

        val modeComboBox = createComboBox(
            listOf(
                "Edit selection",
                "Edit full file",
                "Quick question",
                "Send to chat"
            )
        ).apply {
//            preferredSize = JBUI.size(150, 20)  // smaller combo box width & height
//            font = font.deriveFont(font.size2D * 0.85f)
        }
        p.add(comboBox, BorderLayout.WEST)

        p.add(
            JPanel(BorderLayout()).also { right ->
                right.add(modeComboBox, BorderLayout.WEST)
                right.add(Box.createHorizontalStrut(2), BorderLayout.CENTER)
                right.add(applyButton, BorderLayout.EAST)
            },
            BorderLayout.EAST
        )
    }
    panel.add(actionPanel, BorderLayout.SOUTH)

    // Position updater
    val updatePopupPosition = {
        val basePoint = editor.visualPositionToXY(visualPos)
        val popupPoint = Point(0, basePoint.y - lineHeight - 56)
        popup.setLocation(RelativePoint(editorComponent, popupPoint).screenPoint)
    }

    // Listen to scroll events and update popup position
    val listener = VisibleAreaListener { _: VisibleAreaEvent ->
        updatePopupPosition()
    }

    editor.scrollingModel.addVisibleAreaListener(listener)
    Disposer.register(popup) {
        editor.scrollingModel.removeVisibleAreaListener(listener)
    }

    // Show the popup and store it
    val highlighter = highlightSelectedLinesWithHintColor(editor)
    popup.show(RelativePoint(editorComponent, Point(0, 0))) // Initial dummy location
    updatePopupPosition()

    editor.putUserData(LAST_POPUP_KEY, popup)
    editor.getUserData(LAST_INLAY_KEY)?.let { inlay ->
        Disposer.dispose(inlay)
        editor.putUserData(LAST_INLAY_KEY, null)
    }

    val inlayModel = editor.inlayModel
    val blockInlay = inlayModel.addBlockElement(
        offset,
        true,
        true,
        0,
        HintRenderer("Your hint here")
    )

    if (blockInlay != null) {
        editor.putUserData(LAST_INLAY_KEY, blockInlay)
    }

    Disposer.register(popup) {
        editor.scrollingModel.removeVisibleAreaListener(listener)
        highlighter?.let {
            editor.markupModel.removeHighlighter(it)
        }
        blockInlay?.let { Disposer.dispose(it) }
    }
}

// Key to store and clean up the popup
val LAST_POPUP_KEY = Key.create<JBPopup>("com.github.egorbaranov.cod3.lastPopup")
private val LAST_INLAY_KEY = Key.create<com.intellij.openapi.editor.Inlay<*>>("com.github.egorbaranov.cod3.lastInlay")
