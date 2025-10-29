package com.github.egorbaranov.cod3.toolWindow.chat

import com.github.egorbaranov.cod3.toolWindow.ToolCall
import com.github.egorbaranov.cod3.ui.components.createEditorComponent
import com.github.egorbaranov.cod3.ui.components.createInlineDiffComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findDocument
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import java.io.File
import java.nio.file.Paths
import java.util.regex.PatternSyntaxException
import kotlin.io.FileWalkDirection
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities

internal class ToolCallProcessor {

    fun process(project: Project, toolCall: ToolCall, container: JPanel): String? {
        val name = toolCall.name ?: return null
        val args = toolCall.arguments ?: emptyMap()

        return when (name) {
            "run_command" -> {
                val command = args["command"] ?: error("missing command")
                "Successfully run command=$command"
            }

            "write_file" -> handleWriteFile(project, args, container)
            "edit_file" -> handleEditFile(project, args, container)
            "find" -> handleFind(project, args, container)
            "codebase_search" -> handleCodebaseSearch(args)
            "list_directory" -> handleListDirectory(project, args)
            "grep_search" -> handleGrep(args)
            "view_code_item" -> handleViewCodeItem(args)
            "view_file" -> handleViewFile(project, args)
            else -> null
        }
    }

    private fun handleWriteFile(project: Project, args: Map<String, String>, container: JPanel): String {
        val pathString = args["path"] ?: error("missing path")
        val content = args["content"] ?: error("missing content")

        val path = Paths.get(project.basePath!!)
            .resolve(pathString.trimStart('/', '\\'))
            .toAbsolutePath()
        val parentPath = path.parent.toAbsolutePath().toString()
        val fileName = path.fileName.toString()

        File(parentPath).apply { if (!exists()) mkdirs() }

        lateinit var createdVFile: VirtualFile

        WriteCommandAction.runWriteCommandAction(project) {
            val parentVDir = LocalFileSystem.getInstance()
                .refreshAndFindFileByPath(parentPath)
                ?: error("Could not find or create VFS directory: $parentPath")

            createdVFile = parentVDir.findChild(fileName)
                ?: parentVDir.createChildData(this, fileName)
            VfsUtil.saveText(createdVFile, content)
        }

        ApplicationManager.getApplication().invokeLater {
            FileEditorManager.getInstance(project).openFile(createdVFile, true)
        }

        SwingUtilities.invokeLater {
            val editorPreview = createEditorComponent(
                createdVFile.findDocument()!!,
                createdVFile.extension,
                path = pathString
            ) {}
            container.addChatBubble(JBPanel<JBPanel<*>>(VerticalLayout(JBUI.scale(8))).apply {
                isOpaque = false
                add(editorPreview)
            })
            refreshPanel(container)
        }

        return "Successfully wrote file in path=$pathString"
    }

    private fun handleEditFile(project: Project, args: Map<String, String>, container: JPanel): String {
        val pathString = args["path"] ?: error("missing path")
        val newContent = args["edits"] ?: error("missing edits")

        val filePath = Paths.get(project.basePath!!)
            .resolve(pathString.trimStart('/', '\\'))
            .toAbsolutePath()
        val file = File(filePath.toString())
        if (!file.exists()) error("file not found: $pathString")

        val vFile = LocalFileSystem.getInstance()
            .refreshAndFindFileByPath(filePath.toString())
            ?: error("VFS file not found: $filePath")

        val tempOriginal = File.createTempFile("orig", null)
        tempOriginal.writeBytes(vFile.contentsToByteArray())
        val origVFile = LocalFileSystem.getInstance()
            .refreshAndFindFileByPath(tempOriginal.absolutePath)
            ?: error("temp VFS file not found")

        WriteCommandAction.runWriteCommandAction(project) {
            VfsUtil.saveText(vFile, newContent)
        }

        LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath.toString())

        SwingUtilities.invokeLater {
            container.addChatBubble(JBPanel<JBPanel<*>>(VerticalLayout(JBUI.scale(8))).apply {
                isOpaque = false
                add(createInlineDiffComponent(project, origVFile, vFile))
            })
            refreshPanel(container)
        }

