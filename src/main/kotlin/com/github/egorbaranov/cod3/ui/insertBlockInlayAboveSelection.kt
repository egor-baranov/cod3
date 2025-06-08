package com.github.egorbaranov.cod3.ui

import PlaceholderTextField
import RoundedIconButton
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
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.CheckBox
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
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

    val textField = PlaceholderTextField("Editing instructions... (@ for code / documentation)", 50)
    val panel = JPanel(BorderLayout()).also {
        it.border = JBUI.Borders.empty()
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
        .createPopup()

    panel.add(JPanel(FlowLayout(FlowLayout.RIGHT)).also {
        it.add(textField, BorderLayout.CENTER)

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

    val actionPanel = JPanel(BorderLayout()).also { p ->
        p.border = JBUI.Borders.empty(0, 2, 2, 2)

        val comboBox = JComboBox(
            arrayOf(
                "o4-mini",
                "o3-mini",
                "gpt-4o",
                "claude-4-sonnet",
                "o3",
                "gemini-2.5-pro-max",
                "gpt-4.1"
            )
        ).apply {
            preferredSize = JBUI.size(160, 24)  // smaller combo box width & height
            font = font.deriveFont(font.size2D * 0.95f)
        }

        val applyButton = JButton(AllIcons.Actions.PreviousOccurence).apply {
            preferredSize = Dimension(32, 32)
        }
        val modeComboBox = JComboBox(
            arrayOf(
                "Edit selection",
                "Edit full file",
                "Quick question",
                "Send to chat"
            )
        ).apply {
            preferredSize = JBUI.size(150, 24)  // smaller combo box width & height
            font = font.deriveFont(font.size2D * 0.95f)
        }
        p.add(modeComboBox, BorderLayout.WEST)

        p.add(
            JPanel(BorderLayout()).also { right ->
                right.add(comboBox, BorderLayout.WEST)
                right.add(Box.createHorizontalStrut(4), BorderLayout.CENTER)
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
}

// Key to store and clean up the popup
val LAST_POPUP_KEY = Key.create<JBPopup>("com.github.egorbaranov.cod3.lastPopup")
private val LAST_INLAY_KEY = Key.create<com.intellij.openapi.editor.Inlay<*>>("com.github.egorbaranov.cod3.lastInlay")
