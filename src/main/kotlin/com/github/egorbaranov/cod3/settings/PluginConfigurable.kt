package com.github.egorbaranov.cod3.settings

import com.github.egorbaranov.cod3.koog.KoogModelCatalog
import com.github.egorbaranov.cod3.ui.components.createModelComboBox
import com.github.egorbaranov.cod3.util.UIUtils
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.GridBagLayout
import javax.swing.*

class PluginConfigurable : SearchableConfigurable {

    private var mySettingsComponent: JPanel? = null

    private lateinit var completionApiKeyField: JBTextField
    private lateinit var completionApiUrlField: JBTextField

    private lateinit var chatApiKeyField: JBTextField
    private lateinit var chatApiUrlField: JBTextField

    private lateinit var useKoogCheckbox: JBCheckBox
    private lateinit var koogPromptArea: JBTextArea
    private lateinit var koogModelCombo: ComboBox<KoogModelCatalog.Entry>
    private lateinit var koogMcpCommandField: JBTextField
    private lateinit var koogMcpWorkingDirField: JBTextField
    private lateinit var koogMcpClientNameField: JBTextField

    private lateinit var useAcpCheckbox: JBCheckBox
    private lateinit var acpCommandField: JBTextField
    private lateinit var acpWorkingDirField: JBTextField
    private lateinit var acpSessionRootField: JBTextField

    private var retryQuantityDropdown = ComboBox(arrayOf(1, 3, 5, 10, 20))
    private var indexingStepsDropdown = ComboBox(arrayOf(1, 2, 3))


    override fun getId(): String = "cod3.settings"

    @NlsContexts.ConfigurableName
    override fun getDisplayName(): String = "My Plugin Settings"

    private val project: Project? by lazy {
        ProjectManager.getInstance().openProjects.firstOrNull()
    }

    override fun createComponent(): JComponent {
        val settings = PluginSettingsState.getInstance()
        completionApiKeyField = JBTextField(settings.openAIApiKey, 40).also {
            it.text = PluginSettingsState.getInstance().openAIApiKey
        }
        completionApiUrlField = JBTextField(settings.openAIApiUrl, 40).also {
            it.text = PluginSettingsState.getInstance().openAIApiUrl
        }

        chatApiKeyField = JBTextField(settings.openAIApiKey, 40).also {
            it.text = PluginSettingsState.getInstance().openAIApiKey
        }
        chatApiUrlField = JBTextField(settings.openAIApiUrl, 40).also {
            it.text = PluginSettingsState.getInstance().openAIApiUrl
        }

        useKoogCheckbox = JBCheckBox("Enable Koog agent (experimental)").apply {
            isSelected = settings.useKoogAgents
            addActionListener { updateKoogFieldsEnabled() }
        }
        koogPromptArea = JBTextArea(settings.koogSystemPrompt, 3, 40)
        koogModelCombo = ComboBox(KoogModelCatalog.availableModels.toTypedArray()).apply {
            renderer = object : ListCellRenderer<KoogModelCatalog.Entry> {
                private val delegate = DefaultListCellRenderer()
                override fun getListCellRendererComponent(
                    list: JList<out KoogModelCatalog.Entry>?,
                    value: KoogModelCatalog.Entry?,
                    index: Int,
                    isSelected: Boolean,
                    cellHasFocus: Boolean
                ): Component {
                    return delegate.getListCellRendererComponent(
                        list,
                        value?.label ?: "Select model",
                        index,
                        isSelected,
                        cellHasFocus
                    )
                }
            }
            selectedItem = KoogModelCatalog.availableModels.firstOrNull { it.id == settings.koogModelId }
                ?: KoogModelCatalog.availableModels.first()
        }
        koogMcpCommandField = JBTextField(settings.koogMcpCommand, 40)
        koogMcpWorkingDirField = JBTextField(settings.koogMcpWorkingDirectory, 40)
        koogMcpClientNameField = JBTextField(settings.koogMcpClientName, 40)

        useAcpCheckbox = JBCheckBox("Enable Agent Client Protocol").apply {
            isSelected = settings.useAgentClientProtocol
            addActionListener { updateAcpFieldsEnabled() }
        }
        acpCommandField = JBTextField(settings.acpAgentCommand, 40)
        acpWorkingDirField = JBTextField(settings.acpAgentWorkingDirectory, 40)
        acpSessionRootField = JBTextField(settings.acpSessionRoot, 40)

        mySettingsComponent = panel {
            group("Completion") {
                row("API key") {
                    cell(completionApiKeyField)
                }

                row("API url") {
                    cell(completionApiUrlField)
                }

                row("Model") {
                    cell(createModelComboBox())
                }
            }

            group("Chat") {
                row("API key") {
                    cell(chatApiKeyField)
                }

                row("API url") {
                    cell(chatApiUrlField)
                }

                row("Model") {
                    cell(createModelComboBox())
                }
            }

            group("Koog Agent (experimental)") {
                row {
                    cell(useKoogCheckbox)
                }

                row("System prompt") {
                    cell(JBScrollPane(koogPromptArea).apply {
                        preferredSize = Dimension(0, 80)
                    }).align(AlignX.FILL)
                }

                row("Model") {
                    cell(koogModelCombo).align(AlignX.FILL)
                }

                row("MCP command") {
                    cell(koogMcpCommandField).align(AlignX.FILL)
                }

                row("MCP working directory") {
                    cell(koogMcpWorkingDirField).align(AlignX.FILL)
                }

                row("MCP client name") {
                    cell(koogMcpClientNameField).align(AlignX.FILL)
                }
            }

            group("Agent Client Protocol") {
                row {
                    cell(useAcpCheckbox)
                }

                row("Agent command") {
                    cell(acpCommandField).align(AlignX.FILL)
                }

                row("Working directory") {
                    cell(acpWorkingDirField).align(AlignX.FILL)
                }

                row("Session root") {
                    cell(acpSessionRootField).align(AlignX.FILL)
                }
            }
        }
        updateKoogFieldsEnabled()
        updateAcpFieldsEnabled()
        return mySettingsComponent!!
    }

