package com.github.egorbaranov.cod3.ui.components

import com.github.egorbaranov.cod3.ui.Icons
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.PopupChooserBuilder
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.JBList
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Point
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.BorderFactory
import javax.swing.DefaultListCellRenderer
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListSelectionModel

/**
 * Component to show a two-level popup: first select a group, then select a template item within that group.
 * Usage:
 * TemplatePopupComponent.showGroupPopup(
 *     project,
 *     editorTextField,
 *     contextReferencePanel,
 *     prefix,
 *     atPos,
 *     force,
 *     templatesMap,
 *     itemIconMap,
 *     onInsert = { selected ->
 *         // your logic: replaceAt or add token, etc.
 *     }
 * )
 */
object TemplatePopupComponent {
    private val groupNames = listOf(
        "Files & Folders",
        "Code",
        "Docs",
        "Git",
        "Web",
        "Recent Changes"
    )

    // Mapping from group name to icon
    private val groupIcons: Map<String, Icon> = mapOf(
        "Files & Folders" to AllIcons.Nodes.Folder,
        "Code" to Icons.Code,
        "Docs" to AllIcons.Toolwindows.Documentation,
        "Git" to AllIcons.Vcs.Branch,
        "Web" to Icons.Web,
        "Recent Changes" to Icons.History
    )

    /**
     * Represents a template item with text and optional icon.
     */
    data class TemplateItem(
        val text: String,
        val icon: Icon? = null
    )

    // Example template items per group; replace or extend as needed. "Files & Folders" will be overridden dynamically.
    private val defaultTemplates: Map<String, List<TemplateItem>> = mapOf(
        "Code" to listOf(
            TemplateItem("for loop", Icons.Code),
            TemplateItem("if statement", Icons.Code),
            TemplateItem("class declaration", Icons.Code),
            TemplateItem("function stub", Icons.Code)
        ),
        "Docs" to listOf(
            TemplateItem("TODO comment", AllIcons.General.Show),
            TemplateItem("KDoc snippet", AllIcons.Toolwindows.Documentation),
            TemplateItem("License header", AllIcons.FileTypes.Text)
        ),
        "Git" to listOf(
            TemplateItem("commit message", AllIcons.Vcs.CommitNode),
            TemplateItem("checkout branch", AllIcons.Vcs.Branch),
            TemplateItem("merge branch", AllIcons.Vcs.Merge)
        ),
        "Web" to listOf(
            TemplateItem("<div>...</div>", Icons.Web),
            TemplateItem("<a href=...>", Icons.Web),
            TemplateItem("<img src=...>", Icons.Web)
        ),
        "Recent Changes" to listOf(
            TemplateItem("Change1", Icons.History),
            TemplateItem("Change2", Icons.History),
            TemplateItem("Change3", Icons.History)
        )
    )

    /**
     * Show the group selection popup, then on group chosen show template selection popup.
     * @param project: IntelliJ Project, used to fetch open files for "Files & Folders"
     * @param editorTextField: the EditorTextField to attach key listener if needed
     * @param contextReferencePanel: panel to add token if force == true
     * @param prefix: prefix filter for template items
     * @param atPos: position in editor for replacement/insertion
     * @param force: if true, indicates direct insertion into contextReferencePanel; else replace in editor
     * @param templatesMap: mapping from group name to list of TemplateItem; if null, defaultTemplates is used
     *                      Note: "Files & Folders" in templatesMap will be ignored in favor of open files
     * @param itemIconMap: optional mapping from group name to a map of item text to Icon for custom icons; if provided, overrides TemplateItem.icon
     * @param onInsert: called when a template is selected; receives the selected template string
     */
    fun showGroupPopup(
        project: Project,
        editorTextField: EditorTextField,
        contextReferencePanel: JPanel,
        prefix: String,
        atPos: Int,
        force: Boolean = false,
        templatesMap: Map<String, List<TemplateItem>>? = null,
        itemIconMap: Map<String, Map<String, Icon>>? = null,
        onInsert: (TemplateItem) -> Unit
    ) {
        val templates = templatesMap ?: defaultTemplates

        // First-level list: groups
        val groupList = JBList(groupNames)
        groupList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        groupList.selectedIndex = 0
        groupList.cellRenderer = object : DefaultListCellRenderer() {
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

                val groupName = value as String
                val leftIcon = JLabel(groupIcons[groupName])
                val rightIcon = JLabel(AllIcons.Actions.Forward)

                leftIcon.border = BorderFactory.createEmptyBorder(0, 0, 0, 8)
                rightIcon.border = BorderFactory.createEmptyBorder(0, 12, 0, 0)

                panel.add(leftIcon, BorderLayout.WEST)
                panel.add(label, BorderLayout.CENTER)
                panel.add(rightIcon, BorderLayout.EAST)

                return panel
            }
        }

        // Build and show group popup
        val groupPopup = PopupChooserBuilder(groupList)
            .setMovable(false)
            .setResizable(false)
            .setRequestFocus(true)
            .setItemChosenCallback(Runnable {
                val sel = groupList.selectedValue ?: return@Runnable
                // Open template popup
                val items = if (sel == "Files & Folders") getOpenFileItems(project) else templates[sel] ?: emptyList()
                showTemplatePopup(
                    group = sel,
                    editorTextField = editorTextField,
                    contextReferencePanel = contextReferencePanel,
                    prefix = prefix,
                    atPos = atPos,
                    force = force,
                    templates = items,
                    itemIconMap = itemIconMap?.get(sel),
                    onInsert = onInsert
                )
            })
            .createPopup()

