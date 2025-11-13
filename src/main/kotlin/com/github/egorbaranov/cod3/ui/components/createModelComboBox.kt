package com.github.egorbaranov.cod3.ui.components

import com.github.egorbaranov.cod3.koog.KoogModelCatalog
import com.github.egorbaranov.cod3.settings.PluginSettingsState
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.editor.colors.EditorColorsManager
import java.awt.Color
import javax.swing.JComponent

fun createModelComboBox(): JComponent {
    val settings = PluginSettingsState.getInstance()

    val action = object : ComboBoxAction() {
        override fun update(e: AnActionEvent) {
            val selected = KoogModelCatalog.resolveEntry(settings.koogModelId)
            e.presentation.text = selected.label
            e.presentation.icon = selected.provider.icon
        }

        override fun createPopupActionGroup(button: JComponent?): DefaultActionGroup {
            val presentation = (button as? ComboBoxButton)?.presentation ?: templatePresentation
            val grouped = KoogModelCatalog.availableModels.groupBy { it.provider }
            return DefaultActionGroup().apply {
                grouped.forEach { (provider, entries) ->
                    addSeparator(provider.displayName)
                    entries.forEach { entry ->
                        add(object : AnAction(entry.label, null, provider.icon) {
                            override fun actionPerformed(e: AnActionEvent) {
                                settings.koogModelId = entry.id
                                presentation.text = entry.label
                                presentation.icon = provider.icon
                            }

                            override fun update(e: AnActionEvent) {
                                e.presentation.isEnabled = settings.koogModelId != entry.id
                            }

                            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
                        })
                    }
                }
            }
        }

        override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
            val button = createComboBoxButton(presentation)
            button.foreground = EditorColorsManager.getInstance().globalScheme.defaultForeground
            button.border = null
            button.putClientProperty("JButton.backgroundColor", Color(0, 0, 0, 0))
            return button
        }
    }

    val initial = KoogModelCatalog.resolveEntry(settings.koogModelId)
    action.templatePresentation.text = initial.label
    action.templatePresentation.icon = initial.provider.icon
    return action.createCustomComponent(action.templatePresentation, ActionPlaces.UNKNOWN)
}
