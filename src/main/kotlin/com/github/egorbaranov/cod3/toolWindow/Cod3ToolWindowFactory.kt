package com.github.egorbaranov.cod3.toolWindow

import com.github.egorbaranov.cod3.ui.components.RoundedTokenRenderer
import com.github.egorbaranov.cod3.services.MyProjectService
import com.github.egorbaranov.cod3.ui.Icons
import com.github.egorbaranov.cod3.ui.components.createComboBox
import com.github.egorbaranov.cod3.ui.components.createModelComboBox
import com.github.egorbaranov.cod3.ui.createResizableEditor
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.ui.popup.*
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.ui.EditorTextField
import com.intellij.ui.JBColor
import com.intellij.ui.components.IconLabelButton
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.geom.Area
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import javax.swing.*
import kotlin.text.startsWith

class Cod3ToolWindowFactory : ToolWindowFactory {

    override val icon: Icon? = AllIcons.Actions.EnableNewUi
    override fun shouldBeAvailable(project: Project): Boolean = true
    private var lookupPopup: JBPopup? = null
    private lateinit var editorTextField: EditorTextField
    private val list = JBList(listOf("Files & Folders", "Code", "Docs", "Git", "Web", "Recent Changes"))

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        editorTextField = createResizableEditor(project)
        toolWindow.title = "Cod3"

        // Title-bar actions
        val titleGroup = DefaultActionGroup().apply {
            add(object : AnAction("Add", "Add a new item", AllIcons.General.Add) {
                override fun actionPerformed(e: AnActionEvent) = println("Add clicked")
            })
            add(object : AnAction("History", "Show history", AllIcons.Actions.ListFiles) {
                override fun actionPerformed(e: AnActionEvent) = println("History clicked")
            })
            add(object : AnAction("Settings", "Open settings", AllIcons.General.Settings) {
                override fun actionPerformed(e: AnActionEvent) = println("Settings clicked")
            })
        }
        (toolWindow as? ToolWindowEx)
            ?.setTitleActions(*titleGroup.getChildren(null))

        val panel = SimpleToolWindowPanel(true, true).apply {
            setContent(createChatPanel(project))
        }

