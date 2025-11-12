package com.github.egorbaranov.cod3.koog

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.io.write
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.regex.PatternSyntaxException
import kotlin.io.path.relativeTo

@Suppress("unused")
@LLMDescription("Tools that allow the Koog agent to read and modify files inside the current project.")
class KoogIdeToolset(
    private val project: Project
) : ToolSet {

    private val basePath: Path = Paths.get(
        project.basePath ?: error("Project base path is not available for Koog tools.")
    )

    @Tool(customName = "write_file")
    @LLMDescription("Create or overwrite a file relative to the project root with the provided content.")
    fun writeFile(
        @LLMDescription("Path relative to the project root, e.g. src/main/App.kt")
        path: String,
        @LLMDescription("Full file contents that should be written.")
        content: String
    ): String {
        val target = resolvePath(path)
        Files.createDirectories(target.parent)

        lateinit var createdFile: VirtualFile

        WriteCommandAction.runWriteCommandAction(project) {
            val parentDir = VfsUtil.createDirectoryIfMissing(target.parent.toString())
                ?: error("Unable to create parent directory ${target.parent}")
            val fileName = target.fileName.toString()
            createdFile = parentDir.findChild(fileName) ?: parentDir.createChildData(this, fileName)
            VfsUtil.saveText(createdFile, content)
        }

        ApplicationManager.getApplication().invokeLater {
            FileEditorManager.getInstance(project).openFile(createdFile, true)
        }

        return "Wrote ${target.relativeTo(basePath)} (${content.length} chars)."
    }

    @Tool(customName = "edit_file")
    @LLMDescription("Apply a full-file edit by replacing the contents of the provided project file.")
    fun editFile(
        @LLMDescription("Path relative to the project root") path: String,
        @LLMDescription("Full file contents that should replace the existing file.") edits: String
    ): String {
        val target = resolvePath(path)
        require(Files.exists(target)) { "File $path does not exist." }

        val virtualFile = LocalFileSystem.getInstance()
            .refreshAndFindFileByPath(target.toString())
            ?: error("Unable to resolve $path inside the Virtual File System.")

        val tempOriginal = kotlin.io.path.createTempFile(prefix = "cod3-edit-", suffix = target.fileName.toString())
        tempOriginal.write(Files.readAllBytes(target))

        val originalVFile = LocalFileSystem.getInstance()
            .refreshAndFindFileByPath(tempOriginal.toString())
            ?: error("Unable to create temporary file for diff.")

        WriteCommandAction.runWriteCommandAction(project) {
            VfsUtil.saveText(virtualFile, edits)
        }

        ApplicationManager.getApplication().invokeLater {
            FileEditorManager.getInstance(project).openFile(virtualFile, true)
        }

        return "Updated ${target.relativeTo(basePath)}."
    }

    @Tool(customName = "find")
    @LLMDescription("Find project files by glob or regex pattern.")
    fun findFiles(
        @LLMDescription("Glob pattern (default) or prefix with `r:` to send a regex.") pattern: String
    ): String {
        val (patternBody, isRegex) = if (pattern.startsWith("r:")) pattern.drop(2) to true else pattern to false
        val regex = try {
            if (isRegex) patternBody.toRegex(RegexOption.IGNORE_CASE) else globToRegex(patternBody).toRegex(RegexOption.IGNORE_CASE)
        } catch (ex: PatternSyntaxException) {
            throw IllegalArgumentException("Invalid pattern: ${ex.message}", ex)
        }

        val matches = Files.walk(basePath).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .map { basePath.relativize(it).toString().replace(File.separatorChar, '/') }
                .filter { regex.containsMatchIn(it) }
                .sorted()
                .toList()
        }

        return buildString {
            append("Found ${matches.size} file(s) for ${if (isRegex) "regex" else "glob"} '$patternBody':")
            matches.forEach { append("\n - ").append(it) }
        }
    }

    @Tool(customName = "list_directory")
    @LLMDescription("List all files in a relative directory.")
    fun listDirectory(
        @LLMDescription("Relative directory path ('.' for root).") directory: String
    ): String {
        val dir = resolvePath(directory.ifBlank { "." }).toFile()
        require(dir.isDirectory) { "$directory is not a directory." }
        val children = dir.listFiles().orEmpty()
            .sortedBy { it.name.lowercase() }
            .joinToString("\n") { it.relativeTo(basePath.toFile()).path }
        return "Directory $directory contains:\n$children"
    }

    @Tool(customName = "grep_search")
    @LLMDescription("Search file contents for a regex pattern.")
    fun grepSearch(
        @LLMDescription("Regex pattern to look for.") pattern: String,
        @LLMDescription("Relative directory to search (defaults to project root).") root: String = ".",
        @LLMDescription("Search subdirectories (default true).") recursive: Boolean = true
    ): String {
        val regex = pattern.toRegex()
        val searchRoot = resolvePath(root).toFile()
        require(searchRoot.exists()) { "Path $root does not exist." }

        val matches = mutableListOf<String>()
        val files = if (recursive) {
            searchRoot.walkTopDown()
        } else {
            searchRoot.walkTopDown().maxDepth(1)
        }

        files.forEach { file ->
            if (file.isFile) {
                file.readLines().forEachIndexed { index, line ->
                    if (regex.containsMatchIn(line)) {
                        val rel = basePath.relativize(file.toPath()).toString().replace(File.separatorChar, '/')
                        matches += "$rel:${index + 1}: $line"
                    }
                }
            }
        }

        if (matches.isEmpty()) {
            return "No results for /$pattern/ under $root."
        }
        return "Matches for /$pattern/ under $root:\n${matches.joinToString("\n")}"
    }

    @Tool(customName = "view_file")
    @LLMDescription("Read a text file relative to the project root.")
    fun viewFile(
        @LLMDescription("Relative path to read.") path: String,
        @LLMDescription("Maximum number of characters to return.") maxChars: Int = 16_384
    ): String {
        val target = resolvePath(path)
        require(Files.exists(target)) { "File $path not found." }
        val content = Files.readString(target)
        return if (content.length <= maxChars) {
            "Contents of ${target.relativeTo(basePath)}:\n$content"
        } else {
            "Contents of ${target.relativeTo(basePath)} (truncated to $maxChars chars):\n${content.take(maxChars)}"
        }
    }

    @Tool(customName = "run_command")
    @LLMDescription("Placeholder command runner. Commands are not executed for safety, but the agent can describe intent.")
    fun runCommand(
        @LLMDescription("Command that would be executed inside the project root.") command: String
    ): String = "Command execution is disabled in Koog IDE toolset. Wanted to run: $command"

    private fun resolvePath(relative: String): Path {
        val normalized = basePath.resolve(relative.trim()).normalize()
        require(normalized.startsWith(basePath)) { "Path $relative escapes the project root." }
        return normalized
    }

    private fun globToRegex(glob: String): String {
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
}
