package com.github.egorbaranov.cod3.actions

import com.github.egorbaranov.cod3.ui.insertBlockInlayAboveSelection
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor

/**
 * This action will pop up a message with the currently selected text (if any).
 */
class EditAction : AnAction(
    "Edit ⌘K",          // text shown to user
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
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val project = e.project ?: return

        insertBlockInlayAboveSelection(editor, project)
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }
}