        toolWindow.contentManager.addContent(
            ContentFactory.getInstance()
                .createContent(panel, null, false)
        )
    }

    private fun createChatPanel(project: Project): JComponent {
        val service = project.service<MyProjectService>()

        val messageContainer = JBPanel<JBPanel<*>>().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(4)
        }

        val scroll = JBScrollPane(messageContainer).apply {
            verticalScrollBar.unitIncrement = JBUI.scale(16)
        }

        val sendButton = IconLabelButton(Icons.Send, {
            val text = editorTextField.text.trim()
            if (text.isNotEmpty()) {
                appendUserBubble(messageContainer, text)
                editorTextField.text = ""
                // simulate AI reply
                val reply = service.getRandomNumber().toString()
                appendAssistantBubble(messageContainer, "Cod3: $reply")
                SwingUtilities.invokeLater {
                    scroll.verticalScrollBar.value = scroll.verticalScrollBar.maximum
                }
            }
        }).apply {
            minimumSize = Dimension(24, 24)
            preferredSize = Dimension(24, 24)
            cursor = Cursor(Cursor.HAND_CURSOR)
        }

        // inline @-popup
        editorTextField.document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                if (event.oldLength > 0) {
                    removeTokenInlaysIfOverlapping(editorTextField, event.offset, event.oldLength)
                }
                checkPopup()
            }
        })

        val inputBar = object : JPanel(BorderLayout()) {
            init {
                isOpaque = false
            }

            override fun paintBorder(g: Graphics) {
                val g2 = g.create() as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = JBUI.CurrentTheme.Focus.defaultButtonColor()
                if (editorTextField.isFocusOwner) {
                    g2.stroke = BasicStroke(1.5F)
                } else {
                    g2.stroke = BasicStroke(0F)
                }
                g2.drawRoundRect(0, 0, width - 1, height - 1, 24, 24)
                g2.dispose()
            }

            override fun paintComponent(g: Graphics) {
                val g2 = g.create() as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

                val area = Area(Rectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat()))
                val roundedRect = RoundRectangle2D.Float(
                    0f,
                    0f,
                    width.toFloat(),
                    height.toFloat(),
                    24f,
                    24f
                )
                area.intersect(Area(roundedRect))

                g2.clip = area
                g2.color = JBColor.gray.darker().darker().darker().darker()
                g2.fill(area)

                super.paintComponent(g2)
                g2.dispose()
            }
        }.apply {
            border = JBUI.Borders.empty(4)

            val addContextButton = IconLabelButton(Icons.Mention, {checkPopup(true)}).apply {
                minimumSize = Dimension(24, 24)
                preferredSize = Dimension(24, 24)
                cursor = Cursor(Cursor.HAND_CURSOR)
            }

            val header = panel {
                row {
                    cell(addContextButton).align(AlignX.LEFT)
                }
            }.andTransparent().withBorder(JBUI.Borders.empty(0, 4))

            add(header, BorderLayout.NORTH)
            add(editorTextField, BorderLayout.CENTER)
            val comboBoxAction = createModelComboBox()

            val footer = panel {
                row {
                    cell(comboBoxAction)
                    cell(createComboBox(listOf("Agent", "Ask", "Manual", "Background")))
                    cell(sendButton).align(AlignX.RIGHT)
                }
            }.andTransparent().withBorder(JBUI.Borders.empty(0, 4))

            add(footer, BorderLayout.SOUTH)
        }

        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
            add(scroll, BorderLayout.CENTER)
            add(inputBar, BorderLayout.SOUTH)
        }
    }

    private fun checkPopup(force: Boolean = false) {
        println("check popup")
        val text = editorTextField.text
        val caret = editorTextField.caretModel.currentCaret.offset
        val at = text.lastIndexOf('@', caret)
        if (text.endsWith("@") || force) {
            val prefix = if (force) "" else text.substring(at, caret)
            println("show popup")
            showOrUpdatePopup(prefix, at, force)
        } else {
            lookupPopup?.cancel()
        }
    }

    private fun showOrUpdatePopup(prefix: String, atPos: Int, force: Boolean = false) {
        // Example list of component names:
        val allComponents = listOf(
            "JButton", "JLabel", "JPanel",
            "JBTextField", "JBLabel", "JBPanel",
            "JBScrollPane", "JBList", "JBComboBox"
        )

        val matches = allComponents.filter { it.startsWith(prefix, ignoreCase = true) || force }
        if (matches.isEmpty()) {
            lookupPopup?.cancel()
            return
        }

        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.selectedIndex = 0
        list.cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                val panel = JPanel(BorderLayout())
                panel.border = BorderFactory.createEmptyBorder(0, 8, 0, 8)
                panel.background = if (isSelected) list?.selectionBackground else list?.background

                val label = super.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus
                ) as JLabel
                label.border = BorderFactory.createEmptyBorder()
                label.isOpaque = false

                val leftIcon = JLabel(
                    listOf(
                        AllIcons.Nodes.Folder,
                        AllIcons.Actions.ShowCode,
                        AllIcons.FileTypes.Text,
                        AllIcons.Vcs.Branch,
                        AllIcons.Nodes.WebFolder,
                        AllIcons.General.Show
                    )[index]
                )
                val rightIcon = JLabel(AllIcons.Actions.Forward)

                leftIcon.border = BorderFactory.createEmptyBorder(0, 0, 0, 8)
                rightIcon.border = BorderFactory.createEmptyBorder(0, 12, 0, 0)

                panel.add(leftIcon, BorderLayout.WEST)
                panel.add(label, BorderLayout.CENTER)
                panel.add(rightIcon, BorderLayout.EAST)

                return panel
            }
        }

        // Forward arrow and enter key presses to the list
        editorTextField.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                val editor = editorTextField.editor ?: return
                val caret = editor.caretModel.currentCaret

                when (e.keyCode) {
                    KeyEvent.VK_DOWN -> {
                        val next = (list.selectedIndex + 1).coerceAtMost(list.model.size - 1)
                        list.selectedIndex = next
                        list.ensureIndexIsVisible(next)
                        e.consume()
                    }

                    KeyEvent.VK_UP -> {
                        val prev = (list.selectedIndex - 1).coerceAtLeast(0)
                        list.selectedIndex = prev
                        list.ensureIndexIsVisible(prev)
                        e.consume()
                    }

                    KeyEvent.VK_ENTER -> {
                        val sel = list.selectedValue ?: return
                        replaceAt(editorTextField, sel, atPos)
                        lookupPopup?.cancel()
                        e.consume()
                    }

                    KeyEvent.VK_BACK_SPACE -> {
                        val pos = caret.offset  // offset before deletion
                        if (pos > 0) {
                            // Check inlay immediately before this offset
                            removeTokenInlaysAtOffset(editor, pos - 1)
                        }
                    }

                    KeyEvent.VK_DELETE -> {
                        val pos = caret.offset  // offset of deletion
                        // Check inlay at this offset
                        removeTokenInlaysAtOffset(editor, pos)
                    }
                }
            }
        })

        lookupPopup?.cancel()
        lookupPopup = PopupChooserBuilder(list)
            .setMovable(false)
            .setResizable(false)
            .setRequestFocus(true)
            .setItemChosenCallback(Runnable {
                val sel = list.selectedValue ?: return@Runnable
                replaceAt(editorTextField, "", atPos)
                addRoundedToken(editorTextField, editorTextField.document.text.length - 1, "@${sel}")
                lookupPopup?.cancel()
            })
            .createPopup()

        val location = editorTextField.locationOnScreen
        val popupSize = lookupPopup?.content?.preferredSize
        lookupPopup?.showInScreenCoordinates(editorTextField, Point(location.x, location.y - (popupSize?.height ?: 0)))
        list.requestFocusInWindow()
    }

    private fun appendUserBubble(container: JPanel, text: String) {
        val bubble = createBubble(text, JBColor.LIGHT_GRAY)
        container.add(wrapHorizontal(bubble, Alignment.RIGHT))
        refresh(container)
    }

    private fun appendAssistantBubble(container: JPanel, text: String) {
        val bubble = createBubble(text, UIUtil.getPanelBackground())
        container.add(wrapHorizontal(bubble, Alignment.LEFT))
        refresh(container)
    }

    private fun createBubble(text: String, bg: Color): JComponent {
        val label = JLabel("<html>${text.replace("\n", "<br>")}</html>").apply {
            border = JBUI.Borders.empty(8, 12)
        }
        return object : JPanel(BorderLayout()) {
            init {
                isOpaque = false
                background = bg
                add(label, BorderLayout.CENTER)
                maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
            }

            override fun paintComponent(g: Graphics) {
                val g2 = g.create() as Graphics2D
                try {
                    g2.color = background
                    g2.setRenderingHint(
                        RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON
                    )
                    g2.fillRoundRect(0, 0, width, height, 16, 16)
                } finally {
                    g2.dispose()
                }
                super.paintComponent(g)
            }
        }
    }

    private fun wrapHorizontal(comp: JComponent, alignment: Alignment): JComponent {
        val wrapper = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            isOpaque = false
            border = JBUI.Borders.emptyTop(4)
        }
        when (alignment) {
            Alignment.RIGHT -> {
                wrapper.add(Box.createHorizontalGlue())
                wrapper.add(comp)
            }

            Alignment.LEFT -> {
                wrapper.add(comp)
                wrapper.add(Box.createHorizontalGlue())
            }
        }
        return wrapper
    }

    fun addRoundedToken(editorTextField: EditorTextField, offset: Int, tokenText: String) {
        val editor = editorTextField.editor ?: return
        val inlayModel = editor.inlayModel
        inlayModel.addInlineElement(offset, true, RoundedTokenRenderer(tokenText))
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

    private fun replaceAt(field: EditorTextField, sel: String, atPos: Int) {
        val t = field.text
        val c = field.caretModel.currentCaret.offset
        val before = t.substring(0, atPos)
        val after = t.substring(c)
        field.text = "$before$sel $after"
        field.caretModel.currentCaret.moveToOffset(before.length + sel.length + 1)
    }

    private fun refresh(container: JPanel) {
        container.revalidate()
        container.repaint()
    }

    private enum class Alignment { LEFT, RIGHT }
}
