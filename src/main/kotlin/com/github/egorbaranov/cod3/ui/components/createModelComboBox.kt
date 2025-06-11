package com.github.egorbaranov.cod3.ui.components

import com.github.egorbaranov.cod3.ui.Icons
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.actionSystem.ex.ComboBoxAction.ComboBoxButton
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
import java.awt.Cursor
import java.util.function.Consumer
import java.util.function.Supplier
import javax.swing.Box
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer

fun createComboBox(elements: List<String>) = createModelComboBox(
    elements.firstOrNull() ?: "...",
    listOf(
        Section(
            null,
            groups = elements.map {
                Section.Group("", true, listOf(it))
            }
        )
    )
)

fun createModelComboBox() = createModelComboBox(
    "Auto",
    listOf(
        Section(
            "Cloud Providers",
            groups = listOf(
                Section.Group(
                    "OpenAI",
                    false,
                    listOf(
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
                    Icons.OpenAI
                ),
                Section.Group(
                    "Custom OpenAI",
                    false,
                    listOf("Add.."),
                    Icons.Sparkle
                ),
                Section.Group(
                    "Google (Gemini)",
                    false,
                    listOf(
                        "Gemini-2.5-Pro Preview",
                        "Gemini-2.5-Flash Preview",
                        "Gemini-2.0-Pro-Flash Thinking",
                        "Gemini-2.0-Flash",
                        "Gemini-1.5-Pro"
                    ),
                    Icons.Google
                ),
                Section.Group(
                    "Anthropic",
                    false,
                    listOf("Claude"),
                    Icons.Anthropic
                )
            )
        ),
        Section(
            "Local Providers",
            groups = listOf(
                Section.Group("LLamaCPP", false, listOf("LlamaCPP"), Icons.Llama),
                Section.Group("Ollama", false, listOf("Ollama"), Icons.Ollama)
            )
        )
    )
)

fun createModelComboBox(default: String, sections: List<Section> = mutableListOf()) = object : ComboBoxAction() {

    fun createCustomComponent(place: String): JComponent {
        val presentation = templatePresentation
        presentation.setText(default)
        return createCustomComponent(presentation, place)
    }

    override fun createPopupActionGroup(button: JComponent): DefaultActionGroup {
        val presentation = (button as ComboBoxButton).presentation
        val actionGroup = DefaultActionGroup()

        for (provider in sections) {
            provider.title?.let { actionGroup.addSeparator(it) }

            for (group in provider.groups) {
                val openaiGroup = DefaultActionGroup.createPopupGroup { group.title }

//                if (!group.asElements) {
//                    actionGroup.add(
//                        createOpenAIModelAction(
//                            presentation,
//                            group.title
//                        )
//                    )
//                }

                openaiGroup.templatePresentation.icon = group.icon
                group.actions.orEmpty()
                    .forEach(Consumer { model: String ->
                        val action = createOpenAIModelAction(
                            presentation,
                            model
                        )
                        if (group.asElements) actionGroup.add(action) else openaiGroup.add(action)
                    })

                if (!group.asElements) {
                    actionGroup.add(openaiGroup)
                }
            }
        }

        return actionGroup
    }

    private fun createModelAction(
        label: String?,
        icon: Icon?,
        comboBoxPresentation: Presentation,
        onModelChanged: Runnable?
    ): AnAction {
        return object : DumbAwareAction(label, "", null) {
            override fun update(event: AnActionEvent) {
                val presentation = event.getPresentation()
                presentation.setEnabled(presentation.getText() != comboBoxPresentation.getText())
            }

            override fun actionPerformed(e: AnActionEvent) {
                if (onModelChanged != null) {
                    onModelChanged.run()
                }
            }

            override fun getActionUpdateThread(): ActionUpdateThread {
                return ActionUpdateThread.BGT
            }
        }
    }

    private fun createOpenAIModelAction(
        comboBoxPresentation: Presentation,
        label: String
    ): AnAction {
        return createModelAction(
            label,
            Icons.OpenAI,
            comboBoxPresentation,
            {

            })
    }

    override fun createActionPopup(
        group: DefaultActionGroup,
        context: DataContext,
        disposeCallback: Runnable?
    ): JBPopup {
        val popup: ListPopup = object : PopupFactoryImpl.ActionGroupPopup(
            null,
            group,
            context,
            false,
            false,
            true,
            false,
            null,
            -1,
            null,
            null,
            MenuItemPresentationFactory(),
            false
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

//                                    (value as? ActionItem)?.action?.let { action ->
//                                        updateSecondaryLabel(action)
//
//                                    }
                    }

                    private fun setupSecondaryLabel() {
                        secondaryLabel.apply {
                            font = JBUI.Fonts.toolbarSmallComboBoxFont()
                            border = JBUI.Borders.emptyLeft(8)
                            clear()
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

    override fun createCustomComponent(
        presentation: Presentation,
        place: String
    ): JComponent {
        val button = createComboBoxButton(presentation)
        button.setForeground(
            EditorColorsManager.getInstance().getGlobalScheme().getDefaultForeground()
        )
        button.setBorder(null)
        button.putClientProperty("JButton.backgroundColor", Color(0, 0, 0, 0))
        return button
    }
}.createCustomComponent(ActionPlaces.UNKNOWN)


data class Section(
    val title: String?,
    val groups: List<Group>
) {
    data class Group(
        val title: String,
        val asElements: Boolean = true,
        val actions: List<String>? = null,
        val icon: Icon? = null
    )
}