        // Key navigation on groupList
        groupList.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_DOWN -> {
                        val next = (groupList.selectedIndex + 1).coerceAtMost(groupList.model.size - 1)
                        groupList.selectedIndex = next
                        groupList.ensureIndexIsVisible(next)
                        e.consume()
                    }
                    KeyEvent.VK_UP -> {
                        val prev = (groupList.selectedIndex - 1).coerceAtLeast(0)
                        groupList.selectedIndex = prev
                        groupList.ensureIndexIsVisible(prev)
                        e.consume()
                    }
                    KeyEvent.VK_ENTER -> {
                        val sel = groupList.selectedValue as? String ?: return
                        // Open template popup for this group
                        groupPopup.cancel()
                        val items = if (sel == "Files & Folders") getOpenFileItems(project) else templates[sel] ?: emptyList()
                        showTemplatePopup(
                            group = sel,
                            editorTextField = editorTextField,
                            contextReferencePanel = contextReferencePanel,
                            prefix = prefix,
                            atPos = atPos,
                            force = force,
                            templates = items,
                            itemIconMap = itemIconMap?.get(sel),
                            onInsert = onInsert
                        )
                        e.consume()
                    }
                }
            }
        })

        // Show above editorTextField
        val location = editorTextField.locationOnScreen
        val popupSize = groupPopup.content.preferredSize
        groupPopup.showInScreenCoordinates(editorTextField, Point(location.x, location.y - (popupSize.height)))
        groupList.requestFocusInWindow()
    }

    /**
     * Fetch open files in the project and return as TemplateItem list with file icons.
     */
    private fun getOpenFileItems(project: Project): List<TemplateItem> {
        val manager = FileEditorManager.getInstance(project)
        val openFiles = manager.openFiles
        return openFiles.map { file ->
            val name = file.name
            val icon = file.fileType.icon
            TemplateItem(name, icon)
        }
    }

    private fun showTemplatePopup(
        group: String,
        editorTextField: EditorTextField,
        contextReferencePanel: JPanel,
        prefix: String,
        atPos: Int,
        force: Boolean,
        templates: List<TemplateItem>,
        itemIconMap: Map<String, Icon>?,
        onInsert: (TemplateItem) -> Unit
    ) {
        // Filter templates by prefix if needed; if force==true, ignore prefix filter
        val filteredItems = if (prefix.isNotEmpty() && !force) {
            templates.filter { it.text.startsWith(prefix, ignoreCase = true) }
        } else {
            templates
        }
        if (filteredItems.isEmpty()) {
            return
        }

        val templateList = JBList(filteredItems)
        templateList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        templateList.selectedIndex = 0

        // Renderer: show custom icon (from itemIconMap or TemplateItem.icon), then text
        templateList.cellRenderer = object : DefaultListCellRenderer() {
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

                val item = value as TemplateItem
                val label = super.getListCellRendererComponent(
                    list, item.text, index, isSelected, cellHasFocus
                ) as JLabel
                label.border = BorderFactory.createEmptyBorder()
                label.isOpaque = false

                // Determine icon: first check itemIconMap, then TemplateItem.icon, then group icon fallback
                val icon = itemIconMap?.get(item.text) ?: item.icon ?: groupIcons[group]
                val leftIcon = JLabel(icon)
                leftIcon.border = BorderFactory.createEmptyBorder(0, 0, 0, 8)

                panel.add(leftIcon, BorderLayout.WEST)
                panel.add(label, BorderLayout.CENTER)
                return panel
            }
        }

        // Build and show template popup
        val templatePopup: JBPopup = PopupChooserBuilder(templateList)
            .setMovable(false)
            .setResizable(false)
            .setRequestFocus(true)
            .setItemChosenCallback(Runnable {
                val selItem = templateList.selectedValue as? TemplateItem ?: return@Runnable
                onInsert(selItem)
            })
            .createPopup()

        // Key navigation on templateList
        templateList.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_DOWN -> {
                        val next = (templateList.selectedIndex + 1).coerceAtMost(templateList.model.size - 1)
                        templateList.selectedIndex = next
                        templateList.ensureIndexIsVisible(next)
                        e.consume()
                    }
                    KeyEvent.VK_UP -> {
                        val prev = (templateList.selectedIndex - 1).coerceAtLeast(0)
                        templateList.selectedIndex = prev
                        templateList.ensureIndexIsVisible(prev)
                        e.consume()
                    }
                    KeyEvent.VK_ENTER -> {
                        val selItem = templateList.selectedValue ?: return
                        onInsert(selItem)
                        templatePopup.cancel()
                        e.consume()
                    }
                }
            }
        })

        // Show above editorTextField
        val location = editorTextField.locationOnScreen
        val popupSize = templatePopup.content.preferredSize
        templatePopup.showInScreenCoordinates(editorTextField, Point(location.x, location.y - (popupSize.height)))
        templateList.requestFocusInWindow()
    }
}
