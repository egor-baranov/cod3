package com.github.egorbaranov.cod3.ui.components

import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.ui.EditorTextField
import java.awt.Component
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.Icon
import javax.swing.JPanel

class ReferencePopupProvider(val editorTextField: EditorTextField, val contextReferencePanel: JPanel) {

    private var lookupPopup: JBPopup? = null

    init {
        editorTextField.document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                if (event.oldLength > 0) {
                    removeTokenInlaysIfOverlapping(editorTextField, event.offset, event.oldLength)
                }
                checkPopup()
            }
        })
    }

    fun checkPopup(force: Boolean = false) {
        println("check popup")
        val text = editorTextField.text
        val caret = editorTextField.caretModel.currentCaret.offset
        val at = text.lastIndexOf('@', caret)
        if (text.endsWith("@") || force) {
            val prefix = if (force) "" else text.substring(at, caret)
            println("show popup")
            showOrUpdatePopup(editorTextField, contextReferencePanel, prefix, at, force)
        } else {
            lookupPopup?.cancel()
        }
    }

    private fun showOrUpdatePopup(
        editorTextField: EditorTextField,
        contextReferencePanel: JPanel,
        prefix: String,
        atPos: Int,
        force: Boolean = false
    ) {
        // Example list of component names:
        val allComponents = listOf(
            "JButton", "JLabel", "JPanel",
            "JBTextField", "JBLabel", "JBPanel",
            "JBScrollPane", "JBList", "JBComboBox"
        )

        // Filter by prefix unless force == true
        val matches = allComponents.filter { it.startsWith(prefix, ignoreCase = true) || force }
        if (matches.isEmpty()) {
            lookupPopup?.cancel()
            lookupPopup = null
            return
        }

        // Cancel any previous popup
        lookupPopup?.cancel()
        lookupPopup = null

        // Keep Backspace/Delete listener for token inlays as before
        // (Note: this may add multiple listeners if called repeatedly; consider managing a single listener
        // if necessary.)
        editorTextField.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                val editor = editorTextField.editor ?: return
                val caret = editor.caretModel.currentCaret

                when (e.keyCode) {
                    java.awt.event.KeyEvent.VK_BACK_SPACE -> {
                        val pos = caret.offset  // offset before deletion
                        if (pos > 0) {
                            removeTokenInlaysAtOffset(editor, pos - 1)
                        }
                    }

                    java.awt.event.KeyEvent.VK_DELETE -> {
                        val pos = caret.offset
                        removeTokenInlaysAtOffset(editor, pos)
                    }
                    // Arrow keys and Enter for popup navigation are handled by TemplatePopupComponent
                }
            }
        })

        // Prepare a templatesMap so that the "Code" group holds our filtered component names.
        // You can add other groups if you want, but here we only populate "Code".
        val templatesMap: Map<String, List<String>> = mapOf(
            "Code" to matches
        )

        // Show the two-level popup: first group selection, then template selection.
        // When the user picks a template (component name), we run the same insertion logic as before.
        TemplatePopupComponent.showGroupPopup(
            project = editorTextField.project,
            editorTextField = editorTextField,
            contextReferencePanel = contextReferencePanel,
            prefix = prefix,
            atPos = atPos,
            force = force,
            templatesMap = null
        ) { selected ->
            // onInsert callback: when user selects e.g. "JButton"
            if (force) {
                val label = RoundedTokenLabel(selected.text, selected.icon) {}.apply {
                    alignmentY = Component.CENTER_ALIGNMENT
                }
                contextReferencePanel.add(label)
                refresh(contextReferencePanel)
                label.onClose = {
                    contextReferencePanel.remove(label)
                    refresh(contextReferencePanel)
                }
            } else {
                replaceAt(editorTextField, "", atPos)
                addRoundedToken(
                    editorTextField,
                    editorTextField.document.text.length - 1,
                    selected.text,
                    selected.icon
                )
            }
        }

        // Note: TemplatePopupComponent shows its own JBPopup internally and does not return it here.
        // If you need to cancel it elsewhere, you can extend TemplatePopupComponent.showGroupPopup
        // to return the JBPopup it creates and assign lookupPopup = <that JBPopup>.
    }

    private fun replaceAt(field: EditorTextField, sel: String, atPos: Int) {
        val t = field.text
        val c = if (field.caretModel.allCarets.isEmpty()) 0 else field.caretModel.currentCaret.offset
        val before = t.substring(0, atPos)
        val after = t.substring(c)
        field.text = "$before$sel $after"
        field.caretModel.currentCaret.moveToOffset(before.length + sel.length + 1)
    }

    private fun refresh(container: JPanel) {
        container.revalidate()
        container.repaint()
    }

    fun addRoundedToken(editorTextField: EditorTextField, offset: Int, tokenText: String, icon: Icon?) {
        val editor = editorTextField.editor ?: return
        val inlayModel = editor.inlayModel
        inlayModel.addInlineElement(offset, true, RoundedTokenRenderer(tokenText, icon))
    }

    private fun removeTokenInlaysIfOverlapping(field: EditorTextField, offset: Int, length: Int) {
        val editor = field.editor ?: return
        // After deletion, offsets shift. We search a small range around the deletion point.
        // For simplicity, check range [offset, offset], but you might expand range if token can span multiple chars.
        removeTokenInlaysAtOffset(editor, offset)
    }

    /**
     * Remove any inline inlay whose renderer is RoundedTokenRenderer at the given offset.
     */
    private fun removeTokenInlaysAtOffset(editor: com.intellij.openapi.editor.Editor, offset: Int) {
        // Query inline inlays at this offset. Use getInlineElementsInRange(start, end).
        // Here we query a small range [offset, offset].
        val inlays = editor.inlayModel.getInlineElementsInRange(offset, offset)
        for (inlay in inlays) {
            val renderer = inlay.renderer
            if (renderer is RoundedTokenRenderer) {
                inlay.dispose()
            }
        }
    }
}