    override fun isModified(): Boolean {
        val settings = PluginSettingsState.getInstance()
        return completionApiKeyField.text != settings.openAIApiKey ||
                completionApiUrlField.text != settings.openAIApiUrl ||
                retryQuantityDropdown.item != settings.retryQuantity ||
                useKoogCheckbox.isSelected != settings.useKoogAgents ||
                koogPromptArea.text != settings.koogSystemPrompt ||
                (koogModelCombo.selectedItem as? KoogModelCatalog.Entry)?.id != settings.koogModelId ||
                koogMcpCommandField.text != settings.koogMcpCommand ||
                koogMcpWorkingDirField.text != settings.koogMcpWorkingDirectory ||
                koogMcpClientNameField.text != settings.koogMcpClientName ||
                useAcpCheckbox.isSelected != settings.useAgentClientProtocol ||
                acpCommandField.text != settings.acpAgentCommand ||
                acpWorkingDirField.text != settings.acpAgentWorkingDirectory ||
                acpSessionRootField.text != settings.acpSessionRoot
    }

    override fun apply() {
        val settings = PluginSettingsState.getInstance()
        settings.openAIApiKey = completionApiKeyField.text
        settings.openAIApiUrl = completionApiUrlField.text.takeIf { it.isNotEmpty() } ?: PluginSettingsState.DEFAULT_OPENAI_API_URL
        settings.retryQuantity = retryQuantityDropdown.item
        settings.indexingSteps = indexingStepsDropdown.item
        settings.useKoogAgents = useKoogCheckbox.isSelected
        settings.koogSystemPrompt = koogPromptArea.text.trim()
        settings.koogModelId = (koogModelCombo.selectedItem as? KoogModelCatalog.Entry)?.id ?: settings.koogModelId
        settings.koogMcpCommand = koogMcpCommandField.text.trim()
        settings.koogMcpWorkingDirectory = koogMcpWorkingDirField.text.trim()
        settings.koogMcpClientName = koogMcpClientNameField.text.trim().ifEmpty { "cod3-koog-mcp" }
        settings.useAgentClientProtocol = useAcpCheckbox.isSelected
        settings.acpAgentCommand = acpCommandField.text.trim()
        settings.acpAgentWorkingDirectory = acpWorkingDirField.text.trim()
        settings.acpSessionRoot = acpSessionRootField.text.trim()
    }

