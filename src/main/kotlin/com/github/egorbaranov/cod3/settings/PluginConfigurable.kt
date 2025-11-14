package com.github.egorbaranov.cod3.settings

import com.github.egorbaranov.cod3.koog.KoogModelCatalog
import com.github.egorbaranov.cod3.ui.components.createModelComboBox
import com.github.egorbaranov.cod3.util.UIUtils
import com.intellij.openapi.options.SearchableConfigurable
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

    private lateinit var openAiApiKeyField: JBTextField
    private lateinit var openAiApiUrlField: JBTextField
    private lateinit var anthropicApiKeyField: JBTextField
    private lateinit var anthropicApiUrlField: JBTextField
    private lateinit var anthropicApiVersionField: JBTextField
    private lateinit var googleApiKeyField: JBTextField
    private lateinit var googleApiUrlField: JBTextField

    private lateinit var codeCompletionEnabledCheckbox: JBCheckBox
    private lateinit var codeCompletionModelCombo: ComboBox<KoogModelCatalog.Entry>

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

    override fun createComponent(): JComponent {
        val settings = PluginSettingsState.getInstance()
        openAiApiKeyField = JBTextField(settings.openAIApiKey, 40)
        openAiApiUrlField = JBTextField(settings.openAIApiUrl, 40)
        anthropicApiKeyField = JBTextField(settings.claudeApiKey, 40)
        anthropicApiUrlField = JBTextField(
            settings.claudeApiUrl.ifBlank { PluginSettingsState.DEFAULT_ANTHROPIC_API_URL },
            40
        )
        anthropicApiVersionField = JBTextField(
            settings.claudeApiVersion.ifBlank { PluginSettingsState.DEFAULT_ANTHROPIC_API_VERSION },
            20
        )
        googleApiKeyField = JBTextField(settings.googleApiKey, 40)
        googleApiUrlField = JBTextField(
            settings.googleApiUrl.ifBlank { PluginSettingsState.DEFAULT_GOOGLE_API_URL },
            40
        )

        codeCompletionEnabledCheckbox = JBCheckBox("Enable code completion").apply {
            isSelected = settings.codeCompletionEnabled
            addActionListener { updateCodeCompletionFieldsEnabled() }
        }
        codeCompletionModelCombo = ComboBox(KoogModelCatalog.availableModels.toTypedArray()).apply {
            renderer = object : ListCellRenderer<KoogModelCatalog.Entry> {
                private val delegate = DefaultListCellRenderer()
                override fun getListCellRendererComponent(
                    list: JList<out KoogModelCatalog.Entry>?,
                    value: KoogModelCatalog.Entry?,
                    index: Int,
                    isSelected: Boolean,
                    cellHasFocus: Boolean
                ): Component {
                    val component = delegate.getListCellRendererComponent(
                        list,
                        value?.label ?: "Select model",
                        index,
                        isSelected,
                        cellHasFocus
                    )
                    if (component is JLabel) {
                        component.icon = value?.provider?.icon
                    }
                    return component
                }
            }
            selectedItem = KoogModelCatalog.availableModels.firstOrNull { it.id == settings.codeCompletionModelId }
                ?: KoogModelCatalog.availableModels.first()
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
                    val component = delegate.getListCellRendererComponent(
                        list,
                        value?.label ?: "Select model",
                        index,
                        isSelected,
                        cellHasFocus
                    )
                    if (component is JLabel) {
                        component.icon = value?.provider?.icon
                    }
                    return component
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
            group("Model Configuration") {
                row("Model") {
                    cell(createModelComboBox())
                }
            }

            group("Code Completion Configuration") {
                row {
                    cell(codeCompletionEnabledCheckbox)
                }
                row("Model") {
                    cell(codeCompletionModelCombo).align(AlignX.FILL)
                }
            }

            group("OpenAI Credentials") {
                row("API key") {
                    cell(openAiApiKeyField)
                }
                row("Base URL") {
                    cell(openAiApiUrlField)
                }
            }

            group("Anthropic (Claude) Credentials") {
                row("API key") {
                    cell(anthropicApiKeyField)
                }
                row("Base URL") {
                    cell(anthropicApiUrlField)
                }
                row("API version") {
                    cell(anthropicApiVersionField)
                }
            }

            group("Google Gemini Credentials") {
                row("API key") {
                    cell(googleApiKeyField)
                }
                row("Base URL") {
                    cell(googleApiUrlField)
                }
            }

            group("Agent Configuration") {
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
        updateCodeCompletionFieldsEnabled()
        updateAcpFieldsEnabled()
        return mySettingsComponent!!
    }

    override fun isModified(): Boolean {
        val settings = PluginSettingsState.getInstance()
        return openAiApiKeyField.text != settings.openAIApiKey ||
                openAiApiUrlField.text != settings.openAIApiUrl ||
                anthropicApiKeyField.text != settings.claudeApiKey ||
                anthropicApiUrlField.text != settings.claudeApiUrl ||
                anthropicApiVersionField.text != settings.claudeApiVersion ||
                googleApiKeyField.text != settings.googleApiKey ||
                googleApiUrlField.text != settings.googleApiUrl ||
                codeCompletionEnabledCheckbox.isSelected != settings.codeCompletionEnabled ||
                (codeCompletionModelCombo.selectedItem as? KoogModelCatalog.Entry)?.id != settings.codeCompletionModelId ||
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
        settings.openAIApiKey = openAiApiKeyField.text.trim()
        settings.openAIApiUrl = openAiApiUrlField.text.trim().ifEmpty { PluginSettingsState.DEFAULT_OPENAI_API_URL }
        settings.claudeApiKey = anthropicApiKeyField.text.trim()
        settings.claudeApiUrl = anthropicApiUrlField.text.trim().ifEmpty { PluginSettingsState.DEFAULT_ANTHROPIC_API_URL }
        settings.claudeApiVersion = anthropicApiVersionField.text.trim().ifEmpty { PluginSettingsState.DEFAULT_ANTHROPIC_API_VERSION }
        settings.googleApiKey = googleApiKeyField.text.trim()
        settings.googleApiUrl = googleApiUrlField.text.trim().ifEmpty { PluginSettingsState.DEFAULT_GOOGLE_API_URL }
        settings.codeCompletionEnabled = codeCompletionEnabledCheckbox.isSelected
        settings.codeCompletionModelId =
            (codeCompletionModelCombo.selectedItem as? KoogModelCatalog.Entry)?.id ?: settings.codeCompletionModelId
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
        openAiApiKeyField.text = settings.openAIApiKey
        openAiApiUrlField.text = settings.openAIApiUrl
        anthropicApiKeyField.text = settings.claudeApiKey
        anthropicApiUrlField.text = settings.claudeApiUrl.ifBlank { PluginSettingsState.DEFAULT_ANTHROPIC_API_URL }
        anthropicApiVersionField.text = settings.claudeApiVersion.ifBlank { PluginSettingsState.DEFAULT_ANTHROPIC_API_VERSION }
        googleApiKeyField.text = settings.googleApiKey
        googleApiUrlField.text = settings.googleApiUrl.ifBlank { PluginSettingsState.DEFAULT_GOOGLE_API_URL }
        codeCompletionEnabledCheckbox.isSelected = settings.codeCompletionEnabled
        codeCompletionModelCombo.selectedItem = KoogModelCatalog.availableModels.firstOrNull { it.id == settings.codeCompletionModelId }
            ?: KoogModelCatalog.availableModels.first()
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
        updateCodeCompletionFieldsEnabled()
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

    private fun updateCodeCompletionFieldsEnabled() {
        val enabled = ::codeCompletionEnabledCheckbox.isInitialized && codeCompletionEnabledCheckbox.isSelected
        if (::codeCompletionModelCombo.isInitialized) {
            codeCompletionModelCombo.isEnabled = enabled
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