        return "Successfully edited $pathString"
    }

    private fun handleFind(project: Project, args: Map<String, String>, container: JPanel): String {
        val rawPattern = args["pattern"] ?: error("missing pattern")

        fun globToRegex(glob: String): String {
            val sb = StringBuilder("^")
            var i = 0
            while (i < glob.length) {
                when (val c = glob[i]) {
                    '*' -> sb.append(".*")
                    '?' -> sb.append('.')
                    '[' -> {
                        val j = glob.indexOf(']', i + 1).takeIf { it > i } ?: i
                        sb.append(glob.substring(i, j + 1))
                        i = j
                    }
                    '\\' -> {
                        if (i + 1 < glob.length) {
                            sb.append("\\\\").append(glob[i + 1])
                            i++
                        } else sb.append("\\\\")
                    }
                    else -> {
                        if ("\\.[]{}()+-^$|".contains(c)) sb.append('\\')
                        sb.append(c)
                    }
                }
                i++
            }
            sb.append('$')
            return sb.toString()
        }

        val (patternBody, isRegex) = if (rawPattern.startsWith("r:")) {
            rawPattern.drop(2) to true
        } else rawPattern to false

        val regexString = if (isRegex) patternBody else globToRegex(patternBody)
        val regex = try {
            regexString.toRegex(RegexOption.IGNORE_CASE)
        } catch (ex: PatternSyntaxException) {
            error("Invalid pattern syntax: ${ex.message}")
        }

        val basePath = project.basePath ?: error("missing Project basePath")
        val baseDir = Paths.get(basePath)
        val matches = File(basePath).walk()
            .filter { it.isFile }
            .map { file ->
                baseDir.relativize(file.toPath()).toString().replace("\\", "/")
            }
            .filter { regex.containsMatchIn(it) }
            .toList()

        container.addChatBubble(JBPanel<JBPanel<*>>(VerticalLayout(JBUI.scale(8))).apply {
            isOpaque = false
            matches.forEach { add(JLabel(it)) }
        })
        refreshPanel(container)

        return "Successfully found ${matches.size} result(s) for ${if (isRegex) "regex" else "glob"} /$rawPattern/:\n" +
            matches.joinToString("\n") { " - /$it" }
    }

    private fun handleCodebaseSearch(args: Map<String, String>): String {
        val query = args["query"] ?: error("missing query")
        val topK = args["top_k"]?.toIntOrNull() ?: 5
        return "Successfully searched codebase for '$query' (top $topK) and found results: "
    }

    private fun handleListDirectory(project: Project, args: Map<String, String>): String {
        val dirArg = args["directory"].orEmpty()
        val projectBasePath = project.basePath ?: error("Project base path not available")
        val dir = File(projectBasePath, dirArg).canonicalFile
        if (!dir.isDirectory) error("$dir is not a directory")

        val children = dir.listFiles() ?: emptyArray()
        return "Successfully listed directory '${dir.relativeTo(File(projectBasePath))}' with contents:\n" +
            children.joinToString("\n") { it.relativeTo(File(projectBasePath)).toString() }
    }

    private fun handleGrep(args: Map<String, String>): String {
        val pattern = args["pattern"] ?: error("missing pattern")
        val path = args["path"] ?: error("missing path")
        val recursive = args["recursive"]?.toBoolean() ?: true
        val regex = pattern.toRegex()
        val direction = if (recursive) FileWalkDirection.TOP_DOWN else FileWalkDirection.BOTTOM_UP

        File(path).walk(direction).forEach { file ->
            if (file.isFile) {
                file.readLines().forEachIndexed { idx, line ->
                    if (regex.containsMatchIn(line)) {
                        println("${file.path}:${idx + 1}: $line")
                    }
                }
            }
        }

        return "Successfully provided grep search results for '$pattern' in '$path' (recursive=$recursive):"
    }

    private fun handleViewCodeItem(args: Map<String, String>): String {
        val item = args["item_name"] ?: error("missing item_name")
        val path = args["path"] ?: error("missing path")
        val snippet = File(path).useLines { lines ->
            lines.dropWhile { !it.contains("fun $item") && !it.contains("class $item") }
                .takeWhile { !it.startsWith("}") }
                .joinToString("\n")
        }
        return "Successfully viewed code item $item in $path:\n$snippet"
    }

    private fun handleViewFile(project: Project, args: Map<String, String>): String {
        val path = args["path"] ?: error("missing path")
        val filePath = Paths.get(project.basePath!!)
            .resolve(path.trimStart('/', '\\'))
            .toAbsolutePath()
        val file = File(filePath.toString())
        if (!file.exists()) error("file not found: $path")
        return "Successfully viewed contents of $path:\n${file.readText()}"
    }
}