    override fun reset() {
        val settings = PluginSettingsState.getInstance()
        completionApiKeyField.text = settings.openAIApiKey
        completionApiUrlField.text = settings.openAIApiUrl
        retryQuantityDropdown.item = settings.retryQuantity
        indexingStepsDropdown.item = settings.indexingSteps
        useKoogCheckbox.isSelected = settings.useKoogAgents
        koogPromptArea.text = settings.koogSystemPrompt
        koogModelCombo.selectedItem = KoogModelCatalog.availableModels.firstOrNull { it.id == settings.koogModelId }
            ?: KoogModelCatalog.availableModels.first()
        koogMcpCommandField.text = settings.koogMcpCommand
        koogMcpWorkingDirField.text = settings.koogMcpWorkingDirectory
        koogMcpClientNameField.text = settings.koogMcpClientName
        useAcpCheckbox.isSelected = settings.useAgentClientProtocol
        acpCommandField.text = settings.acpAgentCommand
        acpWorkingDirField.text = settings.acpAgentWorkingDirectory
        acpSessionRootField.text = settings.acpSessionRoot
        updateKoogFieldsEnabled()
        updateAcpFieldsEnabled()
    }
    
    private fun updateAcpFieldsEnabled() {
        val enabled = ::useAcpCheckbox.isInitialized && useAcpCheckbox.isSelected
        if (::acpCommandField.isInitialized) {
            acpCommandField.isEnabled = enabled
        }
        if (::acpWorkingDirField.isInitialized) {
            acpWorkingDirField.isEnabled = enabled
        }
        if (::acpSessionRootField.isInitialized) {
            acpSessionRootField.isEnabled = enabled
        }
    }

    private fun updateKoogFieldsEnabled() {
        val enabled = ::useKoogCheckbox.isInitialized && useKoogCheckbox.isSelected
        if (::koogPromptArea.isInitialized) {
            koogPromptArea.isEnabled = enabled
        }
        if (::koogModelCombo.isInitialized) {
            koogModelCombo.isEnabled = enabled
        }
        if (::koogMcpCommandField.isInitialized) {
            koogMcpCommandField.isEnabled = enabled
        }
        if (::koogMcpWorkingDirField.isInitialized) {
            koogMcpWorkingDirField.isEnabled = enabled
        }
        if (::koogMcpClientNameField.isInitialized) {
            koogMcpClientNameField.isEnabled = enabled
        }
    }

    private fun buildStatPanel(titleText: String, mainText: String): JPanel {
        return JPanel(BorderLayout()).also { panel ->
            val titleLabel = JLabel(titleText).apply {
                font = font.deriveFont(Font.BOLD, 12f)
                foreground = JBColor.namedColor("Label.infoForeground", JBColor.GRAY)
                horizontalAlignment = SwingConstants.CENTER
                border = BorderFactory.createEmptyBorder(8, 0, 4, 0)
            }

            val mainLabel = JLabel(mainText).apply {
                font = font.deriveFont(Font.BOLD, 32f)
                horizontalAlignment = SwingConstants.CENTER
                verticalAlignment = SwingConstants.CENTER
            }

            val centerPanel = JPanel(GridBagLayout()).apply {
                add(mainLabel)
                border = BorderFactory.createEmptyBorder(0, 10, 10, 10)
            }

            panel.add(titleLabel, BorderLayout.NORTH)
            panel.add(centerPanel, BorderLayout.CENTER)

            panel.preferredSize = Dimension(120, 100)
            panel.minimumSize = Dimension(120, 100)
            panel.border = UIUtils.createRoundedBorder()
            panel.background = JBColor.namedColor("Panel.background", JBColor.WHITE)
        }
    }
}
