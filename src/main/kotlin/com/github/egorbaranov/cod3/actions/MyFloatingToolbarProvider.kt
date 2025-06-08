package com.github.egorbaranov.cod3.actions

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.toolbar.floating.FloatingToolbarProvider

/**
 * A FloatingToolbarProvider must override two abstract properties:
 *
 *   override val actionGroup: ActionGroup
 *   override val autoHideable: Boolean
 *
 * Plus any optional methods like isApplicable(...) if you want to control visibility.
 */
class MyFloatingToolbarProvider : FloatingToolbarProvider {

    /**
     * Return the ActionGroup that this provider renders as a floating toolbar.
     * We look up the group by the same ID you gave it in plugin.xml: "MyFloatingActionGroup".
     */
    override val actionGroup: ActionGroup
        get() {
            val actionManager = ActionManager.getInstance()
            // Note: cast is safe because you registered this ID as a group in plugin.xml
            return actionManager.getAction("Cod3") as ActionGroup
        }

    /**
     * Whether this toolbar auto-hides on focus-loss / ESC, etc.
     * Usually you want this true so it disappears automatically.
     */
    override val autoHideable: Boolean
        get() = true

    /**
     * By default, the floating toolbar will show up whenever the IDE deems it “applicable.”
     * If you want finer control—e.g. only when there is an editor with a selection—override isApplicable().
     */
    override fun isApplicable(dataContext: DataContext): Boolean {
        // Only show if there’s an editor and some text selected:
        val editor: Editor? = CommonDataKeys.EDITOR.getData(dataContext)
        return editor?.selectionModel?.hasSelection() == true
    }

    // -- You do NOT need to override `register(...)` unless you want custom behavior when the toolbar is physically attached.
    //    FloatingToolbarProvider.register(...) has a default implementation, so you can omit it entirely.
    //
    // If you did want to customize the component setup, you could override:
    //     override fun register(
    //         dataContext: DataContext,
    //         component: FloatingToolbarComponent,
    //         parentDisposable: Disposable
    //     ) { ... }
    //
    // But in most cases you only need `actionGroup` and (optionally) `isApplicable(...)`.
}
