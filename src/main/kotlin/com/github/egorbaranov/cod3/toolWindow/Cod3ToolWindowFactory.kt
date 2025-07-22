package com.github.egorbaranov.cod3.toolWindow

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.egorbaranov.cod3.completions.CompletionsRequestService
import com.github.egorbaranov.cod3.completions.factory.AssistantMessage
import com.github.egorbaranov.cod3.completions.factory.OpenAIRequestFactory
import com.github.egorbaranov.cod3.completions.factory.UserMessage
import com.github.egorbaranov.cod3.ui.Icons
import com.github.egorbaranov.cod3.ui.components.*
import com.github.egorbaranov.cod3.ui.createResizableEditor
import com.intellij.icons.AllIcons
import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.ui.EditorTextField
import com.intellij.ui.JBColor
import com.intellij.ui.components.IconLabelButton
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import ee.carlrobert.llm.client.openai.completion.ErrorDetails
import ee.carlrobert.llm.client.openai.completion.OpenAIChatCompletionModel
import ee.carlrobert.llm.client.openai.completion.request.OpenAIChatCompletionStandardMessage
import ee.carlrobert.llm.completion.CompletionEventListener
import okhttp3.sse.EventSource
import scaledBy
import java.awt.*
import java.awt.Component.LEFT_ALIGNMENT
import java.awt.geom.Area
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.io.File
import java.nio.file.Paths
import javax.swing.*

class Cod3ToolWindowFactory : ToolWindowFactory {

    override fun shouldBeAvailable(project: Project): Boolean = true
    private var lookupPopup: JBPopup? = null
    private val list = JBList(listOf("Files & Folders", "Code", "Docs", "Git", "Web", "Recent Changes"))

    var chatQuantity = 1
    val messages = mutableMapOf<Int, MutableList<OpenAIChatCompletionStandardMessage>>()

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.title = "Cod3"

        // Set up title-bar actions: "Add", "History", "Settings"
        val titleGroup = DefaultActionGroup().apply {
            add(object : AnAction("Add", "Add a new chat tab", AllIcons.General.Add.scaledBy(0.8)) {
                override fun actionPerformed(e: AnActionEvent) {
                    addChatTab(project, toolWindow)
                }
            })

            add(object : AnAction("History", "Show history", Icons.ChatList.scaledBy(1.15)) {
                override fun actionPerformed(e: AnActionEvent) {
                    addHistoryTab(project, toolWindow)
                }
            })

            add(object : AnAction("Settings", "Open settings", Icons.Settings.scaledBy(1.15)) {
                override fun actionPerformed(e: AnActionEvent) {
                    ShowSettingsUtilImpl.showSettingsDialog(project, "cod3.settings", "")
                }
            })
        }
        (toolWindow as? ToolWindowEx)
            ?.setTitleActions(*titleGroup.getChildren(null))

