package com.github.egorbaranov.cod3.toolWindow

import com.github.egorbaranov.cod3.ui.components.RoundedTokenRenderer
import com.github.egorbaranov.cod3.services.MyProjectService
import com.github.egorbaranov.cod3.ui.Icons
import com.github.egorbaranov.cod3.ui.components.RoundedBorder
import com.github.egorbaranov.cod3.ui.components.RoundedTokenLabel
import com.github.egorbaranov.cod3.ui.components.ScrollableSpacedPanel
import com.github.egorbaranov.cod3.ui.components.TokenChip
import com.github.egorbaranov.cod3.ui.components.createComboBox
import com.github.egorbaranov.cod3.ui.components.createModelComboBox
import com.github.egorbaranov.cod3.ui.createResizableEditor
import com.intellij.icons.AllIcons
import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionButton
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
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import scaledBy
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
    private val list = JBList(listOf("Files & Folders", "Code", "Docs", "Git", "Web", "Recent Changes"))

    var chatIndex = 1

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.title = "Cod3"

        // Set up title-bar actions: "Add", "History", "Settings"
        val titleGroup = DefaultActionGroup().apply {
            add(object : AnAction("Add", "Add a new chat tab", AllIcons.General.Add.scaledBy(0.8)) {
                override fun actionPerformed(e: AnActionEvent) {
                    addChatTab(project, toolWindow)
                }
            })

            add(object : AnAction("History", "Show history", Icons.ChatList.scaledBy(1.15)) {
                override fun actionPerformed(e: AnActionEvent) {
                    addHistoryTab(project, toolWindow)
                }
            })

            add(object : AnAction("Settings", "Open settings", Icons.Settings.scaledBy(1.15)) {
                override fun actionPerformed(e: AnActionEvent) {
                    ShowSettingsUtilImpl.showSettingsDialog(project, "cod3.settings", "")
                }
            })
        }
        (toolWindow as? ToolWindowEx)
            ?.setTitleActions(*titleGroup.getChildren(null))

        // Add an initial tab:
        addChatTab(project, toolWindow)
    }

    private fun addChatTab(project: Project, toolWindow: ToolWindow) {
        // Create fresh per-tab components:
        val editorTextField: EditorTextField = createResizableEditor(project, minHeight = 64)
        val contextReferencePanel = ScrollableSpacedPanel(4).apply {
            isOpaque = false
            alignmentY = Component.CENTER_ALIGNMENT
        }

        // Build the chat panel, passing these fresh components:
        val panel = SimpleToolWindowPanel(true, true).apply {
            setContent(createChatPanel(project, editorTextField, contextReferencePanel))
        }

        val content: Content = ContentFactory.getInstance()
            .createContent(panel, "Chat ${chatIndex++}", /* isLockable= */ false)
            .apply {
                isCloseable = true
                setShouldDisposeContent(true)
            }

        toolWindow.contentManager.addContent(content)
        toolWindow.contentManager.setSelectedContent(content)
    }

    private fun addHistoryTab(project: Project, toolWindow: ToolWindow) {
        // If history tab also needs its own editor etc, create fresh instances as needed.
        val editorTextField: EditorTextField = createResizableEditor(project, minHeight = 64)
        val contextReferencePanel = ScrollableSpacedPanel(4).apply {
            isOpaque = false
            alignmentY = Component.CENTER_ALIGNMENT
        }
        val panel = SimpleToolWindowPanel(true, true).apply {
            setContent(createHistoryPanel(project))
        }
        val content: Content = ContentFactory.getInstance()
            .createContent(panel, "History", /* isLockable= */ false)
            .apply {
                isCloseable = true
                setShouldDisposeContent(true)
            }
        toolWindow.contentManager.addContent(content)
        toolWindow.contentManager.setSelectedContent(content)
    }

    private fun createHistoryPanel(project: Project): JPanel {
        val messageContainer = JBPanel<JBPanel<*>>().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(4)
        }

        val scroll = JBScrollPane(messageContainer).apply {
            verticalScrollBar.unitIncrement = JBUI.scale(16)
        }

        for (i in 1..chatIndex) {
            messageContainer.add(historyBubble("Chat $i"))
            messageContainer.add(Box.createVerticalStrut(8))
        }

        refresh(messageContainer)
        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
            add(scroll, BorderLayout.CENTER)
        }
    }


    private fun createChatPanel(project: Project, editorTextField: EditorTextField, contextReferencePanel: JPanel): JComponent {
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
                appendAssistantBubble(messageContainer, "class URLShortener(\n" +
                        "    private val baseUrl: String,\n" +
                        "    paths: MutableList<String>,\n" +
                        "    val indexSelector: (List<String>) -> String\n" +
                        ") {\n" +
                        "\n" +
                        "    private val paths = ArrayList(paths)2\n" +
                        "    private val idToUrl = ConcurrentHashMap<String, String>()\n" +
                        "    private val lock = ReentrantLock()\n" +
                        "    fun short(url: String): String = lock.withLock {\n" +
                        "        if (paths.isEmpty()) {\n" +
                        "            throw IllegalStateException(\"Paths exceeded\")\n" +
                        "        }\n" +
                        "\n" +
                        "        var randomElement: String\n" +
                        "        do {\n" +
                        "            randomElement = indexSelector(paths)\n" +
                        "        } while (idToUrl.containsKey(randomElement))\n" +
                        "\n" +
                        "        paths.remove(randomElement)\n" +
                        "        idToUrl[randomElement] = url\n" +
                        "\n" +
                        "        return baseUrl + randomElement\n" +
                        "    }f\n" +
                        "\n" +
                        "    fun unshort(url: String): String? {\n" +
                        "        if (!url.startsWith(baseUrl)) {\n" +
                        "            throw IllegalArgumentException(\"Url format is wrong\")")
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
                checkPopup(editorTextField, contextReferencePanel = contextReferencePanel)
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

            val addContextButton = IconLabelButton(Icons.Mention, {checkPopup(editorTextField, contextReferencePanel, true)}).apply {
                minimumSize = Dimension(24, 24)
                preferredSize = Dimension(24, 24)
                cursor = Cursor(Cursor.HAND_CURSOR)
            }

            val header = panel {
                row {
                    cell(addContextButton).align(AlignX.LEFT).gap(RightGap.SMALL)
                    cell(contextReferencePanel).align(Align.FILL)
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

    private fun checkPopup(editorTextField: EditorTextField, contextReferencePanel: JPanel, force: Boolean = false) {
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

    private fun showOrUpdatePopup(editorTextField: EditorTextField, contextReferencePanel: JPanel, prefix: String, atPos: Int, force: Boolean = false) {
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
                        Icons.Web,
                        Icons.History
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

                if (force) {
                    contextReferencePanel.add(RoundedTokenLabel("@$sel").apply {
                        alignmentY = Component.CENTER_ALIGNMENT
                    })
                } else {
                    replaceAt(editorTextField, "", atPos)
                    addRoundedToken(editorTextField, editorTextField.document.text.length - 1, "@${sel}")
                }

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
        val bubble = createBubble(text, UIUtil.getPanelBackground(), assistant = true)
        container.add(wrapHorizontal(bubble, Alignment.LEFT))
        refresh(container)
    }

    private fun historyBubble(text: String): JComponent {
        val label = JLabel("<html>${text.replace("\n", "<br>")}</html>")

        class RenameAction : AnAction("Rename", "Rename chat", Icons.Edit) {
            override fun actionPerformed(e: AnActionEvent) {
                // TODO: implement rename logic
                JOptionPane.showMessageDialog(null, "Rename clicked for: $text")
            }
        }
        class DeleteAction : AnAction("Delete", "Delete chat", Icons.Trash) {
            override fun actionPerformed(e: AnActionEvent) {
                // TODO: implement delete logic
                val result = JOptionPane.showConfirmDialog(null, "Delete this item?", "Confirm Delete", JOptionPane.YES_NO_OPTION)
                if (result == JOptionPane.YES_OPTION) {
                    // perform deletion
                }
            }
        }

        class MoreActionGroup : ActionGroup("More", true) {

            init {
                templatePresentation.icon = AllIcons.Actions.More
                templatePresentation.putClientProperty(ActionButton.HIDE_DROPDOWN_ICON, java.lang.Boolean.TRUE)
            }

            override fun getChildren(e: AnActionEvent?): Array<AnAction> {
                // Populate additional actions here
                return arrayOf(object : AnAction("Details") {
                    override fun actionPerformed(e: AnActionEvent) {
                        // TODO: show details
                        JOptionPane.showMessageDialog(null, "Details for: $text")
                    }
                }, object : AnAction("Copy") {
                    override fun actionPerformed(e: AnActionEvent) {
                        // TODO: copy action
//                        Toolkit.getDefaultToolkit().systemClipboard.setContents(
//                            StringSelection(text), null)
                    }
                })
            }
        }

        val actionGroup = DefaultActionGroup().apply {
            add(RenameAction())
            add(DeleteAction())
            add(MoreActionGroup())
        }

        val actionToolbar = ActionManager.getInstance()
            .createActionToolbar("HistoryBubbleToolbar", actionGroup, true)
            .apply {
                targetComponent = null
                component.isOpaque = false
                isReservePlaceAutoPopupIcon = false
            }

        val contentPanel = object : JPanel(BorderLayout()) {
            init {
                add(label, BorderLayout.CENTER)
                add(actionToolbar.component.apply {
                    border = JBUI.Borders.empty()
                }, BorderLayout.EAST)

                isOpaque = true
                background = JBColor.LIGHT_GRAY
                border = JBUI.Borders.empty(12, 20, 12, 8)

                cursor = Cursor(Cursor.HAND_CURSOR)
            }

            override fun paintComponent(g: Graphics) {
                val g2 = g.create() as Graphics2D
                try {
                    g2.setRenderingHint(
                        RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON
                    )
                    g2.color = JBColor.LIGHT_GRAY
                    g2.fillRoundRect(0, 0, width, height, 24, 24)
                } finally {
                    g2.dispose()
                }
            }

            override fun getMaximumSize(): Dimension {
                return Dimension(Int.MAX_VALUE, preferredSize.height)
            }
        }
        return contentPanel
    }

    private fun createBubble(text: String, bg: Color, assistant: Boolean = false): JComponent {
        val label = JLabel("<html>${text.replace("\n", "<br>")}</html>").apply {
            border = JBUI.Borders.empty(8, 12)
        }
        return object : JPanel(BorderLayout()) {
            init {
                isOpaque = false
                background = bg
                add(label, BorderLayout.CENTER)
                if (assistant) add(
                    JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply {
                        border = JBUI.Borders.empty(4, 0)

                        add(IconLabelButton(Icons.Like.scaledBy(1.1), {}).apply {
                            minimumSize = Dimension(24, 24)
                            preferredSize = Dimension(24, 24)
                            cursor = Cursor(Cursor.HAND_CURSOR)
                        })
                        add(Box.createHorizontalStrut(4))
                        add(IconLabelButton(Icons.Dislike.scaledBy(1.1), {}).apply {
                            minimumSize = Dimension(24, 24)
                            preferredSize = Dimension(24, 24)
                            cursor = Cursor(Cursor.HAND_CURSOR)
                        })
                        add(Box.createHorizontalStrut(4))
                        add(IconLabelButton(Icons.Clipboard.scaledBy(1.1), {}).apply {
                            minimumSize = Dimension(24, 24)
                            preferredSize = Dimension(24, 24)
                            cursor = Cursor(Cursor.HAND_CURSOR)
                        })
                        add(Box.createHorizontalStrut(4))
                        add(IconLabelButton(Icons.More.scaledBy(1.1), {}).apply {
                            minimumSize = Dimension(24, 24)
                            preferredSize = Dimension(24, 24)
                            cursor = Cursor(Cursor.HAND_CURSOR)
                        })
                    }, BorderLayout.SOUTH
                )
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

    private enum class Alignment { LEFT, RIGHT }
}
