package com.github.egorbaranov.cod3.toolWindow

import com.github.egorbaranov.cod3.services.MyProjectService
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.PopupChooserBuilder
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class Cod3ToolWindowFactory : ToolWindowFactory {

    override val icon: Icon? = AllIcons.Actions.EnableNewUi
    override fun shouldBeAvailable(project: Project): Boolean = true

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
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

        // Example list of component names:
        val allComponents = listOf(
            "JButton", "JLabel", "JPanel",
            "JBTextField", "JBLabel", "JBPanel",
            "JBScrollPane", "JBList", "JBComboBox"
        )

        val messageContainer = JBPanel<JBPanel<*>>().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(8)
        }
        val scroll = JBScrollPane(messageContainer).apply {
            verticalScrollBar.unitIncrement = JBUI.scale(16)
        }

        val inputField = JBTextField().apply {
            emptyText.text = "Ask anything..."
        }
        val sendButton = JButton(AllIcons.Actions.PreviousOccurence).apply {
            preferredSize = Dimension(36, 36)
            addActionListener {
                val text = inputField.text.trim()
                if (text.isNotEmpty()) {
                    appendUserBubble(messageContainer, text)
                    inputField.text = ""
                    // simulate AI reply
                    val reply = service.getRandomNumber().toString()
                    appendAssistantBubble(messageContainer, "Cod3: $reply")
                    SwingUtilities.invokeLater {
                        scroll.verticalScrollBar.value = scroll.verticalScrollBar.maximum
                    }
                }
            }
        }

        // inline @-popup
        var lookupPopup: JBPopup? = null

        inputField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = checkPopup()
            override fun removeUpdate(e: DocumentEvent) = checkPopup()
            override fun changedUpdate(e: DocumentEvent) = checkPopup()

            private fun checkPopup() {
                println("check popup")
                val text = inputField.text
                val caret = inputField.caretPosition
                val at = text.lastIndexOf('@', caret - 1)
                if (text.endsWith("@")) {
                    val prefix = text.substring(at + 1, caret)
                    println("show popup")
                    showOrUpdatePopup(prefix, at)
                } else {
                    lookupPopup?.cancel()
                }
            }

            private fun showOrUpdatePopup(prefix: String, atPos: Int) {
                val matches = allComponents.filter { it.startsWith(prefix, ignoreCase = true) }
                if (matches.isEmpty()) {
                    lookupPopup?.cancel()
                    return
                }

                val list = JBList(listOf("Files & Folders", "Code", "Docs", "Git", "Web", "Recent Changes"))
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
                inputField.addKeyListener(object : KeyAdapter() {
                    override fun keyPressed(e: KeyEvent) {
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
                                replaceAt(inputField, sel, atPos)
                                lookupPopup?.cancel()
                                e.consume()
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
                        replaceAt(inputField, sel, atPos)
                        lookupPopup?.cancel()
                    })
                    .createPopup()

                val location = inputField.locationOnScreen
                val popupSize = lookupPopup.content.preferredSize
                lookupPopup.showInScreenCoordinates(inputField, Point(location.x, location.y - popupSize.height))

                list.requestFocusInWindow()
            }

            private fun replaceAt(field: JBTextField, sel: String, atPos: Int) {
                val t = field.text
                val c = field.caretPosition
                val before = t.substring(0, atPos + 1)
                val after = t.substring(c)
                field.text = "$before$sel $after"
                field.caretPosition = before.length + sel.length + 1
            }
        })

        val inputBar = JBPanel<JBPanel<*>>(BorderLayout(4, 0)).apply {
            border = JBUI.Borders.empty(4)
            add(inputField, BorderLayout.CENTER)
            add(sendButton, BorderLayout.EAST)
        }

        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(scroll, BorderLayout.CENTER)
            add(inputBar, BorderLayout.SOUTH)
        }
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

    private fun refresh(container: JPanel) {
        container.revalidate()
        container.repaint()
    }

    private enum class Alignment { LEFT, RIGHT }
}