        // Add an initial tab:
        addChatTab(project, toolWindow)
    }

    private fun addChatTab(project: Project, toolWindow: ToolWindow) {
        // Create fresh per-tab components:
        val editorTextField: EditorTextField = createResizableEditor(project, minHeight = 48)
        val contextReferencePanel = ScrollableSpacedPanel(4).apply {
            alignmentY = Component.CENTER_ALIGNMENT
        }

        val referencePopupProvider = ReferencePopupProvider(editorTextField, contextReferencePanel)
        // Build the chat panel, passing these fresh components:
        chatQuantity++

        val panel = SimpleToolWindowPanel(true, true).apply {
            setContent(createChatPanel(project, chatQuantity, referencePopupProvider))
        }

        val content: Content = ContentFactory.getInstance()
            .createContent(panel, "Chat $chatQuantity", /* isLockable= */ false)
            .apply {
                isCloseable = true
                setShouldDisposeContent(true)
            }

        toolWindow.contentManager.addContent(content)
        toolWindow.contentManager.setSelectedContent(content)
    }

    private fun addHistoryTab(project: Project, toolWindow: ToolWindow) {
        val cm = toolWindow.contentManager
        val existing = cm.contents.firstOrNull { it.getUserData(HISTORY_TAB_KEY) == true }
        if (existing != null) {
            cm.setSelectedContent(existing)
            return
        }

        val panel = SimpleToolWindowPanel(true, true).apply {
            setContent(createHistoryPanel(project))
        }
        val content: Content = ContentFactory.getInstance()
            .createContent(panel, "History", /* isLockable= */ false)
            .apply {
                isCloseable = true
                setShouldDisposeContent(true)
            }
        content.putUserData(HISTORY_TAB_KEY, true)
        toolWindow.contentManager.addContent(content)
        toolWindow.contentManager.setSelectedContent(content)
    }

    private fun createHistoryPanel(project: Project): JPanel {
        val messageContainer = JBPanel<JBPanel<*>>().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(4)
        }

        val scroll = JBScrollPane(messageContainer).apply {
            verticalScrollBar.unitIncrement = JBUI.scale(16)
        }

        for (i in 1..chatQuantity) {
            messageContainer.add(historyBubble("Chat $i"))
            messageContainer.add(Box.createVerticalStrut(8))
        }

        refresh(messageContainer)
        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
            add(scroll, BorderLayout.CENTER)
        }
    }


    private fun createChatPanel(
        project: Project,
        chatIndex: Int,
        referencePopupProvider: ReferencePopupProvider
    ): JComponent {
        val messageContainer = JBPanel<JBPanel<*>>(VerticalLayout(JBUI.scale(8))).apply {
            border = JBUI.Borders.empty(4)

        }

        val scroll = JBScrollPane(messageContainer).apply {
            verticalScrollBar.unitIncrement = JBUI.scale(16)
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        }

        val sendButton = IconLabelButton(Icons.Send, {
            sendMessage(
                project,
                chatIndex,
                referencePopupProvider.editorTextField.text.trim(),
                referencePopupProvider,
                messageContainer,
                scroll
            )
        }).apply {
            minimumSize = Dimension(24, 24)
            preferredSize = Dimension(24, 24)
            cursor = Cursor(Cursor.HAND_CURSOR)
        }

        val inputBar = object : JPanel(BorderLayout()) {
            init {
                isOpaque = false
            }

            override fun paintBorder(g: Graphics) {
                val g2 = g.create() as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = JBUI.CurrentTheme.Focus.defaultButtonColor()
                if (referencePopupProvider.editorTextField.isFocusOwner) {
                    g2.stroke = BasicStroke(1.5F)
                } else {
                    g2.stroke = BasicStroke(0F)
                }
                g2.drawRoundRect(0, 0, width - 1, height - 1, 24, 24)
                g2.dispose()
            }

            override fun paintComponent(g: Graphics) {
                val g2 = g.create() as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

                val area = Area(Rectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat()))
                val roundedRect = RoundRectangle2D.Float(
                    0f,
                    0f,
                    width.toFloat(),
                    height.toFloat(),
                    24f,
                    24f
                )
                area.intersect(Area(roundedRect))

                g2.clip = area
                g2.color = JBColor.gray.darker().darker().darker().darker()
                g2.fill(area)

                super.paintComponent(g2)
                g2.dispose()
            }
        }.apply {
            border = JBUI.Borders.empty(4)

            val addContextButton = IconLabelButton(Icons.Mention, {
                referencePopupProvider.checkPopup(true)
            }).apply {
                minimumSize = Dimension(24, 24)
                preferredSize = Dimension(24, 24)
                cursor = Cursor(Cursor.HAND_CURSOR)
            }

            val scrollPane = JBScrollPane(referencePopupProvider.contextReferencePanel).apply {
                isOpaque = false
                border = JBUI.Borders.empty(2, 0)
                horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
                verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_NEVER
            }

            scrollPane.viewport.isOpaque = false
            scrollPane.viewport.background = UIUtil.TRANSPARENT_COLOR

            val header = panel {
                row {
                    cell(addContextButton).align(AlignX.LEFT).gap(RightGap.SMALL)
                    cell(scrollPane).align(Align.FILL)
                }
            }.andTransparent().withBorder(JBUI.Borders.empty(0, 4))

            add(header, BorderLayout.NORTH)
            add(referencePopupProvider.editorTextField, BorderLayout.CENTER)
            val comboBoxAction = createModelComboBox()

            val footer = panel {
                row {
                    cell(comboBoxAction)
                    cell(createComboBox(listOf("Agent", "Ask", "Manual", "Background")))
                    cell(sendButton).align(AlignX.RIGHT)
                }
            }.andTransparent().withBorder(JBUI.Borders.empty(0, 4))

            add(footer, BorderLayout.SOUTH)
        }

        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
            add(scroll, BorderLayout.CENTER)
            add(inputBar, BorderLayout.SOUTH)
        }
    }

    private fun sendMessage(
        project: Project,
        chatIndex: Int,
        text: String?,
        referencePopupProvider: ReferencePopupProvider,
        messageContainer: JPanel,
        scroll: JBScrollPane
    ) {
        if (text == null || text.isNotEmpty()) {
            ApplicationManager.getApplication().invokeLater {
                WriteCommandAction.runWriteCommandAction(project) {
                    referencePopupProvider.editorTextField.text = ""
                }
            }

            if (text != null) {
                appendUserBubble(messageContainer, text)
            }

            if (messages[chatIndex] == null) {
                messages.put(chatIndex, mutableListOf())
            }

            if (text != null) {
                messages[chatIndex]?.add(UserMessage(text))
            }

            println("messages are: $messages")

            println("send request with messages: ${messages[chatIndex]}")

            val completionRequest = OpenAIRequestFactory.createBasicCompletionRequest(
                model = OpenAIChatCompletionModel.GPT_4_1.code,
                messages = messages[chatIndex].orEmpty(),
                isStream = true,
                overridenPath = ""
            )

            var cachedBubble: ChatBubble? = null
            var text = ""
            val tools: MutableList<ToolCall> = mutableListOf()

            println("sending completion request: ${ObjectMapper().writeValueAsString(completionRequest)}")
            CompletionsRequestService().getChatCompletionAsync(
                completionRequest,
                object : CompletionEventListener<String> {

                    override fun onCancelled(messageBuilder: StringBuilder?) {
                        println("on cancelled")
                    }

                    override fun onComplete(messageBuilder: StringBuilder?) {
                        println("on completed")
                        messages[chatIndex]?.add(AssistantMessage(text))

                        for (tool in tools) {
                            cachedBubble = null
                            text = ""

                            val messageText = try {
                                processToolCall(project, tool, messageContainer) ?: continue
                            } catch (e: Exception) {
                                "Error executing tool: ${e.message}"
                            }

                            messages[chatIndex]?.add(UserMessage(messageText))
                            appendUserBubble(
                                messageContainer,
                                messageText
                            )
                        }

                        if (tools.isNotEmpty()) {
                            sendMessage(
                                project,
                                chatIndex,
                                null,
                                referencePopupProvider,
                                messageContainer,
                                scroll
                            )
                        }
                    }

                    override fun onOpen() {
                        println("On open")
                    }

                    override fun onMessage(message: String?, rawMessage: String?, eventSource: EventSource?) {
                        println("on message: $message")
                        if (message != null && message.isNotEmpty()) {
                            text += message
                            if (cachedBubble != null) {
                                cachedBubble?.updateText(text)
                            } else {
                                cachedBubble = appendAssistantBubble(messageContainer, message)
                            }
                        }
                    }

                    override fun onError(error: ErrorDetails?, ex: Throwable?) {
                        println("on error: $error, $ex")
                    }

                    override fun onEvent(data: String) {
                        println("got event: $data")
                        try {
                            val (content, toolCalls) = SSEParser.parse(data)

                            when (content) {
                                is Content.PlanContent -> {
                                    println("💡 Received plan: ${content.plan.taskTitle}")
                                    println("Steps: ${content.plan.steps.joinToString()}")

                                    text += content.plan.taskTitle + "\n" + content.plan.steps.joinToString() + "\n"
                                    if (cachedBubble != null) {
                                        cachedBubble?.updateText(text)
                                    } else {
                                        cachedBubble = appendAssistantBubble(messageContainer, text)
                                    }
                                }

                                is Content.TextContent -> {
                                    text += content.text
                                    if (cachedBubble != null) {
                                        cachedBubble?.updateText(text)
                                    } else {
                                        cachedBubble = appendAssistantBubble(messageContainer, text)
                                    }

                                    println("✏️  Assistant says: ${content.text}")
                                }

                                else -> {
                                    println("⚠️  No content in this event")
                                }
                            }

                            toolCalls?.let {
                                println("🔧 Tool calls:")
                                val toolCall = it.firstOrNull() ?: return@let

                                if (tools.isEmpty()) {
                                    tools.add(
                                        ToolCall(
                                            name = toolCall.name.takeIf { it != "null" },
                                            arguments = toolCall.arguments
                                        )
                                    )
                                } else {
                                    tools[tools.size - 1] = tools[tools.size - 1].let {
                                        ToolCall(
                                            name = it.name.orEmpty(),
                                            arguments = it.arguments.orEmpty() + toolCall.arguments.orEmpty()
                                        )
                                    }
                                }

                                it.forEach { tc ->
                                    println(" - ${tc.name}(${tc.arguments})")
                                }
                            } ?: run {
                                for (tool in tools) {
                                    text += "\n\nTool call: " + tool.name.orEmpty() + "(" + tool.arguments.orEmpty() + ")\n\n"
                                    appendUserBubble(
                                        messageContainer,
                                        tool.name.orEmpty() + "(" + tool.arguments.orEmpty() + ")"
                                    )
                                    cachedBubble = null
                                    text = ""

                                    val toolResult = try {
                                        processToolCall(project, tool, messageContainer) ?: return
                                    } catch (e: Exception) {
                                        "Error executing tool: ${e.message}"
                                    }

                                    sendMessage(
                                        project,
                                        chatIndex,
                                        toolResult,
                                        referencePopupProvider,
                                        messageContainer,
                                        scroll
                                    )
                                }

                                tools.clear()
                            }

                        } catch (e: Exception) {
                            println("❌ Failed to parse SSE JSON for data $data: ${e.stackTraceToString()}")
                        }
                    }
                }
            )
        }
    }

    private fun processToolCall(project: Project, toolCall: ToolCall, container: JPanel): String? {
        val name = toolCall.name ?: return null
        val args = toolCall.arguments ?: emptyMap()
        println("processing a tool call: $toolCall")

        return when (name) {
                "write_file" -> {
                    val pathString = args["path"] ?: error("missing path")
                    val content = args["content"] ?: error("missing content")

                    // Normalize to system‐independent path
                    val path = Paths.get(project.basePath!!)
                        .resolve(pathString.trimStart('/', '\\'))
                        .toAbsolutePath()
                    val parentPath = path.parent.toAbsolutePath().toString()
                    val fileName = path.fileName.toString()

                    // Ensure the parent directory exists on disk
                    println("parent path: $parentPath, path: $path")
                    File(parentPath).apply { if (!exists()) mkdirs() }

                    lateinit var createdVFile: com.intellij.openapi.vfs.VirtualFile

                    WriteCommandAction.runWriteCommandAction(project) {
                        // Locate (or refresh) the parent directory in the VFS
                        val parentVDir = LocalFileSystem.getInstance()
                            .refreshAndFindFileByPath(parentPath)
                            ?: error("Could not find or create VFS directory: $parentPath")

                        // find or create the file
                        createdVFile = parentVDir.findChild(fileName)
                            ?: parentVDir.createChildData(this, fileName)
                        // write the text (this also handles line‐endings, encoding, etc.)
                        VfsUtil.saveText(createdVFile, content)
                    }

                    ApplicationManager.getApplication().invokeLater {
                        FileEditorManager.getInstance(project)
                            .openFile(createdVFile, true)
                    }

                    println("✅ Wrote $pathString")
                    "Successfully wrote file in path=$pathString"
                }


                "edit_file" -> {
                    val path = args["path"] ?: error("missing path")
                    val edits = args["edits"] ?: error("missing edits")
                    val filePath = Paths.get(project.basePath!!)
                        .resolve(path.trimStart('/', '\\'))
                        .toAbsolutePath()
                    val file = File(filePath.toString())

                    if (!file.exists()) error("file not found: $path")
                    file.appendText("\n$edits")
                    println("Edited file in path=$path")
                    "Successfully edited $path"
                }


                "find" -> {
                    val rawPattern = args["pattern"] ?: error("missing pattern")   // now a real regex
                    val basePath = project.basePath
                        ?: error("missing Project basePath")
                    val baseDir = Paths.get(basePath)

                    // Compile the user’s pattern into a Kotlin Regex.
                    // RegexOption.IGNORE_CASE is optional—remove if you want strict case.
                    val regex = rawPattern.toRegex(RegexOption.IGNORE_CASE)

                    val matches = File(basePath).walk()
                        .filter { it.isFile }
                        .map { file ->
                            // path relative to project root, with forward slashes
                            baseDir.relativize(file.toPath()).toString().replace("\\", "/")
                        }
                        .filter { relPath ->
                            // true if the regex finds a match anywhere in the relative path
                            regex.find(relPath) != null
                        }
                        .toList()

                    println("🔍 find results for regex /$rawPattern/ in project '$basePath':")
                    matches.forEach { println(" - $it") }

                    container.addCustomBubble(JPanel(VerticalLayout(8)).also {
                        for (match in matches) {
                            container.add(createBubble(match, JBColor.LIGHT_GRAY))
                        }

                        refresh(container)
                    })

                    "Successfully found ${matches.size} result(s) for regex /$rawPattern/ in project:\n" +
                            matches.joinToString("\n") { " - /$it" }
                }

                "codebase_search" -> {
                    val query = args["query"] ?: error("missing query")
                    val topK = args["top_k"]?.toIntOrNull() ?: 5
                    // Stub: in real life you'd call your semantic‐search index here
                    println("💡 (stub) searching codebase for '$query' (top $topK)")
                    "Successfully searched codebase for '$query' (top $topK) and found results: "
                }

                "list_directory" -> {
                    val dirArg = args["directory"].orEmpty()

                    val projectBasePath = project.basePath ?: error("Project base path not available")
                    val dir = File(projectBasePath, dirArg).canonicalFile

                    if (!dir.isDirectory) error("$dir is not a directory")

                    val children = dir.listFiles() ?: emptyArray()
                    println("📁 listing '${dir.relativeTo(File(projectBasePath))}':")
                    children.forEach {
                        val type = if (it.isDirectory) "dir" else "file"
                        println(" - [$type] ${it.name} (${it.length()} bytes)")
                    }

                    "Successfully listed directory '${dir.relativeTo(File(projectBasePath))}' with contents:\n${
                        children.map {
                            it.relativeTo(
                                File(
                                    projectBasePath
                                )
                            )
                        }.joinToString("\n")
                    }"
                }


                "grep_search" -> {
                    val pattern = args["pattern"] ?: error("missing pattern")
                    val path = args["path"] ?: error("missing path")
                    val recursive = args["recursive"]?.toBoolean() ?: true
                    val regex = pattern.toRegex()
                    val direction = if (recursive) FileWalkDirection.TOP_DOWN else FileWalkDirection.BOTTOM_UP

                    File(path).walk(direction).forEach { file ->
                        if (file.isFile) {
                            file.readLines().forEachIndexed { idx, line ->
                                if (regex.containsMatchIn(line)) {
                                    println("🧪 ${file.path}:${idx + 1}: $line")
                                }
                            }
                        }
                    }

                    "Successfully provided grep search results for '$pattern' in '$path' (recursive=$recursive):"
                }

                "view_code_item" -> {
                    val item = args["item_name"] ?: error("missing item_name")
                    val path = args["path"] ?: error("missing path")
                    val v = File(path).useLines { lines ->
                        val snippet = lines
                            .dropWhile { !it.contains("fun $item") && !it.contains("class $item") }
                            .takeWhile { !it.startsWith("}") }
                            .joinToString("\n")
                        println("$item in $path:\n$snippet")
                        return@useLines snippet
                    }

                    "Successfully viewed code item $item in $path:\n$v"
                }

                "view_file" -> {
                    val path = args["path"] ?: error("missing path")
                    val filePath = Paths.get(project.basePath!!)
                        .resolve(path.trimStart('/', '\\'))
                        .toAbsolutePath()
                    val file = File(filePath.toString())
                    if (!file.exists()) error("file not found: $path")
                    println("📄 contents of $path:\n${file.readText()}")
                    "Successfully viewed contents of $path:\n${file.readText()}"
                }

                else -> {
                    println("⚠️ Unknown tool: $name")
                    null
                }
            }
    }

    private fun JPanel.addCustomBubble(component: JPanel) {
        val container = this
        val bubble = object : JPanel(BorderLayout()) {
            init {
                background = JBColor.LIGHT_GRAY
                this.add(component)
            }

            override fun paintComponent(g: Graphics) {
                val g2 = g.create() as Graphics2D
                try {
                    g2.color = background
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    g2.fillRoundRect(0, 0, width, height, JBUI.scale(16), JBUI.scale(16))
                } finally {
                    g2.dispose()
                }
                super.paintComponent(g)
            }
        }
        container.add(bubble)
        refresh(container)
    }

    private fun appendUserBubble(container: JPanel, text: String) {
        val bubble = createBubble(text, JBColor.LIGHT_GRAY)
        container.add(bubble)
        refresh(container)
    }

    private fun appendAssistantBubble(container: JPanel, text: String): ChatBubble {
        val bubble = createBubble(text, UIUtil.getPanelBackground(), assistant = true)
        container.add(bubble)
        refresh(container)
        return bubble as ChatBubble
    }

    private fun historyBubble(text: String): JComponent {
        val label = JLabel("<html>${text.replace("\n", "<br>")}</html>")

        class RenameAction : AnAction("Rename", "Rename chat", Icons.Edit) {
            override fun actionPerformed(e: AnActionEvent) {
                // TODO: implement rename logic
                JOptionPane.showMessageDialog(null, "Rename clicked for: $text")
            }
        }

        class DeleteAction : AnAction("Delete", "Delete chat", Icons.Trash) {
            override fun actionPerformed(e: AnActionEvent) {
                // TODO: implement delete logic
                val result = JOptionPane.showConfirmDialog(
                    null,
                    "Delete this item?",
                    "Confirm Delete",
                    JOptionPane.YES_NO_OPTION
                )
                if (result == JOptionPane.YES_OPTION) {
                    // perform deletion
                }
            }
        }

        class MoreActionGroup : ActionGroup("More", true) {

            init {
                templatePresentation.icon = AllIcons.Actions.More
                templatePresentation.putClientProperty(ActionButton.HIDE_DROPDOWN_ICON, java.lang.Boolean.TRUE)
            }

            override fun getChildren(e: AnActionEvent?): Array<AnAction> {
                // Populate additional actions here
                return arrayOf(object : AnAction("Details") {
                    override fun actionPerformed(e: AnActionEvent) {
                        // TODO: show details
                        JOptionPane.showMessageDialog(null, "Details for: $text")
                    }
                }, object : AnAction("Copy") {
                    override fun actionPerformed(e: AnActionEvent) {
                        // TODO: copy action
//                        Toolkit.getDefaultToolkit().systemClipboard.setContents(
//                            StringSelection(text), null)
                    }
                })
            }
        }

        val actionGroup = DefaultActionGroup().apply {
            add(RenameAction())
            add(DeleteAction())
            add(MoreActionGroup())
        }

        val actionToolbar = ActionManager.getInstance()
            .createActionToolbar("HistoryBubbleToolbar", actionGroup, true)
            .apply {
                targetComponent = null
                component.isOpaque = false
                isReservePlaceAutoPopupIcon = false
            }

        val contentPanel = object : JPanel(BorderLayout()) {
            init {
                add(label, BorderLayout.CENTER)
                add(actionToolbar.component.apply {
                    border = JBUI.Borders.empty()
                }, BorderLayout.EAST)

                isOpaque = true
                background = JBColor.LIGHT_GRAY
                border = JBUI.Borders.empty(12, 20, 12, 8)

                cursor = Cursor(Cursor.HAND_CURSOR)
            }

            override fun paintComponent(g: Graphics) {
                val g2 = g.create() as Graphics2D
                try {
                    g2.setRenderingHint(
                        RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON
                    )
                    g2.color = JBColor.LIGHT_GRAY
                    g2.fillRoundRect(0, 0, width, height, 24, 24)
                } finally {
                    g2.dispose()
                }
            }

            override fun getMaximumSize(): Dimension {
                return Dimension(Int.MAX_VALUE, preferredSize.height)
            }
        }
        return contentPanel
    }

    private fun createBubble(text: String, bg: Color, assistant: Boolean = false): JComponent {
        return ChatBubble(text, bg, assistant)
    }


    private fun refresh(container: JPanel) {
        container.revalidate()
        container.repaint()
    }

    private enum class Alignment { LEFT, RIGHT }

    companion object {
        private val HISTORY_TAB_KEY = Key.create<Boolean>("com.example.Cod3.HISTORY_TAB")
    }
}
