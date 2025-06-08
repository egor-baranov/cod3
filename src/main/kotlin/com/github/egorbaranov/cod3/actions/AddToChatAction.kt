package com.github.egorbaranov.cod3.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages

/**
 * This action will pop up a message with the currently selected text (if any).
 */
class AddToChatAction : AnAction(
    "Add To Chat",          // text shown to user
    "Updates selected code with instruction", // description
    null
) {
    override fun displayTextInToolbar(): Boolean = true

    override fun update(e: AnActionEvent) {
        // Enable only when an editor and a non‐empty selection exist
        val editor: Editor? = e.getData(CommonDataKeys.EDITOR)
        val hasSelection = editor?.selectionModel?.hasSelection() == true
        e.presentation.isEnabledAndVisible = hasSelection
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project: Project? = e.project
        val editor: Editor = e.getRequiredData(CommonDataKeys.EDITOR)
        val selected = editor.selectionModel.selectedText
        Messages.showInfoMessage(
            project,
            selected ?: "<no selection>",
            "You Selected"
        )
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }
}
