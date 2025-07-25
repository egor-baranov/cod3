package com.github.egorbaranov.cod3.ui.components


import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffDialogHints
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.JComponent

/**
 * Creates an embedded inline diff panel between two VirtualFiles.
 * @param project IntelliJ Project (also used as Disposable)
 * @param leftFile first file in diff
 * @param rightFile second file in diff
 * @return a JComponent displaying an inline diff between the two files
 */
fun createInlineDiffComponent(
    project: Project,
    leftFile: VirtualFile,
    rightFile: VirtualFile
): JComponent {
    val factory = DiffContentFactory.getInstance()
    val content1 = factory.create(project, leftFile)
    val content2 = factory.create(project, rightFile)

    val title1 = leftFile.name
    val title2 = rightFile.name
    val request = SimpleDiffRequest("Diff: \$title1 vs \$title2", content1, content2, title1, title2)

    // Create inline diff panel (embedded, no frame)
    // Pass project as both context and Disposable owner
    val panel = DiffManager.getInstance().createRequestPanel(project, project, null)
    // Set request with default dialog hints
    panel.setRequest(request, DiffDialogHints.DEFAULT)
    return panel.component
}