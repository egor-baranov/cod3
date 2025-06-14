package com.github.egorbaranov.cod3.ui.components

import com.github.egorbaranov.cod3.ui.Icons
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.actionSystem.impl.MenuItemPresentationFactory
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.ui.popup.list.PopupListElementRenderer
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import javax.swing.Box
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer

/**
 * Data classes representing sections and groups for the combo box.
 */
data class Section(
    val title: String?,
    val groups: List<Group>
) {
    data class Group(
        val title: String,
        val asElements: Boolean = true,
        val actions: List<String>? = null,  // list of model names
        val icon: Icon? = null
    )
}

/**
 * Creates a simple combo box with the given flat list of elements.
 * It wraps them into a single unnamed section with each element as a group element.
 */
fun createComboBox(elements: List<String>): JComponent {
    val defaultText = elements.firstOrNull() ?: "..."
    val section = Section(
        title = null,
        groups = elements.map { element ->
            Section.Group(
                title = "",
                asElements = true,
                actions = listOf(element),
                icon = null
            )
        }
    )
    return createModelComboBox(defaultText, listOf(section))
}

/**
 * Creates a “model” combo box with predefined sections.
 * Here’s an example default setup grouping cloud/local providers.
 */
fun createModelComboBox(): JComponent {
    // Default button text:
    val defaultText = "Auto"
    val sections = listOf(
        Section(
            title = "Cloud Providers",
            groups = listOf(
                Section.Group(
                    title = "OpenAI",
                    asElements = false,
                    actions = listOf(
                        "GPT-4.1",
                        "GPT-4.1 mini",
                        "GPT-4.1 nano",
                        "O3-Mini",
                        "O1-Preview",
                        "O1-Mini",
                        "GPT-4o",
                        "GPT-4o-mini",
                        "GPT-4o-0125 128k"
                    ),
                    icon = Icons.OpenAI
                ),
                Section.Group(
                    title = "Custom OpenAI",
                    asElements = true,
                    actions = listOf("Add..."),
                    icon = Icons.Sparkle
                ),
                Section.Group(
                    title = "Google (Gemini)",
                    asElements = false,
                    actions = listOf(
                        "Gemini-2.5-Pro Preview",
                        "Gemini-2.5-Flash Preview",
                        "Gemini-2.0-Pro-Flash Thinking",
                        "Gemini-2.0-Flash",
                        "Gemini-1.5-Pro"
                    ),
                    icon = Icons.Google
                ),
                Section.Group(
                    title = "Anthropic",
                    asElements = true,
                    actions = listOf("Claude"),
                    icon = Icons.Anthropic
                )
            )
        ),
        Section(
            title = "Local Providers",
            groups = listOf(
                Section.Group("LLamaCPP", asElements = true, actions = listOf("LlamaCPP"), icon = Icons.Llama),
                Section.Group("Ollama", asElements = true, actions = listOf("Ollama"), icon = Icons.Ollama)
            )
        )
    )
    return createModelComboBox(defaultText, sections)
}

/**
 * Core implementation: returns a JComponent (the combo box button) that shows the current selection,
 * and pops up a menu of sections/groups/models. When a model is selected, it updates the displayed text
 * (and icon) on the combo box.
 *
 * @param default Initial displayed text when nothing selected or default state.
 * @param sections List of Section, each with optional title and a list of Group.
 */
fun createModelComboBox(default: String, sections: List<Section>): JComponent {
    // Anonymous subclass of ComboBoxAction
    val comboBoxAction = object : ComboBoxAction() {
        // Store current selection if needed externally
        private var selectedModel: String = default

        /**
         * Create or update the presentation text/icon when a model is selected.
         */
        private fun createModelAction(
            label: String?,
            icon: Icon?,
            comboBoxPresentation: Presentation,
            onModelChanged: Runnable?
        ): AnAction {
            // Use DumbAwareAction so it works even when indexing (dumb mode)
            return object : DumbAwareAction(label, /* description */ null, icon) {
                override fun update(event: AnActionEvent) {
                    // Disable the action if this label is already selected
                    val presentation = event.presentation
                    presentation.isEnabled = (presentation.text != comboBoxPresentation.text)
                }

                override fun actionPerformed(e: AnActionEvent) {
                    // Update the combo box's displayed text and icon
                    comboBoxPresentation.text = label
                    comboBoxPresentation.icon = icon
                    // Store selection
                    selectedModel = label ?: default
                    // Invoke any callback
                    onModelChanged?.run()
                }

                override fun getActionUpdateThread(): ActionUpdateThread {
                    return ActionUpdateThread.BGT
                }
            }
        }

        /**
         * Convenience overload: when only label, icon, and callback needed.
         */
        private fun createModelAction(
            comboBoxPresentation: Presentation,
            label: String,
            icon: Icon?,
            onSelection: (String) -> Unit
        ): AnAction {
            return createModelAction(
                label,
                icon,
                comboBoxPresentation
            ) {
                onSelection(label)
            }
        }

        override fun createPopupActionGroup(button: JComponent): DefaultActionGroup {
            // The Presentation backing the combo button:
            val comboPresentation = (button as? ComboBoxButton)
                ?.presentation ?: templatePresentation

            val actionGroup = DefaultActionGroup()

            for (provider in sections) {
                // Add a separator with title if provided
                provider.title?.let { actionGroup.addSeparator(it) }

                for (group in provider.groups) {
                    if (group.asElements) {
                        // Direct elements: each action as separate entry
                        group.actions.orEmpty().forEach { modelName ->
                            // Pass group.icon so that selecting this model sets that icon on the combo
                            val action = createModelAction(comboPresentation, modelName, group.icon) { selected ->
                                // additional logic on selection, e.g. notify other parts of plugin
                            }
                            // Also set the icon on the menu item itself
                            action.templatePresentation.icon = group.icon
                            actionGroup.add(action)
                        }
                    } else {
                        // Create a submenu for this group
                        val subGroup = DefaultActionGroup.createPopupGroup { group.title }
                        subGroup.templatePresentation.icon = group.icon
                        group.actions.orEmpty().forEach { modelName ->
                            val action = createModelAction(comboPresentation, modelName, group.icon) { selected ->
                                // additional logic on selection
                            }
                            action.templatePresentation.icon = group.icon
                            subGroup.add(action)
                        }
                        actionGroup.add(subGroup)
                    }
                }
            }
            return actionGroup
        }

        override fun createActionPopup(
            group: DefaultActionGroup,
            context: DataContext,
            disposeCallback: Runnable?
        ): JBPopup {
            // Use the default ActionGroupPopup but override renderer to show secondary label if desired.
            val popup: ListPopup = object : PopupFactoryImpl.ActionGroupPopup(
                /* title = */ null,
                group,
                context,
                /* showNumbers */ false,
                /* enableAlpha */ false,
                /* requestFocus */ true,
                /* showSubmenuOnHover */ false,
                /* dataProvider */ null,
                /* maxRowCount */ -1,
                /* preselectCondition */ null,
                /* speedSearch */ null,
                MenuItemPresentationFactory(),
                /* honorActionMnemonics */ false
            ) {
                override fun getListElementRenderer(): ListCellRenderer<*> {
                    return object : PopupListElementRenderer<Any>(this) {
                        private lateinit var secondaryLabel: SimpleColoredComponent

                        override fun createLabel() {
                            super.createLabel()
                            secondaryLabel = SimpleColoredComponent()
                        }

                        override fun createItemComponent(): JComponent? {
                            createLabel()
                            val panel = JPanel(BorderLayout()).apply {
                                add(myTextLabel, BorderLayout.WEST)
                                add(secondaryLabel, BorderLayout.EAST)
                            }
                            myIconBar = createIconBar()
                            return layoutComponent(panel)
                        }

                        override fun createIconBar(): JComponent? {
                            return Box.createHorizontalBox().apply {
                                border = JBUI.Borders.emptyRight(JBUI.CurrentTheme.ActionsList.elementIconGap())
                                add(myIconLabel)
                            }
                        }

                        override fun customizeComponent(
                            list: JList<out Any>?,
                            value: Any?,
                            isSelected: Boolean
                        ) {
                            super.customizeComponent(list, value, isSelected)
                            setupSecondaryLabel()
                            // If you want to show extra info in secondaryLabel, implement here.
                        }

                        private fun setupSecondaryLabel() {
                            secondaryLabel.apply {
                                font = JBUI.Fonts.toolbarSmallComboBoxFont()
                                border = JBUI.Borders.emptyLeft(8)
                                clear()
                                // e.g., append("info") if needed
                            }
                        }
                    }
                }
            }
            if (disposeCallback != null) {
                popup.addListener(object : JBPopupListener {
                    override fun onClosed(event: LightweightWindowEvent) {
                        disposeCallback.run()
                    }
                })
            }
            popup.setShowSubmenuOnHover(true)
            return popup
        }

        override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
            // Create the combo button for the toolbar/panel:
            val button = createComboBoxButton(presentation)
            // Style adjustments:
            button.foreground = EditorColorsManager.getInstance().globalScheme.defaultForeground
            button.border = null
            // Transparent background if desired:
            button.putClientProperty("JButton.backgroundColor", Color(0, 0, 0, 0))
            return button
        }

        override fun update(e: AnActionEvent) {
            // Optional: any update logic for enabling/disabling the combo as a whole.
            super.update(e)
        }

        /**
         * If other parts of your plugin need to know the selected model, you can expose it:
         */
        fun getSelectedModel(): String = selectedModel
    }

    // Initialize the templatePresentation text/icon before creating component:
    val presentation = comboBoxAction.templatePresentation
    presentation.text = default
    // Optionally: set an initial icon (e.g. a default icon). Using null here means no icon initially.
    presentation.icon = null

    // Create and return the JComponent to place in your UI (toolbar, panel, etc.)
    return comboBoxAction.createCustomComponent(presentation, ActionPlaces.UNKNOWN)
}
